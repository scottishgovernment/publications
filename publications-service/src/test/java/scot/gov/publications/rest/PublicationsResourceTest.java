package scot.gov.publications.rest;

import org.junit.Test;
import scot.gov.publications.repo.ListResult;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PublicationsResourceTest {

    @Test
    public void listReturnsResutlsFromRepo() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        ListResult result = new ListResult();
        when(sut.repository.list(1, 10, "queryString", "", "")).thenReturn(result);

        // ACT
        Response actual = sut.list(1, 10, "queryString", "", "");

        // ASSERT
        verify(sut.repository).list(1, 10, "queryString", "", "");
        assertSame(actual.getEntity(), result);
    }

    @Test(expected = WebApplicationException.class)
    public void listWrapsRepoException() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.list(1, 10, "queryString", "", ""))
                .thenThrow(new PublicationRepositoryException("arg", new RuntimeException("arg")));

        // ACT
        try (Response actual = sut.list(1, 10, "queryString", "", "")) {
            fail("Expected exception");
        }

        // ASSERT - see exception
    }

    @Test
    public void getReturnsResultFromRepo() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        Publication publication = new Publication();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenReturn(publication);

        // ACT
        Response reponse = sut.get("id");
        Publication actual = (Publication) reponse.getEntity();

        // ASSERT
        assertSame(actual, publication);
    }

    @Test
    public void getReturns500ForException() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenThrow(new PublicationRepositoryException("arg", new RuntimeException("arg")));

        // ACT
        Response actual = sut.get("id");

        // ASSERT
        assertEquals(500, actual.getStatus());
    }

    @Test
    public void getReturns404IfPubNotFound() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenReturn(null);

        // ACT
        Response actual = sut.get("id");

        // ASSERT
        assertEquals(404, actual.getStatus());
    }

}
