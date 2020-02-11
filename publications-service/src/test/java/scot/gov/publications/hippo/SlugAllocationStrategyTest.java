package scot.gov.publications.hippo;

import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import static java.util.Collections.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SlugAllocationStrategyTest {

    @Test
    public void canAllocateSlugWithNoClash() throws RepositoryException {

        // ARRANGE
        SlugAllocationStrategy sut = new SlugAllocationStrategy(null);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.findFirst(any(), any(), any())).thenReturn(null);

        // ACT
        String actual = sut.allocate("MY Publication title");

        // ASSERT
        // my is a stopword ... spaces are replaces with hyphens
        assertEquals("publication-title", actual);
    }

    @Test
    public void canAllocateSlugWithOneClash() throws RepositoryException {

        // ARRANGE
        SlugAllocationStrategy sut = new SlugAllocationStrategy(sessionWithOneClash());

        // ACT
        String actual = sut.allocate("MY Publication title");

        // ASSERT
        // my is a stopword ... spaces are replaces with hyphens
        assertEquals("publication-title-2", actual);
    }

    @Test
    public void canAllocateSlugWithTwoClashes() throws RepositoryException {

        // ARRANGE
        SlugAllocationStrategy sut = new SlugAllocationStrategy(sessionWithTwoClashes());

        // ACT
        String actual = sut.allocate("MY Publication title");

        // ASSERT
        // my is a stopword ... spaces are replaces with hyphens
        assertEquals("publication-title-3", actual);
    }

    @Test(expected = RepositoryException.class)
    public void throwsRepoExceptionIfCantTalkToRepo() throws RepositoryException {

        // ARRANGE
        SlugAllocationStrategy sut = new SlugAllocationStrategy(null);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.findFirst(any(), any(), any())).thenThrow(new RepositoryException());

        // ACT
        String actual = sut.allocate("MY Publication title");

        // ASSERT - expect exception
    }

    Session sessionWithOneClash() throws RepositoryException {
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        when(workspace.getQueryManager()).thenReturn(queryManager);

        Query query1 = mock(Query.class);
        QueryResult result1 = singleResult();
        when(query1.execute()).thenReturn(result1);

        Query query2 = mock(Query.class);
        QueryResult result2 = emptyResult();
        when(query2.execute()).thenReturn(result2);

        when(queryManager.createQuery(any(), any())).thenReturn(query1).thenReturn(query2);
        when(session.getWorkspace()).thenReturn(workspace);
        return session;
    }

    Session sessionWithTwoClashes() throws RepositoryException {
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        when(workspace.getQueryManager()).thenReturn(queryManager);

        Query query1 = mock(Query.class);
        QueryResult result1 = singleResult();
        when(query1.execute()).thenReturn(result1);

        Query query2 = mock(Query.class);
        QueryResult result2 = singleResult();
        when(query2.execute()).thenReturn(result2);

        Query query3 = mock(Query.class);
        QueryResult result3 = emptyResult();
        when(query3.execute()).thenReturn(result3);

        when(queryManager.createQuery(any(), any())).thenReturn(query1).thenReturn(query2).thenReturn(query3);
        when(session.getWorkspace()).thenReturn(workspace);
        return session;
    }

    QueryResult emptyResult() throws RepositoryException {
        QueryResult queryResult = mock(QueryResult.class);
        NodeIterator iterator = mock(NodeIterator.class);
        when(queryResult.getNodes()).thenReturn(iterator);
        return queryResult;
    }

    QueryResult singleResult() throws RepositoryException {
        QueryResult queryResult = mock(QueryResult.class);
        Node node = mock(Node.class);
        NodeIterator iterator = HippoUtilsTest.iterator(singletonList(node));
        when(queryResult.getNodes()).thenReturn(iterator);
        return queryResult;
    }

}
