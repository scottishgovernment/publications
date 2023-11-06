package scot.gov.publications.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.hippo.HippoPaths;
import scot.gov.publications.hippo.HippoSessionFactory;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import jakarta.ws.rs.core.Response;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class HealthCheckResourceTest {

    @Test
    public void healthlyReturnsAsExpeted() throws Exception {

        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = healthyPublicationRepository();
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = healthySessionFactory();

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 200);
        assertTrue(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfPublicationInQueueTooLong() throws Exception {
        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = publicationRepositoryWithItems(Collections.singleton(pendingALongTimePublication()));
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = healthySessionFactory();

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfTooManyWaitingPublicationsInQueue() throws Exception {
        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        Collection<Publication> publications = new ArrayList<>();
        Collections.addAll(publications, anyPublication(), anyPublication(), anyPublication(), anyPublication(), anyPublication(), anyPublication());
        sut.publicationRepository = publicationRepositoryWithItems(publications);
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = healthySessionFactory();

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfRepositorythrowsException() throws Exception {

        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = exceptionThrowingPublicationRepository();
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = healthySessionFactory();

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfJCRRepositorythrowsRepositoryException() throws Exception {

        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = healthyPublicationRepository();
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = unhealthSessionFactory(RepositoryException.class);

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfJCRRepositorythrowsRemoteRuntimeException() throws Exception {

        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = healthyPublicationRepository();
        sut.publicationStorage = healthlyPublicationStorage();
        sut.sessionFactory = unhealthSessionFactory(RemoteRuntimeException.class);

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    @Test
    public void errorAddedIfStorageThrowsException() throws Exception {

        // ARRANGE
        HealthCheckResource sut = new HealthCheckResource();
        sut.publicationRepository = healthyPublicationRepository();
        sut.publicationStorage = unhealthlyPublicationStorage();
        sut.sessionFactory = healthySessionFactory();

        // ACT
        Response actual = sut.health();
        ObjectNode actualEntity = (ObjectNode) actual.getEntity();

        // ASSERT
        assertEquals(actual.getStatus(), 503);
        assertFalse(actualEntity.get("ok").asBoolean());
    }

    PublicationRepository healthyPublicationRepository() throws Exception {
        PublicationRepository repository = mock(PublicationRepository.class);
        Mockito.when(repository.waitingPublications()).thenReturn(Collections.singleton(anyPublication()));
        return repository;
    }

    PublicationRepository exceptionThrowingPublicationRepository() throws Exception {
        PublicationRepository repository = mock(PublicationRepository.class);
        Mockito.when(repository.waitingPublications()).thenThrow(new PublicationRepositoryException("arg", new RuntimeException("cause")));
        return repository;
    }

    PublicationRepository publicationRepositoryWithItems(Collection<Publication> publications) throws Exception {
        PublicationRepository repository = mock(PublicationRepository.class);
        Mockito.when(repository.waitingPublications()).thenReturn(publications);
        return repository;
    }

    Publication anyPublication() {
        Publication publication = new Publication();
        publication.setChecksum("checksun");
        publication.setId("id");
        publication.setTitle("title");
        publication.setIsbn("isbn");
        publication.setEmbargodate(new Timestamp(Instant.now().toEpochMilli()));
        publication.setState(State.DONE.name());
        publication.setStatedetails("");
        publication.setChecksum("checksum");
        publication.setCreateddate(new Timestamp(Instant.now().toEpochMilli()));
        publication.setLastmodifieddate(new Timestamp(Instant.now().toEpochMilli()));
        return publication;
    }

    Publication pendingALongTimePublication() {
        Publication publication = anyPublication();
        publication.setState(State.PENDING.name());
        publication.setCreateddate(new Timestamp(Instant.now().minusSeconds(HealthCheckResource.WAIT_THRESHOLD + 1).toEpochMilli()));
        publication.setLastmodifieddate(new Timestamp(Instant.now().toEpochMilli()));
        return publication;
    }

    PublicationStorage healthlyPublicationStorage() {
        PublicationStorage storage = mock(PublicationStorage.class);
        return storage;
    }

    PublicationStorage unhealthlyPublicationStorage() throws Exception {
        PublicationStorage storage = mock(PublicationStorage.class);
        Mockito.when(storage.ok()).thenThrow(new PublicationStorageException(new RuntimeException("arg")));
        return storage;
    }

    HippoSessionFactory healthySessionFactory() throws Exception {
        HippoSessionFactory sessionFactory = mock(HippoSessionFactory.class);
        Session session = mock(Session.class);
        Mockito.when(session.nodeExists(HippoPaths.ROOT)).thenReturn(true);
        Mockito.when(sessionFactory.newSession()).thenReturn(session);
        return sessionFactory;
    }

    HippoSessionFactory unhealthSessionFactory(Class throwableclass) throws Exception {
        HippoSessionFactory sessionFactory = mock(HippoSessionFactory.class);
        Session session = mock(Session.class);
        Mockito.when(session.itemExists(HippoPaths.ROOT)).thenThrow(throwableclass);
        Mockito.when(sessionFactory.newSession()).thenReturn(session);
        return sessionFactory;
    }

}
