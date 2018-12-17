package scot.gov.publications.hippo;

import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.metadata.Metadata;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PublicationPathStrategyTest {

    @Test
    public void returnsExpectedPathIfDoesNotAlreadyExist() throws Exception {

        // ARRANGE
        PublicationPathStrategy sut = new PublicationPathStrategy(null);
        sut.session = sessionWithEmptyResult();
        sut.hippoPaths = new HippoPaths(null);
        Metadata input = new Metadata();
        input.setPublicationDate(LocalDateTime.of(2010, 01, 02, 12, 30));
        input.setPublicationType("Report");
        input.setTitle("Publication title");
        List<String> expected = new ArrayList<>();
        Collections.addAll(expected, "Publications", "Report", "2010", "01", "Publication title");

        // ACT
        List<String> actual = sut.path(input);


        // ASSERT
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void returnsExpectedPathForSingleClash() throws Exception {

        // ARRANGE
        PublicationPathStrategy sut = new PublicationPathStrategy(null);
        sut.session = sessionWithOneClash();
        sut.hippoPaths = new HippoPaths(null);
        Metadata input = new Metadata();
        input.setPublicationDate(LocalDateTime.of(2010, 01, 02, 12, 30));
        input.setPublicationType("Report");
        input.setTitle("Publication title");
        List<String> expected = new ArrayList<>();
        Collections.addAll(expected, "Publications", "Report", "2010", "01", "Publication title 2");

        // ACT
        List<String> actual = sut.path(input);


        // ASSERT
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void returnsExpectedPathForDoubleClash() throws Exception {

        // ARRANGE
        PublicationPathStrategy sut = new PublicationPathStrategy(null);
        sut.session = sessionWithTwoClashes();
        sut.hippoPaths = new HippoPaths(null);
        Metadata input = new Metadata();
        input.setPublicationDate(LocalDateTime.of(2010, 01, 02, 12, 30));
        input.setPublicationType("Report");
        input.setTitle("Publication title");
        List<String> expected = new ArrayList<>();
        Collections.addAll(expected, "Publications", "Report", "2010", "01", "Publication title 3");

        // ACT
        List<String> actual = sut.path(input);


        // ASSERT
        Assert.assertEquals(expected, actual);
    }

    Session sessionWithEmptyResult() throws RepositoryException {
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        when(workspace.getQueryManager()).thenReturn(queryManager);
        Query query = mock(Query.class);
        QueryResult result = emptyResult();
        when(query.execute()).thenReturn(result);
        when(queryManager.createQuery(any(), any())).thenReturn(query);
        when(session.getWorkspace()).thenReturn(workspace);
        return session;
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
        NodeIterator iterator = HippoUtilsTest.iterator(Collections.singletonList(node));
        when(queryResult.getNodes()).thenReturn(iterator);
        return queryResult;
    }
}


//    private boolean titleAlreadyExists(String title) throws RepositoryException {
//        String xpath = String.format(XPATH_TEMPLATE, title);
//        Query query = session.getWorkspace().getQueryManager().createQuery(xpath, Query.XPATH);
//        QueryResult result = query.execute();
//        boolean exists = result.getRows().getSize() != 0;
//        if (exists) {
//            LOG.info("The title \"{}\" is already used by another publication", title);
//        }


//
//    LocalDate pubDate = metadata.getPublicationDate().toLocalDate();
//    String yearString = Integer.toString(pubDate.getYear());
//    String monthString = String.format("%02d", pubDate.getMonthValue());
//    String sanitizedTitle = Sanitiser.sanitise(metadata.getTitle());
//    String disambiguatedTitle = disambiguatedTitle(sanitizedTitle);
//
//return asList(
//        "Publications",
//        defaultIfBlank(metadata.getPublicationType(), "Publication"),
//        yearString,
//        monthString,
//        disambiguatedTitle);