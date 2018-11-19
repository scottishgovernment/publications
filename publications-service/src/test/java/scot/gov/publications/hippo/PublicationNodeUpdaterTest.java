package scot.gov.publications.hippo;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.metadata.EqualityInfo;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static scot.gov.publications.hippo.Constants.GOVSCOT_GOVSCOTURL;

public class PublicationNodeUpdaterTest {

    /**
     * Throws exception if old url is unparsable
     * wraps jcr expection as expected
     */

    @Test
    public void resusesNodeWithISBNIfPresent() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicMappings.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(nodeWithISBN);
        Metadata input = metadata();

        // ACT
        sut.createOrUpdatePublicationNode(input);

        // ASSERT - should not have created any new document nodes
        Mockito.verify(sut.nodeFactory, never()).newDocumentNode(any(), any(), any(), any(), any());
    }

    @Test
    public void createsNewNodeIfNoNodeWithISBN() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicMappings.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        when(nodeWithISBN.getParent()).thenReturn(handle);

        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any())).thenReturn(nodeWithISBN);

        Metadata input = metadata();

        // ACT
        sut.createOrUpdatePublicationNode(input);

        // ASSERT - should have created new document node
        Mockito.verify(sut.nodeFactory).newDocumentNode(any(), any(), any(), any(), any());
    }

    @Test
    public void doesNotSetGovScotUrlIfBlank() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicMappings.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        when(nodeWithISBN.getParent()).thenReturn(handle);
        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(nodeWithISBN);
        Metadata input = metadata();
        input.setUrl("");

        // ACT
        sut.createOrUpdatePublicationNode(input);

        // ASSERT - should not have created any new document nodes
        Mockito.verify(nodeWithISBN, never()).setProperty(eq(GOVSCOT_GOVSCOTURL), anyString());
    }

    @Test(expected = ApsZipImporterException.class)
    public void throwsExceptionIfUrlIsUnparsable() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicMappings.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        when(nodeWithISBN.getParent()).thenReturn(handle);

        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any())).thenReturn(nodeWithISBN);

        Metadata input = metadata();
        input.setUrl("INVALID URL");

        // ACT
        sut.createOrUpdatePublicationNode(input);

        // ASSERT - see expected exception
    }

    @Test(expected = ApsZipImporterException.class)
    public void repoExceptionWrappeCorrectly() throws Exception {
        // ARRANGE
        PublicationNodeUpdater sut = new PublicationNodeUpdater(null, null);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.nodeFactory = mock(HippoNodeFactory.class);
        sut.topicMappings = mock(TopicMappings.class);
        sut.hippoPaths =  mock(HippoPaths.class);
        Node nodeWithISBN = mock(Node.class);
        Node handle = mock(Node.class);
        when(nodeWithISBN.getParent()).thenReturn(handle);

        when(sut.hippoUtils.findOne(any(), startsWith("SELECT * FROM govscot:Publication WHERE govscot:isbn ="))).thenReturn(null);
        when(sut.nodeFactory.newDocumentNode(any(), any(), any(), any(), any())).thenThrow(new RepositoryException("arg"));

        Metadata input = metadata();

        // ACT
        sut.createOrUpdatePublicationNode(input);

        // ASSERT - see expected exception
    }

    Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.setId("id");
        metadata.setIsbn("isbn");
        metadata.setTitle("title");
        metadata.setPublicationDate(LocalDateTime.now());
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
