package scot.gov.publications.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class PublicationRepositoryTest {

    PublicationRepository sut;

    @Before
    public void setup() {
        sut = new PublicationRepository();
        JdbcConnectionPool connectionPool = JdbcConnectionPool.create("jdbc:h2:mem:testing", "user", "password");
        Flyway.configure().dataSource(connectionPool).load().migrate();
        sut.queryRunner = new QueryRunner(connectionPool);
        sut.timestampSource = mock(TimestampSource.class);
        when(sut.timestampSource.now()).thenReturn(new Timestamp(1), new Timestamp(2), new Timestamp(3), new Timestamp(4), new Timestamp(5));
    }

    @After
    public void teardown() throws Exception {
        sut.queryRunner.update("TRUNCATE TABLE publication");
    }

    @Test
    public void canCreateAndFetchPublication() throws Exception {
        // ARRANGE
        Publication in = examplePublication();

        // ACT
        sut.create(in);
        Publication out = sut.get(in.getId());

        // ASSERT
        assertEquals(in.getId(), out.getId());
        assertEquals(in.getTitle(), out.getTitle());
        assertEquals(in.getIsbn(), out.getIsbn());
        assertEquals(in.getState(), out.getState());
        assertEquals(in.getEmbargodate(), out.getEmbargodate());
        assertEquals(in.getStatedetails(), out.getStatedetails());
        assertEquals(in.getStacktrace(), out.getStacktrace());
        assertEquals(in.getChecksum(), out.getChecksum());
        assertEquals(out.getCreateddate(), new Timestamp(1));
        assertEquals(out.getLastmodifieddate(), new Timestamp(1));
    }

    @Test(expected = PublicationRepositoryException.class)
    public void createExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        sut.queryRunner = exceptionThrowingQueryRunner();

        // ACT
        sut.create(examplePublication());

        // ASSERT - see expected
    }

    @Test
    public void canUpdateAndFetchPublication() throws Exception {
        // ARRANGE
        Publication in = examplePublication();

        // ACT
        sut.create(in);
        in.setState("newstate");
        in.setChecksum("newchecksum");
        sut.update(in);
        Publication out = sut.get(in.getId());

        // ASSERT
        assertEquals(in.getState(), "newstate");
        assertEquals(in.getChecksum(), "newchecksum");
    }

    @Test(expected = PublicationRepositoryException.class)
    public void updateExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        sut.queryRunner = exceptionThrowingQueryRunner();

        // ACT
        sut.update(examplePublication());

        // ASSERT - see expected
    }

    @Test
    public void getReturnsNullIfIdDoesNotExist() throws Exception {
        // ARRANGE

        // ACT
        Publication actual = sut.get("nosuchid");

        // ASSERT
        assertNull(actual);
    }

    @Test(expected = PublicationRepositoryException.class)
    public void getExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        sut.queryRunner = exceptionThrowingQueryRunner();

        // ACT
        sut.get("id");

        // ASSERT - see expected
    }

    @Test
    public void listReturnsRightEntriesForPage() throws Exception {
        // ARRANGE
        createPublications(50, "one");

        // ACT
        ListResult actual = sut.list(1, 10, "");

        // ASSERT
        assertEquals(actual.getPage(), 1);
        assertEquals(actual.getPageSize(), 10);
        assertEquals(actual.getTotalSize(), 50);
    }

    @Test
    public void listReturnsRightEntriesForPageWithFilter() throws Exception {
        // ARRANGE
        for (int i = 0; i < 50; i++) {
            sut.create(examplePublication("one"));
        }
        for (int i = 0; i < 50; i++) {
            sut.create(examplePublication("two"));
        }

        // ACT
        ListResult actual = sut.list(1, 10, "one");

        // ASSERT
        assertEquals(actual.getPage(), 1);
        assertEquals(actual.getPageSize(), 10);
        assertEquals(actual.getTotalSize(), 50);
    }

    @Test
    public void listReturnsEmptyResultsIfNoMathcingEntries() throws Exception {
        // ARRANGE
        createPublications(50, "one");
        createPublications(50, "two");

        // ACT
        ListResult actual = sut.list(1, 10, "three");

        // ASSERT
        assertEquals(actual.getPage(), 1);
        assertEquals(actual.getPageSize(), 10);
        assertEquals(actual.getTotalSize(), 0);
    }

    @Test
    public void listReturnsEmptyResultsIfNoPublications() throws Exception {
        // ARRANGE

        // ACT
        ListResult actual = sut.list(1, 10, "");

        // ASSERT
        assertEquals(actual.getPage(), 1);
        assertEquals(actual.getPageSize(), 10);
        assertEquals(actual.getTotalSize(), 0);
    }

    @Test
    public void waitingReturnsExceptedPublivaitons() throws Exception {
        // ARRANGE
        createPublications(50, "one", State.PENDING);
        createPublications(50, "one", State.PROCESSING);
        createPublications(50, "one", State.DONE);
        createPublications(50, "one", State.FAILED);

        // ACT
        Collection<Publication> actual = sut.waitingPublications();

        // ASSERT
        assertEquals(actual.size(), 100);
    }

    @Test(expected = PublicationRepositoryException.class)
    public void waitingExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        sut.queryRunner = exceptionThrowingQueryRunner();

        // ACT
        sut.waitingPublications();

        // ASSERT - see expected
    }

    //, State.PENDING
    private void createPublications(int count, String prefix) throws Exception {
        createPublications(count, prefix, State.PENDING);
    }

    private void createPublications(int count, String prefix, State state) throws Exception {
        for (int i = 0; i < 50; i++) {
            Publication publication = examplePublication(prefix);
            publication.setState(state.name());
            sut.create(publication);
        }
    }

    Publication examplePublication() {
        return examplePublication("");
    }

    Publication examplePublication(String prefix) {
        Publication publication = new Publication();
        publication.setId(UUID.randomUUID().toString());
        publication.setTitle(prefix + "title");
        publication.setIsbn(prefix + "isbn");
        publication.setState(State.PENDING.name());
        publication.setEmbargodate(Timestamp.from(Instant.now()));
        publication.setStacktrace(prefix + "stacktrace");
        publication.setStatedetails(prefix + "statedetails");
        publication.setChecksum(prefix + "checksum");
        return publication;
    }

    QueryRunner exceptionThrowingQueryRunner() throws Exception {
        QueryRunner queryRunner = Mockito.mock(QueryRunner.class);
        when(queryRunner.update(anyString(), any())).thenThrow(new SQLException(""));
        when(queryRunner.query(anyString(), any())).thenThrow(new SQLException(""));
        when(queryRunner.query(anyString(), any(new BeanHandler<>(Publication.class).getClass()), any(Object.class))).thenThrow(new SQLException(""));
        return queryRunner;
    }
}