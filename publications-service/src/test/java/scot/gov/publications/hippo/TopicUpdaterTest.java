package scot.gov.publications.hippo;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Arrays;

import static org.mockito.Mockito.*;

/**
 * Created by z418868 on 08/04/2019.
 */
public class TopicUpdaterTest {

    @Test
    public void updatesLegacyTopicAsExcepted() throws Exception {

        // ARRANGE
        Metadata input = metadata("Agriculture");
        Node publicationNode = mock(Node.class);
        Node mirror = mock(Node.class);
        Mockito.when(publicationNode.addNode("govscot:topics", "hippo:mirror")).thenReturn(mirror);

        TopicsUpdater sut = new TopicsUpdater(null);
        sut.hippoUtils = mock(HippoUtils.class);

        Node topicNode = topicNode("farming-id");
        when(sut.hippoUtils.findOneXPath(any(), contains("farming-and-rural"))).thenReturn(topicNode);

        // ACT
        sut.ensureTopics(publicationNode, input);

        // ASSERT
        verify(mirror).setProperty("hippo:docbase", "farming-id");

    }

    @Test
    public void topicAlreadyExists() throws Exception {

        // ARRANGE
        Metadata input = metadata("Agriculture");
        Node publicationNode = mock(Node.class);
        Node mirror = mock(Node.class);
        Mockito.when(publicationNode.addNode("govscot:topics", "hippo:mirror")).thenReturn(mirror);

        TopicsUpdater sut = new TopicsUpdater(null);
        sut.hippoUtils = mock(HippoUtils.class);
        Node topicNode = topicNode("farming-id");
        when(sut.hippoUtils.findOneXPath(any(), contains("farming-and-rural"))).thenReturn(topicNode);
        when(sut.hippoUtils.find(any(), any())).thenReturn(topicNode);

        // ACT
        sut.ensureTopics(publicationNode, input);

        // ASSERT
        verify(publicationNode, never()).addNode(eq("govscot:topics"), any());

    }

    @Test
    public void ignoresUrecognisedLegacyTopic() throws RepositoryException {
        // ARRANGE
        Metadata input = metadata("NO SUCH TOPIC");
        Node publicationNode = mock(Node.class);
        Node mirror = mock(Node.class);
        Mockito.when(publicationNode.addNode("govscot:topics", "hippo:mirror")).thenReturn(mirror);

        TopicsUpdater sut = new TopicsUpdater(null);
        sut.hippoUtils = mock(HippoUtils.class);
        Node topicNode = topicNode("farming-id");
        when(sut.hippoUtils.findOneXPath(any(), contains("farming-and-rural"))).thenReturn(topicNode);
        when(sut.hippoUtils.find(any(), any())).thenReturn(topicNode);

        // ACT
        sut.ensureTopics(publicationNode, input);

        // ASSERT
        verify(publicationNode, never()).addNode(eq("govscot:topics"), any());
    }

    Node topicNode(String id) throws RepositoryException {
        Node node = mock(Node.class);
        when(node.getIdentifier()).thenReturn(id);
        return node;
    }

    Metadata metadata(String legacyTopic, String ... topics) {
        Metadata metadata = new Metadata();
        metadata.setTopic(legacyTopic);
        metadata.getTopics().addAll(Arrays.asList(topics));
        return metadata;
    }
}
