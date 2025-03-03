package org.dice_group.raki.hobbit.systems.http;

import com.google.common.collect.Sets;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.dice_group.raki.hobbit.system.http.AbstractHTTPSystemAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLParser;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxPrefixNameShortFormProvider;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rdf.rdfxml.parser.RDFXMLParser;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * Creates the raki ilp system adapter for the Ontolearn/DRILL System
 */
public class DRILLSystemAdapter extends AbstractHTTPSystemAdapter {

    private static final String ONTOPY_PATH = "/Ontolearn/";
    private static final String baseUri ="http://localhost:9080";
    private final ManchesterOWLSyntaxOWLObjectRendererImpl renderer;
    private Configuration mapping;
    private final OWLOntologyManager manager = OWLManager.createConcurrentOWLOntologyManager();
    private final OWLParser parser = new RDFXMLParser();


    @Override
    protected String convertToManchester(String concept) throws OWLOntologyCreationException, IOException {
        //We get the whole thing in RDF syntax, but we want Manchester Syntax
        OWLOntology onto = manager.createOntology();
        parser.parse(new StreamDocumentSource(new ByteArrayInputStream(concept.getBytes(StandardCharsets.UTF_8))), onto, manager.getOntologyLoaderConfiguration());
        OWLClass  pred0 = new OWLDataFactoryImpl().getOWLClass(IRI.create(onto.getOntologyID().getOntologyIRI().get() +"#Pred_0"));
        OWLEquivalentClassesAxiom axiom = onto.getEquivalentClassesAxioms(pred0).iterator().next();
        OWLClassExpression expr = axiom.getClassExpressionsMinus(pred0).iterator().next();
        manager.removeOntology(onto);
        return renderer.render(expr);
    }

    public DRILLSystemAdapter() {
        super(baseUri);
        LOGGER.info("Loading mapping now.");
        loadFileMapping();
        LOGGER.info("mapping loaded.");
        renderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
    }

    /**
     * Read drills configuration file
     *
     * @param fileName
     * @return
     */
    private Configuration readConfiguration(String fileName){
        if(!new File(fileName).exists()){
            LOGGER.error("Couldn't find configuration file {}", fileName);
            System.exit(1);
        }
        File propertiesFile = new File(fileName);
        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder =
                new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                        .configure(params.fileBased()
                                .setFile(propertiesFile));
        try
        {
            return builder.getConfiguration();
            // config contains all properties read from the file
        }
        catch(ConfigurationException cex)
        {
            LOGGER.error("Could not read dataset/benchmark property file");
            return null;
        }
    }


    private void loadFileMapping() {
        mapping = readConfiguration("/raki/drill-mapping.properties");

    }


    @Override
    public void startSystem(String ontologyFile) throws Exception {
        if(!new File(ontologyFile).exists()){
            LOGGER.error("Couldn't find ontology file {}", ontologyFile);
            throw new FileNotFoundException(ontologyFile);
        }

        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFile));
        OWLOntology owlOntology = manager.loadOntologyFromOntologyDocument(IRI.create("http://www.w3.org/2002/07/owl"));
        BidirectionalShortFormProviderAdapter provider = new BidirectionalShortFormProviderAdapter(Sets.newHashSet(ontology, owlOntology), new ManchesterOWLSyntaxPrefixNameShortFormProvider(ontology));
        renderer.setShortFormProvider(provider);
        String id = ontology.getOntologyID().getOntologyIRI().get().toString();

        LOGGER.info("Using Ontology with ID {}", id);
        String[] files = mapping.getString(id).split(",\\s*");
        String embeddings = files[0];
        String preTrainedData = files[1];

        //Check if both embeddings or training data exists, if not exit, otherwise the system will hang and not terminate
        if(!new File(ONTOPY_PATH+"embeddings/"+embeddings).exists()){
            LOGGER.error("Couldn't find embeddings file {}embeddings/{}", ONTOPY_PATH, embeddings);
            throw new FileNotFoundException(ONTOPY_PATH+"embeddings/"+embeddings);
        }
        if(!new File(ONTOPY_PATH+"pre_trained_agents/"+preTrainedData).exists()){
            LOGGER.error("Couldn't find pre trained data file {}pre_trained_agents/{}", ONTOPY_PATH, preTrainedData);
            throw new FileNotFoundException(ONTOPY_PATH+"pre_trained_agents/"+preTrainedData);
        }

        LOGGER.info("Found embeddings {} and pre trained data {}", embeddings, preTrainedData);
        System.out.println(timeOutMs);
        String[] start = new String[]{"bash", "-c", "/raki/startonto.sh "+ontologyFile+" "+ONTOPY_PATH+"embeddings/"+embeddings+
                " "+ONTOPY_PATH+"pre_trained_agents/"+preTrainedData+" "+Math.max(1, this.timeOutMs/1000) };
        execute(start);
    }

    @Override
    public void init() throws Exception {
        super.init();
    }


    public void execute(String[] args) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder().redirectErrorStream(true).inheritIO();
        processBuilder.command(args);
        processBuilder.start();
    }
}
