package scot.gov.publications.hippo;

import org.junit.Test;
import org.mockito.Mockito;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TopicMappingsTest {

    @Test
    public void updateTopicIgnoresIfNodeAlreadyHasATopic() throws RepositoryException {

        // ARRANGE
        Session session = mock(Session.class);
        TopicMappings sut = new TopicMappings(session);
        Node node = mock(Node.class);
        when(node.hasNode("govscot:topics")).thenReturn(true);

        // ACT
        sut.updateTopics(node, "Agriculture");

        // ASSERT
        verify(node, never()).addNode("govscot:topics", "hippo:mirror");
    }

    @Test
    public void updateTopicIgnoresUnmappableTopic() throws RepositoryException {

        // ARRANGE
        Session session = mock(Session.class);
        TopicMappings sut = new TopicMappings(session);
        Node node = mock(Node.class);

        // ACT
        sut.updateTopics(node, "Space Travel");

        // ASSERT
        verify(node, never()).addNode("govscot:topics", "hippo:mirror");
    }

    @Test
    public void updateTopicUpdatesMappedTopic() throws RepositoryException {

        // ARRANGE
        Session session = mock(Session.class);
        TopicMappings sut = new TopicMappings(session);
        Node node = mock(Node.class);
        Node topicNode = mock(Node.class);
        Node topicMirror = mock(Node.class);
        when(topicNode.getIdentifier()).thenReturn("farming-and-rural-id");
        when(node.addNode("govscot:topics", "hippo:mirror")).thenReturn(topicMirror);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.findOne(Mockito.same(session), any(), eq("Farming and rural"))).thenReturn(topicNode);

        // ACT
        sut.updateTopics(node, "Agriculture");

        // ASSERT
        verify(topicMirror).setProperty("hippo:docbase", "farming-and-rural-id");
    }

    @Test
    public void topicIgnoredIfNoTopicNodePresent() throws RepositoryException {

        // ARRANGE
        Session session = mock(Session.class);
        TopicMappings sut = new TopicMappings(session);
        Node node = mock(Node.class);
        Node topicNode = mock(Node.class);
        Node topicMirror = mock(Node.class);
        when(topicNode.getIdentifier()).thenReturn("farming-and-rural-id");
        when(node.addNode("govscot:topics", "hippo:mirror")).thenReturn(topicMirror);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.findOne(Mockito.same(session), any(), eq("Farming and rural"))).thenReturn(null);

        // ACT
        sut.updateTopics(node, "Agriculture");

        // ASSERT
        verify(topicMirror, never()).setProperty("hippo:docbase", "farming-and-rural-id");
    }

}