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
import scot.gov.publications.hippo.HippoUtils;
import scot.gov.publications.hippo.ZipFixtures;
import scot.gov.publications.imageprocessing.ImageProcessing;
import scot.gov.publications.imageprocessing.ImageProcessingException;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestEntry;
import scot.gov.publications.manifest.ManifestParser;
import scot.gov.publications.manifest.ManifestParserException;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataParser;
import scot.gov.publications.metadata.MetadataParserException;
import scot.gov.publications.metadata.MetadataWrapper;
import scot.gov.publications.repo.Publication;

import javax.jcr.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipFile;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertFalse;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.junit.Assert.*;
import static scot.gov.publications.hippo.Constants.HIPPOSTD_TAGS;

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
        System.setProperty("java.awt.headless", "true");

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
    public void tearDown() throws IOException {
        session.logout();

        ZipFixtures.deleteFixtures();
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
        Path fixturePath = ZipFixtures.copyFixture("expectedFieldsRetainedWhenReimportingPublication!");
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
        Path fixturePath2 = ZipFixtures.copyFixture("expectedFieldsRetainedWhenReimportingPublication2");
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

        // changed properties should be the value set in code rather than the values from the second zip.
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
        Path fixturePath = ZipFixtures.copyFixture("publicationMovedToCorrectFolder!");
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
        Path fixturePath2 = ZipFixtures.copyFixture("publicationMovedToCorrectFolder2");
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
     * - Reimport it with a publication date in the past - it should be published with no workflow job
     * - Reimport it again with a publication date in the future - the workflow job should be back and the publication
     *   should not be published
     */
    @Test
    public void workFlowJobAndPublishStatusSetCorrectly() throws Exception {
        // import sample publication with publication date in future then assert that it is not published
        Path fixturePath = ZipFixtures.copyFixture("workFlowJobAndPublishStatusSetCorrectly");
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
        Path fixturePathNoHtml = ZipFixtures.copyFixture("canUploadZipWithNoHtml");
        File [] htmlFiles = fixturePathNoHtml.toFile().listFiles(file -> endsWith(file.getName(), ".htm"));
        for (File htmlFile : htmlFiles) {
            htmlFile.delete();
        }
        ZipFile zipWithoutHtml = ZipFixtures.zipDirectory(fixturePathNoHtml);

        Path fixturePathHtml = ZipFixtures.copyFixture("canUploadZipWithHtml");
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
        Path fixturePath1 = ZipFixtures.copyFixture("slugDisambiguatedIfAlreadyTaken1");
        Metadata metadata1 = loadMetadata(fixturePath1);
        metadata1.setIsbn("111");
        metadata1.setTitle("publication title");
        saveMetadata(metadata1, fixturePath1);
        fixturePath1.toFile().list();
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath1);

        Path fixturePath2 = ZipFixtures.copyFixture("slugDisambiguatedIfAlreadyTaken1");
        Metadata metadata2 = loadMetadata(fixturePath1);
        metadata2.setIsbn("222");
        metadata2.setTitle("publication title");
        saveMetadata(metadata2, fixturePath2);
        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath2);

        // ACT -- import them both
        String path1 = sut.importApsZip(zip1, new Publication());
        String path2 = sut.importApsZip(zip2, new Publication());

        // ASSERT - the second path should have been disambiguated
        assertEquals(path1, "/content/documents/govscot/publications/statistics/2018/10/publication-title");
        assertEquals(path2, "/content/documents/govscot/publications/statistics/2018/10/publication-title-2");
    }

    /**
     * Rejects unrecognised publication type
     */
    @Test(expected = ApsZipImporterException.class)
    public void rejectsInvalidPublicationtype() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("rejectsInvalidPublicationtype");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("rejectsInvalidPublicationtype");
        metadata.setPublicationType("invalid");
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        sut.importApsZip(zip1, publication);

        // ASSERT -- exect an exception
    }

    /**
     * Rejects invalid manifest
     */
    @Test(expected = ApsZipImporterException.class)
    public void rejectsInvalidManifest() throws Exception {
        // ARRANGE - write over the manifest with invalid values
        Path fixturePath = ZipFixtures.copyFixture("rejectsInvalidManifest");
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
        // ARRANGE - remove the metadata
        Path fixturePath = ZipFixtures.copyFixture("rejectsEmptyManifest");
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
        // ARRANGE - remove the metadata
        Path fixturePath = ZipFixtures.copyFixture("rejectsMissingManifest");
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
        Path fixturePath = ZipFixtures.copyFixture("rejectsMissingMetadata");
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
        Path fixturePath = ZipFixtures.copyFixture("rejectsTruncatedMetadata");
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
     * Rejects invalid links
     *
     * We have seen examples where links contain invalid markup,
     * and the broken links result in empty pages in the CMS.
     *
     */
    @Test
    public void rejectsInvalidLinks() throws Exception {
        // ARRANGE - add HTM with malformed links
        Path fixturePath = ZipFixtures.copyFixture("rejectsInvalidLinks");
        Path htmWithInvalidLinks  = Paths.get(ApsZipImporterTest.class.getResource("/htmWithInvalidLinks.htm").getPath());
        Files.copy(htmWithInvalidLinks, fixturePath.resolve("SCT12181804281-01.htm"), StandardCopyOption.REPLACE_EXISTING);

        ZipFile zip = ZipFixtures.zipDirectory(fixturePath);
        try {
            // ACT
            sut.importApsZip(zip, new Publication());
            fail("An exception should have been thrown");

        } catch (ApsZipImporterException e) {
            // ASSERT - ApsZipImporterException thrown because invalid links found
            assertEquals(e.getMessage(), "Invalid Links: http://www.gov.scot/<abbr>ISBN</abbr>/9781836018667");
        }
    }

    /**
     * Rejects zip if manifest mentions file not in zip
     *
     * If the manifest file mentions a file that is not present in the zip then an exception should be thrown.
     */
    @Test
    public void rejectsZipIfManifestMentionsFileNotInZip() throws Exception {
        // ARRANGE - add a non-existent entry to the manifest and save it
        Path fixturePath = ZipFixtures.copyFixture("rejectsZipIfManifestMentionsFileNotInZip");
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
        Path fixturePath = ZipFixtures.copyFixture("rejectsZipContainingUnrecognisedFileTypes");
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
            assertTrue(startsWith(e.getMessage(), "Unsupported file types in the manifest:"));
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
        Path fixturePath = ZipFixtures.copyFixture("linkRewriting");
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
        assertTrue(docbaseToFacet.containsKey(page1.getParent().getIdentifier()));
        assertTrue(html.contains(String.format("<a href=\"%s\">", page1.getParent().getIdentifier())));

        // page 0 has a fact for page1 and the link has been rewritten
        assertTrue(docbaseToFacet.containsKey(page2.getParent().getIdentifier()));
        assertTrue(html.contains(String.format("<a href=\"%s\">", page2.getParent().getIdentifier())));

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

    /**
     * Create a publication with a time in the past with a GMT timezone and ensure that the publication date has the
     * right timezone
     */
    @Test
    public void publishDateInPastWithGMTTimezoneAHandledcorrectly() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("publishDateInPastWithGMTTimezoneAHandledcorrectly");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("publishDateInPastWithGMTTimezoneAHandledcorrectly");
        metadata.setPublicationDate(publishDateTimeInPastGMT());
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node index = publicationFolder.getNode("index/index");
        Calendar pulicatonDate = index.getProperty("govscot:publicationDate").getDate();

        // explicitly assert the timezone
        assertEquals("GMT", pulicatonDate.getTimeZone().getID());

        // compare the publications date set on the node with what we think it should be
        Calendar expectedPubDate = GregorianCalendar.from(metadata.getPublicationDate().atZone(ZoneId.of("GMT")));
        assertEquals(0, pulicatonDate.compareTo(expectedPubDate));
    }

    /**
     * Create a publication with a time in the fiture with a GMT timezone and ensure that the publication date is set
     * as expected
     */
    @Test
    public void publishDateInPastWithBSTTimezoneAHandledcorrectly() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("publishDateInPastWithBSTTimezoneAHandledcorrectly");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("publishDateInPastWithBSTTimezoneAHandledcorrectly");
        metadata.setPublicationDate(publishDateTimeInPastBST());
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node index = publicationFolder.getNode("index/index");
        Calendar pulicatonDate = index.getProperty("govscot:publicationDate").getDate();

        // explicitly assert the timezone
        assertEquals("GMT+01:00", pulicatonDate.getTimeZone().getID());

        // compare the publications date set on the node with what we think it should be
        Calendar expectedPubDate = GregorianCalendar.from(metadata.getPublicationDate().atZone(ZoneId.of("GMT+01:00")));
        assertEquals(0, pulicatonDate.compareTo(expectedPubDate));
    }

    /**
     * Create a publication with a time in GMT and ensure that the publication date is set as expected
     */
    @Test
    public void publishDateInFutureWithGMTTimezoneHandledCorrectly() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("publishDateInFutureWithGMTTimezoneHandledCorrectly");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("publishDateInFutureWithGMTTimezoneHandledCorrectly");
        metadata.setPublicationDate(publishDateTimeInFutureGMT());
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");
        Node trigger = handle.getNode("hippo:request/hipposched:triggers/default");
        Calendar publicatonDate = index.getProperty("govscot:publicationDate").getDate();
        Calendar triggerDate = trigger.getProperty("hipposched:nextFireTime").getDate();

        // explicitly assert the timezone
        assertEquals("GMT", publicatonDate.getTimeZone().getID());
        assertEquals("GMT", triggerDate.getTimeZone().getID());

        // compare the publications date set on the node with what we think it should be
        Calendar expectedPubDate = GregorianCalendar.from(metadata.getPublicationDate().atZone(ZoneId.of("GMT")));
        assertEquals(0, publicatonDate.compareTo(expectedPubDate));
        assertEquals(0, triggerDate.compareTo(expectedPubDate));
    }

    /**
     * Create a publication with a time in BST and ensure that the publication date is set as expected
     */
    @Test
    public void publishDateInFutureWithBSTTimezoneHandledCorrectly() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("publishDateInFutureWithBSTTimezoneHandledCorrectly");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("publishDateInFutureWithBSTTimezoneHandledCorrectly");
        metadata.setPublicationDate(publishDateTimeInFutureBST());
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");
        Node trigger = handle.getNode("hippo:request/hipposched:triggers/default");
        Calendar pulicatonDate = index.getProperty("govscot:publicationDate").getDate();
        Calendar triggerDate = trigger.getProperty("hipposched:nextFireTime").getDate();

        // explicitly assert the timezone
        assertEquals("GMT+01:00", pulicatonDate.getTimeZone().getID());
        assertEquals("GMT+01:00", triggerDate.getTimeZone().getID());
        // compare the publications date set on the node with what we think it should be
        Calendar expectedPubDate = GregorianCalendar.from(metadata.getPublicationDate().atZone(ZoneId.of("GMT+01:00")));
        assertEquals(expectedPubDate.getTimeInMillis(), pulicatonDate.getTimeInMillis());
    }

    @Test
    public void tagsAddedIfPresent() throws Exception {
        // import sample publication
        Path fixturePath = ZipFixtures.copyFixture("tagsAddedIfPresent!");
        Metadata metadata = loadMetadata(fixturePath);
        List<String> tags = new ArrayList<>();
        Collections.addAll(tags, "one", "two", "three");
        metadata.setTags(tags);
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        tagSet(path).equals(new HashSet<>(tags));
    }

    @Test
    public void directoratesAddedIfPresent() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("directoratesAddedIfPresent");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setPrimaryResponsibleDirectorate("advanced-learning-and-science");
        metadata.setSecondaryResponsibleDirectorates(singletonList("advanced-learning-and-science"));
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");
        assertTrue(index.hasNode("govscot:responsibleDirectorate"));
        assertTrue(index.hasNode("govscot:secondaryResponsibleDirectorate"));
    }


    @Test
    public void policiesAddedCorrectly() throws Exception {
        // ARRANGE - create two zips.  The first is related to digital and a non existant policy.  the second is related to biodiversity
        Path fixturePath1= ZipFixtures.copyFixture("policiesAddedCorrectly1");
        Metadata metadata = loadMetadata(fixturePath1);
        metadata.getPolicies().add("digital");
        metadata.getPolicies().add("no-such-policy");
        saveMetadata(metadata, fixturePath1);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath1);

        Path fixturePath2= ZipFixtures.copyFixture("policiesAddedCorrectly2");
        saveMetadata(metadata, fixturePath2);
        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath2);


        // ACT

        // import both of the zips.  We should end up with the publication related to both digital and biodiversity
        String path;
        path = sut.importApsZip(zip1, new Publication());
        path = sut.importApsZip(zip2, new Publication());

        // ASSERT
        Node publicationFolder = session.getNode(path);
        assertIsRelatedTo(publicationFolder, "digital");
    }

    void assertIsRelatedTo(Node publicationFolder, String policyName) throws RepositoryException {
        String publicationHandleId = publicationFolder.getNode("index").getIdentifier();
        Node latestNode = session.getNode("/content/documents/govscot/policies/" + policyName + "/latest/latest");
        Node relatedItem = new HippoUtils().find(latestNode.getNodes("govscot:relatedItems"),
                item -> item.getProperty("hippo:docbase").getString().equals(publicationHandleId));
        Assert.assertNotNull(relatedItem);
    }

    @Test(expected = ApsZipImporterException.class)
    public void exceptionThrownIfDirectorateIsNotPresent() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("exceptionThrownIfDirectorateIsNotPresent");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setPrimaryResponsibleDirectorate("NO SUCH DIRECTORATE");
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        sut.importApsZip(zip1, publication);

        // ASSERT - expect exception
    }

    @Test
    public void rolesAddedIfPresentInMetadata() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("rolesAddedIfPresentInMetadata");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setPrimaryResponsibleRole("commissioner-fair-access");
        metadata.setSecondaryResponsibleRoles(singletonList("sheila-rowan"));
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // ASSERT
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");
        assertTrue(index.hasNode("govscot:responsibleRole"));
        assertTrue(index.hasNode("govscot:secondaryResponsibleRole"));
    }

    @Test(expected = ApsZipImporterException.class)
    public void exceptionThrownIfRoleIsNotPublished() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("exceptionThrownIfRoleIsNotPublushed");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("exceptionThrownIfRoleIsNotPublushed");
        metadata.setPrimaryResponsibleRole("Commissioner for Fair Access");
        // this role is not published and so the importer should throw and exception
        metadata.setSecondaryResponsibleRoles(singletonList("Chief Veterinary Officer"));
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        sut.importApsZip(zip1, publication);

        // ASSERT - expect exception
    }

    @Test(expected = ApsZipImporterException.class)
    public void exceptionThrownIfRoleIsNotFound() throws Exception {
        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("exceptionThrownIfRoleIsNotFounf");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("exceptionThrownIfRoleIsNotFound");
        metadata.setPrimaryResponsibleRole("Commissioner for Fair Access");
        metadata.setSecondaryResponsibleRoles(singletonList("NO SUCH ROLE"));
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        sut.importApsZip(zip1, publication);

        // ASSERT - expect exception
    }

    @Test
    public void updatesTopicsCorrectly() throws Exception {

        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("updatesTopicsCorrectly");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("updatesTopicsCorrectly");
        metadata.setTopic("");
        saveMetadata(metadata, fixturePath);

        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        sut.importApsZip(zip1, publication);

        // ASSERT - expect exception

    }

    @Test
    public void addsTagsAsExpected() throws Exception {
        // add a publication with some tags

        // change the tags that are in the zip and reimport it - should have added the new ones but left the old ones
        // in place


        // ARRANGE
        Path fixturePath = ZipFixtures.copyFixture("exceptionThrownIfRoleIsNotFounf");
        Metadata metadata = loadMetadata(fixturePath);
        metadata.setIsbn("addsTagsAsExpected");
        Collections.addAll(metadata.getTags(), "all", "good", "boys");
        saveMetadata(metadata, fixturePath);
        ZipFile zip1 = ZipFixtures.zipDirectory(fixturePath);
        Publication publication = new Publication();

        // ACT
        String path = sut.importApsZip(zip1, publication);

        // now add some more tags
        metadata.getTags().clear();
        Collections.addAll(metadata.getTags(), "deserve", "fudge");
        saveMetadata(metadata, fixturePath);
        ZipFile zip2 = ZipFixtures.zipDirectory(fixturePath);
        sut.importApsZip(zip2, publication);

        // ASSERT
        //
        // we should have all of the tags that were added
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");

        Value [] tagValues = index.getProperty(HIPPOSTD_TAGS).getValues();
        Set<String> actual = new HashSet<>();
        for (Value value : tagValues) {
            actual.add(value.getString());
        }

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "all", "good", "boys", "deserve", "fudge");
        assertEquals(expected , actual);
    }

    Set<String> tagSet(String path) throws RepositoryException {
        Node publicationFolder = session.getNode(path);
        Node handle = publicationFolder.getNode("index");
        Node index = handle.getNode("index");
        Property property = index.getProperty(HIPPOSTD_TAGS);
        Value [] tagValues = property.getValues();
        Set<String> tagSet = new HashSet<>();
        for (Value tagValue : tagValues) {
            tagSet.add(tagValue.getString());
        }
        return tagSet;
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
     * since gm is not installed on jenkins we want a fake implementation of ImageProcessing that always returns the same image.
     */
    ImageProcessing fakeImageProcessing() {

        return new ImageProcessing() {
            public File extractPdfCoverImage(InputStream source) throws ImageProcessingException {
                return tmpFile();
            }

            public File thumbnail(InputStream source, int width) throws ImageProcessingException {
                return tmpFile();
            }

            private File tmpFile() throws ImageProcessingException {
                try {
                    Path tmpDir = ZipFixtures.fixturesDirectory();
                    if (!Files.exists(tmpDir)) {
                        Files.createDirectory(tmpDir);
                    }
                    File imageFile = File.createTempFile("coverImageBlah", ".jpeg", tmpDir.toFile());
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

    /**
     * We need a publication date that:
     *  - is in BST
     *  - is in the past
     *  - has the right resolution
     */
    LocalDateTime publishDateTimeInPastBST() {
        return LocalDateTime.of(2018, 8, 01, 01, 01);
    }

    /**
     * We need a publication date that:
     *  - is in GMT
     *  - is in the future
     *  - has the right resolution
     */
    LocalDateTime publishDateTimeInFutureGMT() {
        return LocalDateTime.of(2030, 1, 1, 1, 1, 1);
    }

    /**
     * We need a publication date that:
     *  - is in BST
     *  - is in the future
     *  - has the right resolution
     */
    LocalDateTime publishDateTimeInFutureBST() {
        return LocalDateTime.of(2030, 8, 1, 1, 1, 1);
    }

    /**
     * We need a publication date that:
     *  - is in GMT
     *  - is in the past
     *  - has the right resolution
     */
    LocalDateTime publishDateTimeInPastGMT() {
        return LocalDateTime.of(2018, 01, 01, 01, 01);
    }

}
