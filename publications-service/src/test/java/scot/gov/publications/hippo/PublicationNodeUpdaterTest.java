package scot.gov.publications.hippo;

import org.apache.jackrabbit.value.StringValue;
import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.repo.Publication;
import scot.gov.publishing.sluglookup.SlugLookups;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.mockito.Mockito.*;
import static scot.gov.publications.hippo.Constants.GOVSCOT_GOVSCOTURL;
import static scot.gov.publications.hippo.Constants.HIPPOSTD_STATE;

public class PublicationNodeUpdaterTest {

    @Test
    public void reusesNodeWithISBNIfPresent() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicsUpdater.class);
        sut.pathStrategy = mock(PublicationPathStrategy.class);
        sut.hippoPaths = mock(HippoPaths.class);
        sut.sitemap = mock(Sitemap.class);
        sut.slugLookups = mock(SlugLookups.class);
        Node publicationFolder = mock(Node.class);

        when(sut.hippoPaths.ensurePath(any())).thenReturn(publicationFolder);
        Node nodeWithISBN = mock(Node.class);
        Property slug = mock(Property.class);
        when(slug.toString()).thenReturn("slug");
        when(nodeWithISBN.getProperty("govscot:slug")).thenReturn(slug);
        Node handle = mock(Node.class);
        when(handle.getParent()).thenReturn(publicationFolder);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        Property state =  stringProperty("published");
        when(nodeWithISBN.getProperty(eq("hippostd:state"))).thenReturn(state);
        sut.session = sessionbWithPubs(nodeWithISBN);
        Metadata input = metadata();

        Property tags = mock(Property.class);
        when(tags.getValues()).thenReturn(new Value[]{});
        when(nodeWithISBN.getProperty("hippostd:tags")).thenReturn(tags);
        sut.tagUpdater.hippoUtils = mock(HippoUtils.class);

        // ACT
        sut.createOrUpdatePublicationNode(input, new Publication());

        // ASSERT - should not have created any new document nodes
        Mockito.verify(sut.nodeFactory, never()).newDocumentNode(any(), any(), any(), any(), any(), anyBoolean());
    }

    Property stringProperty(String value) throws RepositoryException {
        Property p = mock(Property.class);
        StringValue val = mock(StringValue.class);
        when(val.getString()).thenReturn(value);
        when(p.getString()).thenReturn(value);
        return p;
    }

    @Test
    public void createsNewNodeIfNoNodeWithISBN() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicsUpdater.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        sut.pathStrategy = mock(PublicationPathStrategy.class);
        sut.session = sessionbWithPubs();
        sut.sitemap = mock(Sitemap.class);
        sut.slugLookups = mock(SlugLookups.class);
        Node nodeWithISBN = mock(Node.class);
        Property slug = mock(Property.class);
        when(slug.toString()).thenReturn("slug");
        when(nodeWithISBN.getProperty("govscot:slug")).thenReturn(slug);
        Property state = mock(Property.class);
        when(state.getString()).thenReturn("published");
        when(nodeWithISBN.getProperty(HIPPOSTD_STATE)).thenReturn(state);
        Node handle = mock(Node.class);
        Node folder = mock(Node.class);
        when(handle.getParent()).thenReturn(folder);
        when(sut.hippoPaths.ensurePath(any())).thenReturn(folder);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(nodeWithISBN);

        Metadata input = metadata();

        Property tags = mock(Property.class);
        when(tags.getValues()).thenReturn(new Value[]{});
        when(nodeWithISBN.getProperty("hippostd:tags")).thenReturn(tags);
        sut.tagUpdater.hippoUtils = mock(HippoUtils.class);

        // ACT
        sut.createOrUpdatePublicationNode(input, new Publication());

        // ASSERT - should have created new document node
        Mockito.verify(sut.nodeFactory).newDocumentNode(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void doesNotSetGovScotUrlIfBlank() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicsUpdater.class);
        sut.pathStrategy = mock(PublicationPathStrategy.class);
        sut.hippoPaths = mock(HippoPaths.class);
        sut.sitemap = mock(Sitemap.class);
        sut.slugLookups = mock(SlugLookups.class);
        Node publicationFolder = mock(Node.class);

        when(sut.hippoPaths.ensurePath(any())).thenReturn(publicationFolder);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        Node folder = mock(Node.class);
        when(handle.getParent()).thenReturn(folder);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        Property slug = mock(Property.class);
        when(slug.toString()).thenReturn("slug");
        when(nodeWithISBN.getProperty("govscot:slug")).thenReturn(slug);
        Property state =  stringProperty("published");
        when(nodeWithISBN.getProperty(eq("hippostd:state"))).thenReturn(state);
        sut.session = sessionbWithPubs(nodeWithISBN);
        Metadata input = metadata();
        input.setUrl("");
        Property tags = mock(Property.class);
        when(tags.getValues()).thenReturn(new Value[]{});
        when(nodeWithISBN.getProperty("hippostd:tags")).thenReturn(tags);
        sut.tagUpdater.hippoUtils = mock(HippoUtils.class);

        // ACT
        sut.createOrUpdatePublicationNode(input, new Publication());

        // ASSERT
        Mockito.verify(nodeWithISBN, never()).setProperty(eq(GOVSCOT_GOVSCOTURL), anyString());
    }

    @Test(expected = ApsZipImporterException.class)
    public void throwsExceptionIfUrlIsUnparsable() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicsUpdater.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        sut.pathStrategy = mock(PublicationPathStrategy.class);
        sut.session = sessionbWithPubs();
        sut.sitemap = mock(Sitemap.class);
        sut.slugLookups = mock(SlugLookups.class);
        Node nodeWithISBN = mock(Node.class);
        Property state = mock(Property.class);
        when(state.getString()).thenReturn("published");
        when(nodeWithISBN.getProperty(HIPPOSTD_STATE)).thenReturn(state);
        Property slug = mock(Property.class);
        when(slug.toString()).thenReturn("slug");
        when(nodeWithISBN.getProperty("govscot:slug")).thenReturn(slug);
        Node handle = mock(Node.class);
        Node folder = mock(Node.class);
        when(handle.getParent()).thenReturn(folder);
        when(sut.hippoPaths.ensurePath(any())).thenReturn(folder);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(nodeWithISBN);
        Property tags = mock(Property.class);
        when(tags.getValues()).thenReturn(new Value[]{});
        when(nodeWithISBN.getProperty("hippostd:tags")).thenReturn(tags);
        sut.tagUpdater.hippoUtils = mock(HippoUtils.class);
        //publicationNode.getProperty()
        Metadata input = metadata();
        input.setUrl("INVALID URL");

        // ACT
        sut.createOrUpdatePublicationNode(input, new Publication());

        // ASSERT - see expected exception
    }

    @Test(expected = ApsZipImporterException.class)
    public void repoExceptionWrappeCorrectly() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicsUpdater.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        sut.pathStrategy = mock(PublicationPathStrategy.class);
        sut.session = sessionbWithPubs();
        sut.sitemap = mock(Sitemap.class);
        sut.slugLookups = mock(SlugLookups.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        Node folder = mock(Node.class);
        when(sut.hippoPaths.ensurePath(any())).thenReturn(folder);
        when(nodeWithISBN.getParent()).thenReturn(handle);

        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any(), anyBoolean())).thenThrow(new RepositoryException("arg"));

        Metadata input = metadata();

        // ACT
        sut.createOrUpdatePublicationNode(input, new Publication());

        // ASSERT - see expected exception
    }

    Session sessionbWithPubs(Node ...pubs) throws RepositoryException {
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        Query query = mock(Query.class);
        QueryResult result = mock(QueryResult.class);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getQueryManager()).thenReturn(queryManager);
        when(queryManager.createQuery(any(), any())).thenReturn(query);
        when(query.execute()).thenReturn(result);
        when(result.getNodes()).thenReturn(HippoUtilsTest.iterator(Arrays.asList(pubs)));
        return session;
    }

    Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.setId("id");
        metadata.setIsbn("isbn");
        metadata.setTitle("title");
        metadata.setPublicationDate(LocalDateTime.now());
        metadata.setPublicationDateWithTimezone(ZonedDateTime.now());
        metadata.setUrl("https://www2.gov.scot/url");
        metadata.setAlternateUrl("https://www2.gov.scot/alternateurl");
        metadata.setExecutiveSummary("executive summary");
        metadata.setDescription("description");
        metadata.setTopic("Economy");
        metadata.setPublicationType("Consulatation");
        metadata.setKeywords("keywords");
        metadata.setResearchCategory("researchCategory");
        metadata.setStatisticsCategory("statisticsCategory");
        return metadata;
    }
}
