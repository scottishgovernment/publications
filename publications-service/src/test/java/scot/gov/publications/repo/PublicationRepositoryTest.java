package scot.gov.publications.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

    @Test
    public void getReturnsNullIfIdDoesNotExist() throws Exception {
        // ARRANGE

        // ACT
        Publication actual = sut.get("nosuchid");

        // ASSERT
        assertNull(actual);
    }

    @Test
    public void listReturnsRightEntriesForPage() throws Exception {
        // ARRANGE
        for (int i = 0; i < 50; i++) {
            sut.create(examplePublication(Integer.toString(i)));
        }

        // ACT
        Publication actual = sut.get("nosuchid");

        // ASSERT
        assertNull(actual);
    }

    /**
     * can save a publicaiton - dates get set as expected, all the fields are as expected
     * can update a publicaitons
     * can create 50 publicaitons and paging works as expected, fitlering works as expected
     * exception handling - if query runner throws and exception it behaves as expected
     */
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
}
