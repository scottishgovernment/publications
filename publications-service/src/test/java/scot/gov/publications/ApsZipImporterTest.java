package scot.gov.publications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;
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
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestEntry;
import scot.gov.publications.manifest.ManifestParser;
import scot.gov.publications.manifest.ManifestParserException;
import scot.gov.publications.metadata.*;
import scot.gov.publications.repo.Publication;

import javax.jcr.*;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static junit.framework.TestCase.assertFalse;
import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * These tests run against an embedded JCR repository: see TestRepository.
 */
public class ApsZipImporterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ApsZipImporterTest.class);

    private Session session;

    ObjectMapper objectMapper;
    ApsZipImporter sut;

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

    /**
     * Can upload no html version then a version with html
     */
    @Test
    public void canUploadZipWithNoHtmlThenOneWithTheHtml() throws Exception {

        // Create 2 zips, one without html and with html
        Path fixturePathNoHtml = ZipFixtures.copyFixtureToTmpDirectory("canUploadZipWithNoHtml", "fixtures/exampleZipContents");
        File [] htmlFiles = fixturePathNoHtml.toFile().listFiles(file -> endsWith(file.getName(), ".htm"));
        for (File htmlFile : htmlFiles) {
            htmlFile.delete();
        }
        ZipFile zipWithoutHtml = ZipFixtures.zipDirectory(fixturePathNoHtml);

        Path fixturePathHtml = ZipFixtures.copyFixtureToTmpDirectory("canUploadZipWithHtml", "fixtures/exampleZipContents");
        ZipFile zipWithHtml = ZipFixtures.zipDirectory(fixturePathHtml);

        // ACT -- import without html
        String pathWithPages = sut.importApsZip(zipWithoutHtml, new Publication());
        Node pagesNode1 = session.getNode(pathWithPages + "/pages");
        assertEquals(0, pagesNode1.getNodes().getSize());
        String pathWithoutPages = sut.importApsZip(zipWithHtml, new Publication());

        // ASSERT
        // they should have been imported to the same path
        assertEquals(pathWithPages, pathWithoutPages);

        // the pages node should not have pages in it
        Node pagesNode = session.getNode(pathWithoutPages + "/pages");
        assertEquals(pagesNode.getNodes().getSize(), htmlFiles.length);
    }

    /**
     * Publication slug is disambiguated if slug already used.
     */
    @Test
    public void slugDisambiguatedIfAlreadyTaken() throws Exception {

        // ARRANGE - create 2 zips with the same title but different isbn's
        Path fixturePath1 = ZipFixtures.copyFixtureToTmpDirectory("slugDisambiguatedIfAlreadyTaken1", "fixtures/exampleZipContents");
        Metadata metadata1 = loadMetadata(fixturePath1);
        metadata1.setIsbn("111");
        metadata1.setTitle("publication title");
        saveMetadata(metadata1, fixturePath1);
        fixturePath1.toFile().list();
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath1);

        Path fixturePath2 = ZipFixtures.copyFixtureToTmpDirectory("slugDisambiguatedIfAlreadyTaken1", "fixtures/exampleZipContents");
        Metadata metadata2 = loadMetadata(fixturePath1);
        metadata2.setIsbn("222");
        metadata2.setTitle("publication title");
        saveMetadata(metadata2, fixturePath2);
        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath2);

        // ACT -- import them both
        String path1 = sut.importApsZip(zip1, new Publication());
        String path2 = sut.importApsZip(zip2, new Publication());

        // ASSERT - the second path should have been disambiguated
        assertEquals(path1, "/content/documents/govscot/publications/statistics-publication/2018/10/publication-title");
        assertEquals(path2, "/content/documents/govscot/publications/statistics-publication/2018/10/publication-title-2");
    }

    /**
     * Rejects invalid manifest
     */
    @Test(expected = ApsZipImporterException.class)
    public void rejectsInvalidManifest() throws Exception {
        // ARRANGE - write over the manifest with invalid values
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsInvalidManifest", "fixtures/exampleZipContents");
        FileUtils.write(fixturePath.resolve("manifest.txt").toFile(), "££££\nrrrr", "UTF-8");

        // ACT
        sut.importApsZip(ZipFixtures.zipDirectory(fixturePath), new Publication());

        // ASSERT - expected exception
    }

    /**
     * We accept an empty manifest - this indicates that there are no entries to be uploaded.
     */
    @Test
    public void acceptsEmptyManifest() throws Exception {
        // ARRANGE - remove the metatdata
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsEmptyManifest", "fixtures/exampleZipContents");
        FileUtils.write(fixturePath.resolve("manifest.txt").toFile(), "", "UTF-8");

        // ACT
        sut.importApsZip(ZipFixtures.zipDirectory(fixturePath), new Publication());

        // ASSERT - no exception should have been thrown
    }

    /**
     * Rejects missing manifest
     */
    @Test(expected = ApsZipImporterException.class)
    public void rejectsMissingManifest() throws Exception {
        // ARRANGE - remove the metatdata
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsMissingManifest", "fixtures/exampleZipContents");
        fixturePath.resolve("manifest.txt").toFile().delete();

        // ACT
        sut.importApsZip(ZipFixtures.zipDirectory(fixturePath), new Publication());

        // ASSERT - expect an exception
    }

    /**
     * Rejects missing metadata
     */
    @Test
    public void rejectsMissingMetadata() throws Exception {
        // ARRANGE - remove the metatdata
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsMissingMetadata", "fixtures/exampleZipContents");
        Path metadataPath = findMetadata(fixturePath);
        metadataPath.toFile().delete();

        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);
        try {
            // ACT
            sut.importApsZip(zip, new Publication());
            fail("An exception should have been thrown");
        } catch (ApsZipImporterException e) {
            // ASSERT
            assertEquals(e.getMessage(), "No metadata file in zip");
        }
    }

    /**
     * Rejects truncated metadata
     */
    @Test
    public void rejectsTruncatedMetadata() throws Exception {
        // ARRANGE - save half of the contents of the metadata
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsTruncatedMetadata", "fixtures/exampleZipContents");
        Path metadataPath = findMetadata(fixturePath);
        String metaDataString = FileUtils.readFileToString(metadataPath.toFile(), "UTF-8");
        String truncatedMetadata = StringUtils.truncate(metaDataString, 10);
        FileUtils.write(metadataPath.toFile(), truncatedMetadata, "UTF-8");

        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);
        try {
            // ACT
            sut.importApsZip(zip, new Publication());
            fail("An exception should have been thrown");
        } catch (ApsZipImporterException e) {
            // ASSERT
            assertEquals(e.getMessage(), "Failed to parse metadata");
        }
    }

    /**
     * Rejects zip if manifest mentions file not in zip
     *
     * If the manifest file mentions a file that is not present in the zip then an exception should be thrown.
     */
    @Test
    public void rejectsZipIfManifestMentionsFileNotInZip() throws Exception {
        // ARRANGE - radd a non existant entry to the manifest and save it
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsZipIfManifestMentionsFileNotInZip", "fixtures/exampleZipContents");
        Manifest manifest = loadManifest(fixturePath);
        manifest.getEntries().add(new ManifestEntry("nosuchfile.pdf", "No such file"));
        saveManifest(manifest, fixturePath);

        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);
        try {
            // ACT
            sut.importApsZip(zip, new Publication());
            fail("An exception should have been thrown");
        } catch (ApsZipImporterException e) {
            // ASSERT
            assertEquals(e.getMessage(), "Manifest specifies document not present in zip: nosuchfile.pdf");
        }
    }

    /**
     * Rejects unrecognised file types
     *
     * We only want to allow files wil recognised extensions.  For example if the zip contain an .exe or a .zip
     * file then it should be rejected.
     */
    @Test
    public void rejectsZipContainingUnrecognisedFileTypes() throws Exception {
        // ARRANGE - rename one of the files to have a zip extension and update the manifest to match
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("rejectsZipContainingUnrecognisedFileTypes", "fixtures/exampleZipContents");
        Manifest manifest = loadManifest(fixturePath);
        ManifestEntry firstManifestEntry = manifest.getEntries().get(0);
        Path oldpath = fixturePath.resolve(firstManifestEntry.getFilename());
        Path newPath = fixturePath.resolve(firstManifestEntry.getFilename().split("\\.")[0] + ".zip");
        firstManifestEntry.setFilename(newPath.getFileName().toString());
        saveManifest(manifest, fixturePath);
        FileUtils.moveFile(oldpath.toFile(), newPath.toFile());

        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);
        try {
            // ACT
            sut.importApsZip(zip, new Publication());
            fail("An exception should have been thrown");
        } catch (ApsZipImporterException e) {
            // ASSERT
            assertTrue(startsWith(e.getMessage(), "File has an unsupported file extension"));
        }
    }

    /**
     * Test that the link rewriting we do to the imported HTML is done correctly.
     *
     * The types of rewrites we perform are:
     * - direct links to documents that are part of this publication
     * - links to other pages within this publication
     * - anchor links within the page
     * - anchor links to other pages in this publications
     */
    @Test
    public void linkRewriting() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixtureToTmpDirectory("linkRewriting", "fixtures/exampleZipContents");
        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);

        // ACT
        String path = sut.importApsZip(zip, new Publication());

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node pagesNode = publicationFolder.getNode("pages");

        Node page0 = pagesNode.getNode("0").getNode("0");
        Node page1 = pagesNode.getNode("1").getNode("1");
        Node page2 = pagesNode.getNode("2").getNode("2");

        // the contents page links to the other pages - make sure they are linked correctly
        // to check this we need to ensure that a facet exists for each of the linked to pages and that the html has
        // been changed to link to these factes by name
        Node page0Content = page0.getNode("govscot:content");
        String html = page0Content.getProperty("hippostd:content").getString();
        Map<String, Node> docbaseToFacet = new HashMap<>();
        NodeIterator it = page0Content.getNodes();
        while (it.hasNext()) {
            Node next = it.nextNode();

            // skip the ones to images....
            if (next.getName().endsWith(".gif")) {
                continue;
            }
            docbaseToFacet.put(next.getProperty("hippo:docbase").getString(), next);
        }
        // page 0 has a fact for page1 and the link has been rewritten
        assertTrue(docbaseToFacet.containsKey(page1.getIdentifier()));
        assertTrue(html.contains(String.format("<a href=\"%s\">", page1.getIdentifier())));

        // page 0 has a fact for page1 and the link has been rewritten
        assertTrue(docbaseToFacet.containsKey(page2.getIdentifier()));
        assertTrue(html.contains(String.format("<a href=\"%s\">", page2.getIdentifier())));

        // page one has a link to a local anchor in page2
        String expectedLinkToLocalAnchor = String.format("<a href=\"%s\">", "/publications/example-publication/pages/2#inPageAnchor");
        assertTrue(page1.getNode("govscot:content").getProperty("hippostd:content").getString().contains(expectedLinkToLocalAnchor));

        // page two has a link to an anchor within itself
        assertTrue(page2.getNode("govscot:content").getProperty("hippostd:content").getString().contains(expectedLinkToLocalAnchor));
    }

    /**
     * Import a sample zip and assert some properties
     */
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
        assertEquals(1, documentsFolder.getNodes().getSize());
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

    void saveMetadata(Metadata metadata, Path path) throws IOException {
        Path metadataPath = findMetadata(path);
        LOG.info("Saving metedata to {}", metadataPath);
        MetadataWrapper metadataWrapper = new MetadataWrapper();
        metadataWrapper.setMetadata(metadata);
        objectMapper.writeValue(metadataPath.toFile(), metadataWrapper);
    }

    Metadata loadMetadata(Path path) throws MetadataParserException, IOException {
        Path metadataPath = findMetadata(path);
        return new MetadataParser().parse(new FileInputStream(metadataPath.toFile()));
    }

    void saveManifest(Manifest manifest, Path path) throws IOException {
        Path manifestPath = path.resolve("manifest.txt");
        PrintWriter writer = new PrintWriter(new FileOutputStream(manifestPath.toFile()));
        for (ManifestEntry entry : manifest.getEntries()) {
            String line = String.format("%s : %s", entry.getFilename(), entry.getTitle());
            writer.println(line);
        }
        writer.close();
    }

    Manifest loadManifest(Path path) throws ManifestParserException, IOException {
        Path manifestPath = path.resolve("manifest.txt");
        return new ManifestParser().parse(new FileInputStream(manifestPath.toFile()));
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
