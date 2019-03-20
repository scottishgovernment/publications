package scot.gov.publications.hippo;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
        sut.addTopicsIfAbsent(node, metadata("Agriculture", emptyList()));

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
        sut.addTopicsIfAbsent(node, metadata("Space Travel", singletonList("Scuba diving")));

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
        when(sut.hippoUtils.findOneXPath(Mockito.same(session), contains("Farming and rural"))).thenReturn(topicNode);

        // ACT
        sut.addTopicsIfAbsent(node, metadata("Agriculture", emptyList()));

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
        sut.addTopicsIfAbsent(node, metadata("Agriculture", emptyList()));

        // ASSERT
        verify(topicMirror, never()).setProperty("hippo:docbase", "farming-and-rural-id");
    }

    Metadata metadata(String topic, List<String> topics) {
        Metadata metadata = new Metadata();
        metadata.setTopic(topic);
        metadata.setTopics(topics);
        return metadata;
    }
}