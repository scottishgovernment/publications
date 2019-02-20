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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PublicationPathStrategyTest {

    @Test
    public void returnsExpectedPath() throws Exception {

        // ARRANGE
        PublicationPathStrategy sut = new PublicationPathStrategy(null);
        sut.hippoPaths = new HippoPaths(null);
        sut.slugAllocationStrategy = mock(SlugAllocationStrategy.class);
        when(sut.slugAllocationStrategy.allocate(anyString())).thenReturn("slug");
        Metadata input = new Metadata();
        input.setPublicationDate(LocalDateTime.of(2010, 01, 02, 12, 30));
        input.setPublicationType("Report");
        input.setTitle("Publication title");
        List<String> expected = new ArrayList<>();
        Collections.addAll(expected, "Publications", "Publication", "2010", "01", "slug");

        // ACT
        List<String> actual = sut.path(input);


        // ASSERT
        Assert.assertEquals(expected, actual);
    }

    @Test(expected = RepositoryException.class)
    public void expcetionThrowsIFSlugAllocationFails() throws Exception {

        // ARRANGE
        PublicationPathStrategy sut = new PublicationPathStrategy(null);
        sut.hippoPaths = new HippoPaths(null);
        sut.slugAllocationStrategy = mock(SlugAllocationStrategy.class);
        when(sut.slugAllocationStrategy.allocate(anyString())).thenThrow(new RepositoryException());
        Metadata input = new Metadata();
        input.setPublicationDate(LocalDateTime.of(2010, 01, 02, 12, 30));
        input.setPublicationType("Report");
        input.setTitle("Publication title");

        // ACT
        sut.path(input);


        // ASSERT - expected exception
    }
}