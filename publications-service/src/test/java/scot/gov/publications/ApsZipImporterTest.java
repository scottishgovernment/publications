package scot.gov.publications;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.jcr.TestRepository;
import scot.gov.publications.hippo.DocumentUploader;
import scot.gov.publications.hippo.ImageUploader;
import scot.gov.publications.hippo.PublicationNodeUpdater;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.hippo.ZipFixtures;
import scot.gov.publications.hippo.pages.PublicationPageUpdater;
import scot.gov.publications.manifest.ManifestExtractor;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.Publication;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.time.LocalDateTime;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySetOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApsZipImporterTest {

    private Session session;

    ApsZipImporter sut;

    @Before
    public void setUp() throws RepositoryException {

        sut = new ApsZipImporter();

        // get a session with the test repository
        session = TestRepository.session();
        sut.sessionFactory = Mockito.mock(SessionFactory.class);
        Mockito.when(sut.sessionFactory.newSession()).thenReturn(TestRepository.session());

        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getHippo().setUser("testuser");
    }

    @After
    public void tearDown() throws RepositoryException {
        session.logout();
    }

    //@Test - this will not run on Jenkins as it uses Exif tool and graphics magik
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
        Assert.assertTrue("isContentPage", contentsPage.getProperty("govscot:contentsPage").getBoolean());

        // has document nodes as expected
        Node documentsFolder = publicationFolder.getNode("documents");
        Assert.assertEquals(1, documentsFolder.getNodes().getSize());
    }

}
