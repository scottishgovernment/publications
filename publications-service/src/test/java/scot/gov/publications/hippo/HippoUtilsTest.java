package scot.gov.publications.hippo;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Assert;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HippoUtilsTest {

    @Test
    public void setPropertyIfAbsentSetsIfAbsent() throws RepositoryException {

        // ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        when(node.hasProperty("prop")).thenReturn(false);

        // ACT
        sut.setPropertyIfAbsent(node, "prop", "val");

        // ASSERT
        verify(node).setProperty("prop", "val");
    }

    @Test
    public void setPropertyIfAbsentDoesNotSetIfPresent() throws RepositoryException {
        //ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        when(node.hasProperty("prop")).thenReturn(true);

        // ACT
        sut.setPropertyIfAbsent(node, "prop", "val");

        //ASSERT
        verify(node, never()).setProperty("prop", "val");
    }

    @Test
    public void setPropertyStringsIfAbsentSetsIfAbsent() throws RepositoryException {
        // ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        when(node.hasProperty("prop")).thenReturn(false);

        // ACT
        sut.setPropertyStringsIfAbsent(node, "prop", Collections.singleton("val"));

        // ASSERT
        verify(node).setProperty("prop", new String [] {"val"}, 1);
    }

    @Test
    public void setPropertyStringsIfAbsentDoesNotSetIfPresent() throws RepositoryException {
        //ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        when(node.hasProperty("prop")).thenReturn(true);

        // ACT
        sut.setPropertyStringsIfAbsent(node, "prop", Collections.singleton("val"));

        //ASSERT
        verify(node, never()).setProperty("prop", "val");
    }

    @Test
    public void addHtmlNodeIfAbsentIgnoresIfPresent() throws RepositoryException {

        // ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Node contentNode = mock(Node.class);
        when(node.hasNode("name")).thenReturn(true);

        // ACT
        sut.addHtmlNodeIfAbsent(node, "name", "val");

        // ASSERT
        verify(node, never()).addNode("name", "hippostd:html");
        verify(contentNode, never()).setProperty("hippostd:content", "val");
    }

    @Test
    public void addHtmlNodeIgnoresIfPresent() throws RepositoryException {
        // ARRANGE
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Node contentNode = mock(Node.class);
        when(node.addNode("name", "hippostd:html")).thenReturn(contentNode);
        when(node.hasNode("nodename")).thenReturn(false);

        // ACT
        sut.addHtmlNodeIfAbsent(node, "name", "val");

        // ASSERT
        verify(node).addNode("name", "hippostd:html");
        verify(contentNode).setProperty("hippostd:content", "val");
    }

    @Test
    public void ensureNodeReturnsNodeIfAlreadyExists() throws RepositoryException {
        HippoUtils sut = new HippoUtils();
        Node parent = mock(Node.class);
        Node node = mock(Node.class);
        when(parent.hasNode("name")).thenReturn(true);
        when(parent.getNode("name")).thenReturn(node);

        // ACT
        Node actual = sut.ensureNode(parent, "name", "primType");

        // ASSERT
        assertSame(actual, node);
    }

    @Test
    public void ensureNodeCreatesNodeIfDoesntExist() throws RepositoryException {
        HippoUtils sut = new HippoUtils();
        Node parent = mock(Node.class);
        Node node = mock(Node.class);
        when(parent.hasNode("name")).thenReturn(false);
        when(parent.addNode("name", "primType")).thenReturn(node);

        // ACT
        Node actual = sut.ensureNode(parent, "name", "primType", "anothertype");

        // ASSERT
        assertSame(actual, node);
    }

    @Test
    public void ensureRemovedRemovesIfPresent() throws Exception {
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Node child = mock(Node.class);
        when(node.hasNode("child")).thenReturn(true);
        when(node.getNode("child")).thenReturn(child);
        sut.ensureRemoved(node, "child");
        verify(child).remove();
    }

    @Test
    public void ensureRemovedIgnoresIfNotPresent() throws Exception {
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Node child = mock(Node.class);
        when(node.hasNode("child")).thenReturn(false);
        sut.ensureRemoved(node, "child");
        verify(child, never()).remove();
    }

    @Test
    public void removerChildrenRemovesAllChildren() throws RepositoryException {
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Node child = mock(Node.class);
        when(node.getNodes()).thenReturn(iterator(Collections.singletonList(child)));
        sut.removeChildren(node);
        verify(child).remove();
    }

    @Test
    public void findOneReturnsNullIfNoResults() throws RepositoryException {
        HippoUtils sut = new HippoUtils();
        Session session = session(Collections.emptyList());
        Node actual = sut.findOne(session, "SEELCT * FROM blah WHERE arg = '{}'", "arg1");
        Assert.assertNull(actual);
    }

    @Test
    public void findOneReturnsSingleResult() throws RepositoryException {
        HippoUtils sut = new HippoUtils();
        Node node = mock(Node.class);
        Session session = session(Collections.singletonList(node));
        Node actual = sut.findOne(session, "SEELCT * FROM blah WHERE arg = '{}'", "arg1");
        assertSame(actual, node);
    }

    @Test
    public void mostRecentDraftReturnExpectedNode() throws Exception {
        // ARRANGE
        Node handle = mock(Node.class);
        Node one = mock(Node.class);
        Node two = mock(Node.class);
        List<Node> nodes = asList(one, two);
        when(handle.getNodes()).thenReturn(iterator(nodes));
        HippoUtils sut = new HippoUtils();

        // ACT
        Node actual = sut.mostRecentDraft(handle);

        // ASSERT
        assertSame(two, actual);
    }

    public static Session session(List<Node> nodes) throws RepositoryException {
        Session session = mock(Session.class);
        Workspace ws = mock(Workspace.class);
        QueryManager qm = mock(QueryManager.class);
        Query q = mock(Query.class);
        QueryResult qr = mock(QueryResult.class);
        when(session.getWorkspace()).thenReturn(ws);
        when(ws.getQueryManager()).thenReturn(qm);
        when(qm.createQuery(any(), any())).thenReturn(q);
        when(q.execute()).thenReturn(qr);
        when(qr.getNodes()).thenReturn(HippoUtilsTest.iterator(nodes));
        return session;
    }

    public static NodeIterator iterator(Collection<Node> nodes) {

        Iterator<Node> it = nodes.iterator();

        return new NodeIterator() {

            public boolean hasNext() {
                return  it.hasNext();
            }

            public Node nextNode() {
                return (Node) it.next();
            }

            public void skip(long skipNum) {
                throw new NotImplementedException();
            }

            public long getSize() {
                return nodes.size();
            }

            public long getPosition() {
                throw new NotImplementedException();
            }

            public NodeIterator next() {
                throw new NotImplementedException();
            }
        };
    }
}
