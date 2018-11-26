package scot.gov.publications.repo;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class QueryLoaderTest {

    QueryLoader sut = new QueryLoader();

    @Test
    public void canLoadKnownQueries() {
        assertNotNull(sut.loadSQL("/sql/insert.sql"));
        assertNotNull(sut.loadSQL("/sql/update.sql"));
        assertNotNull(sut.loadSQL("/sql/list.sql"));
        assertNotNull(sut.loadSQL("/sql/waiting.sql"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsIllegalArgumentExceptionForUnknownException() {
        sut.loadSQL("/sql/unknown.sql");
    }
}
