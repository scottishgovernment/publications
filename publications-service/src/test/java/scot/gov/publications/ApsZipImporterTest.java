package scot.gov.publications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.jcr.TestRepository;
import scot.gov.publications.hippo.*;
import scot.gov.publications.imageprocessing.ImageProcessing;
import scot.gov.publications.imageprocessing.ImageProcessingException;
import scot.gov.publications.metadata.*;
import scot.gov.publications.repo.Publication;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipFile;

import static junit.framework.TestCase.assertFalse;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * These tests run against an embedded JCR repository: see TestRepository.
 */
public class ApsZipImporterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ApsZipImporterTest.class);

    private Session session;

    ObjectMapper objectMapper;
    ApsZipImporter sut;

    HippoUtils hippoUtils = new HippoUtils();

    @Before
    public void setUp() throws Exception {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        sut = new ApsZipImporter();
        sut.imageProcessing = fakeImageProcessing();
        sut.exif = (binary, mime) -> 100;

        // get a session with the test repository
        session = TestRepository.session();
        sut.sessionFactory = () -> TestRepository.session();
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getHippo().setUser("testuser");
    }

    @After
    public void tearDown() throws RepositoryException {
        session.logout();
    }

    /**
     * Certain fields should not be overwritten when re-importing a zip.  This is to allow content editors to be in
     * control of these fields since they apply their own standards for titles, tags etc. To test:
     *
     * - Import a publication
     * - Update title and other fields that should not be overwritten
     * - Import it again
     * - Ensure that the fields such as title are not overwritten
     */
    @Test
    public void expectedFieldsRetainedWhenReimportingPublication() throws Exception {

        // import sample publication
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("expectedFieldsRetainedWhenReimportingPublication!", "fixtures/exampleZipContents");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("expectedFieldsRetainedWhenReimportingPublication");
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();
        String path1 = sut.importApsZip(zip1, publication);

        // update fields in the repository that a user might edit: title, directory, topics etc.
        Node publicationFolder = session.getNode(path1);
        Node node = publicationFolder.getNode("index").getNodes().nextNode();
        changePropertyValues(node, "govscot:title", "govscot:summary", "govscot:seoTitle", "govscot:metaDescription", "govscot:notes");
        changeHtmlPropertyValue(node, "govscot:content");
        changeArrayProperty(node, "hippostd:tags");

        // now alter the metadata and import the new zip
        Path fixturePath2 = ZipFixtures.copyFixtureToTmpDirectory("expectedFieldsRetainedWhenReimportingPublication2", "fixtures/exampleZipContents");
        metadata.setTitle("new title");
        metadata.setExecutiveSummary("new exec summary");
        metadata.setDescription("new description");
        saveMetadata(metadata, fixturePath2);

        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath2);
        String path2 = sut.importApsZip(zip2, publication);

        // ASSERT

        // the path should not have changed
        assertEquals(path1, path2);
        Node pubFolder2 = session.getNode(path2);
        Node pub = pubFolder2.getNode("index").getNodes().nextNode();

        // changed properties should be the value set in code rather then the values from the second zip.
        assertTrue(endsWith(pub.getProperty("govscot:title").getString(), "changed"));
        assertTrue(endsWith(pub.getProperty("govscot:summary").getString(), "changed"));
        assertTrue(endsWith(pub.getProperty("govscot:seoTitle").getString(), "changed"));
        assertTrue(endsWith(pub.getProperty("govscot:metaDescription").getString(), "changed"));
        assertTrue(endsWith(pub.getProperty("govscot:notes").getString(), "changed"));
    }

    /**
     * When a publication is imported it should be moved to the correct folder according to its publication type
     * and publication date.
     */
    @Test
    public void publicationMovedToCorrectFolder() throws Exception {

        // import sample publication
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("publicationMovedToCorrectFolder!", "fixtures/exampleZipContents");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setTitle("publicationMovedToCorrectFolder  ");
        metadata.setIsbn("publicationMovedToCorrectFolder");
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();
        String path1 = sut.importApsZip(zip1, publication);

        /// move the publication
        session.move(path1, "/content/documents/govscot/publications/moved");

        // now alter the metadata and import the new zip
        LocalDateTime now = LocalDateTime.now();
        Path fixturePath2 = ZipFixtures.copyFixtureToTmpDirectory("publicationMovedToCorrectFolder2", "fixtures/exampleZipContents");
        metadata.setIsbn("publicationMovedToCorrectFolder");
        metadata.setPublicationType("Map");
        metadata.setPublicationDate(now);
        saveMetadata(metadata, fixturePath2);

        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath2);
        String path2 = sut.importApsZip(zip2, publication);

        // ASSERT

        // the path should be based on the new metadata
        assertEquals(path2, "/content/documents/govscot/publications/map/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/publicationmovedtocorrectfolder");
    }

    /**
     * Ensure that the state is set correctly depending on the publications date.  This also tests whether workflow jobs
     * for publications with publication dates in the future are created and moved if they are no longer needed.
     *
     * Steps:
     * - Import a publication with a future publication date - it should have a workflow job and not be published
     * - Reimoort it with a publication date in the past - it should be published with no workflow job
     * - Reimport it again with a publication date in the future - the workflow job should be back and the publication
     *   should not be published
     */
    @Test
    public void workFlowJobAndPublishStatusSetCorrectly() throws Exception {
        // import sample publication with publication date in fiture then assert that it is not published
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("workFlowJobAndPublishStatusSetCorrectly", "fixtures/exampleZipContents");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setTitle("workFlowJobAndPublishStatusSetCorrectly  ");
        metadata.setIsbn("workFlowJobAndPublishStatusSetCorrectly");
        metadata.setPublicationDate(LocalDateTime.now().plusMonths(1));
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        String path1 = sut.importApsZip(zip1, new Publication());
        assertPublicationFields(path1, false);

        // change the publication date to be in the past and reimport it...should be published
        metadata.setPublicationDate(LocalDateTime.now().minusDays(1));
        saveMetadata(metadata, fixturePath);
        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath);
        String path2 = sut.importApsZip(zip2, new Publication());
        assertPublicationFields(path2, true);

        // change the publication date to be in the past and reimport it...should be published
        metadata.setPublicationDate(LocalDateTime.now().plusDays(1));
        saveMetadata(metadata, fixturePath);
        ZipFile zip3 = ZipFixtures.zipDirectory(fixturePath);
        String path3 = sut.importApsZip(zip3, new Publication());
        assertPublicationFields(path3, false);
    }


    void assertPublicationFields(String path, boolean shoudlBePublished) throws Exception {
        Node folder = session.getNode(path);
        Node handle = folder.getNode("index");
        Node pub = handle.getNodes().nextNode();
        if (shoudlBePublished) {
            assertFalse(handle.hasNode("hippo:request/hipposched:triggers/default"));
            assertEquals(pub.getProperty("hippostd:state").getString(), "published");
        } else {
            assertTrue(handle.hasNode("hippo:request/hipposched:triggers/default"));
            assertEquals(pub.getProperty("hippostd:state").getString(), "unpublished");
        }
    }

     // import a publication and make sure that links are rewritten correctly

     // can upload no html version then a version with html

     // publication with the same title as an existing one gets a disambiguated slug

     // rejects zip with no manifest

     // rejects zip with invalid manifest

     // rejects zip if manifest mentions file not peresent in zip

     // rejects missing metadata

     // rejects invalid metadata

    @Test
    public void canImportExampleZip() throws Exception {
        // ARRANGE

        // ACT
        sut.importApsZip(ZipFixtures.exampleZip(), new Publication());

        // ASSERT
        String expectedPath = "/content/documents/govscot/publications/publication/2018/09/social-security-scotland-digital-technology-strategy";

        // some basic fields
        Node publicationFolder = session.getNode(expectedPath);
        Node index = publicationFolder.getNode("index/index");
        assertEquals("title", "Social Security Scotland Digital and Technology Strategy", index.getProperty("govscot:title").getString());
        assertEquals("name", "Social Security Scotland Digital and Technology Strategy", index.getProperty("hippo:name").getString());
        assertEquals("seoTitle", "Social Security Scotland Digital and Technology Strategy", index.getProperty("govscot:seoTitle").getString());
        assertEquals("publicationType", "publication", index.getProperty("govscot:publicationType").getString());
        assertEquals("isbn", "9781787810754", index.getProperty("govscot:isbn").getString());

        // has right number of pages and the first one is marked as the contents page
        Node pagesFolder = publicationFolder.getNode("pages");
        assertEquals(17, pagesFolder.getNodes().getSize());
        Node contentsPage = pagesFolder.getNode("0/0");
        System.out.println(contentsPage.getProperty("govscot:contentsPage").getBoolean());
        assertTrue("isContentPage", contentsPage.getProperty("govscot:contentsPage").getBoolean());

        // has document nodes as expected
        Node documentsFolder = publicationFolder.getNode("documents");
        Assert.assertEquals(1, documentsFolder.getNodes().getSize());
    }

    @Test
    public void canImportExampleZipFromResources() throws Exception {
        // ARRANGE
        ZipFile zip = ZipFixtures.zipResourceDirectory("fixtures/exampleZipContents");
        Publication publication = new Publication();
        publication.setId("exampleid");

        // ACT
        sut.importApsZip(zip, publication);

        // ASSERT
        String expectedPath = "/content/documents/govscot/publications/statistics-publication/2018/10/example-publication";

        // some basic fields
        Node publicationFolder = session.getNode(expectedPath);
        Node index = publicationFolder.getNode("index/index");
        assertEquals("title", "This is an example publication", index.getProperty("govscot:title").getString());
        assertEquals("name", "This is an example publication", index.getProperty("hippo:name").getString());
    }

    Path findMetadata(Path path) {
        String [] jsonFiles = path.toFile().list(new SuffixFileFilter(".json"));
        if (jsonFiles.length != 1) {
            throw new RuntimeException("unexpected version ");
        }
        return path.resolve(jsonFiles[0]);
    }

    void changePropertyValues(Node node, String ... propertyNames) throws RepositoryException {
        for (String propertyName : propertyNames) {
            String propertyValue = node.getProperty(propertyName).getString();
            node.setProperty(propertyName, propertyValue + "changed");
        }
    }

    void changeHtmlPropertyValue(Node node, String property) throws RepositoryException {
        String newValue = node.getNode(property).getProperty("hippostd:content").toString() + "changed";
        node.getNode("govscot:content").setProperty("hippostd:content", newValue);
    }

    void changeArrayProperty(Node node, String property) throws RepositoryException {
        String [] newvalue = {"changed"};
        node.setProperty(property, newvalue, PropertyType.STRING);
    }


    Node findNodeByIsbn(String isbn) throws RepositoryException {
        return hippoUtils.findOne(session, "SELECT * FROM govscot:Publication WHERE govscot:isbn = '%s' and hippostd:state = 'published'", isbn);
    }

    void saveMetadata(Metadata metadata, Path path) throws IOException {
        Path metadataPath = findMetadata(path);
        LOG.info("Saving metedata to {}", metadataPath);
        MetadataWrapper metadataWrapper = new MetadataWrapper();
        metadataWrapper.setMetadata(metadata);
        objectMapper.writeValue(metadataPath.toFile(), metadataWrapper);
    }

    Metadata loadMetadata(Path path) throws MetadataParserException, IOException {
        Path metadataPath = findMetadata(path);
        return new MetadataParser()
                .parse(new FileInputStream(metadataPath.toFile()));
    }

    /**
     * since gm is not installed on jenkins we want a fake implementaiton of ImageProcessing that always returns the same image.
     */
    ImageProcessing fakeImageProcessing() throws Exception {

        return new ImageProcessing() {
            public File extractPdfCoverImage(InputStream source) throws ImageProcessingException {
                return tmpFile();
            }

            public File thumbnail(InputStream source, int width) throws ImageProcessingException {
                return tmpFile();
            }

            private File tmpFile() throws ImageProcessingException {
                try {
                    File imageFile = File.createTempFile("coverImage", ".jpeg");
                    IOUtils.copy(
                            ApsZipImporterTest.class.getResourceAsStream("/exampleImage.jpeg"),
                            new FileOutputStream(imageFile));
                    return imageFile;
                } catch (IOException e) {
                    throw new ImageProcessingException("Failed to fake an image", e);
                }
            }
        };
    }

}
