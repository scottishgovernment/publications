package scot.gov.publications.rest;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MaintenanceResourceTest {

    @Test
    public void getOrpahsReturnsExectedItems() throws Exception {
        // ARRANGE
        MaintenanceResource sut = new MaintenanceResource();
        sut.storage = storageWithKeys("one", "two", "three");
        sut.repository = repoWithChecksums("one");

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "two", "three");

         // ACT
        Set<String> actual = sut.getOrphans();

        // ASSERT
        assertEquals(actual, expected);
    }

    @Test(expected = WebApplicationException.class)
    public void getOrphansWrapsStorageException() throws Exception {
        // ARRANGE
        MaintenanceResource sut = new MaintenanceResource();
        sut.storage = exceptionThrowingStorage();
        sut.repository = repoWithChecksums("one");

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "two", "three");

        // ACT
        Set<String> actual = sut.getOrphans();

        // ASSERT -- see expected
    }

    @Test(expected = WebApplicationException.class)
    public void getOrphansWrapsRepoException() throws Exception {
        // ARRANGE
        MaintenanceResource sut = new MaintenanceResource();
        sut.storage = storageWithKeys("one", "two", "three");
        sut.repository = exceptionThrowingRepo();

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "two", "three");

        // ACT
        Set<String> actual = sut.getOrphans();

        // ASSERT -- see expected
    }

    @Test
    public void doDeleteOrphansDeletesExpectedKeys() throws Exception {
        // ARRANGE
        MaintenanceResource sut = new MaintenanceResource();
        sut.status = new MaintenanceStatus();
        sut.storage = storageWithKeys("one", "two", "three");
        sut.repository = repoWithChecksums("one");

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "two", "three");

        // ACT
        sut.doDeleteOrphans();

        // ASSERT
        verify(sut.storage).deleteKeys(expected);
    }

    @Test
    public void doDeleteUpdatesStatusOnException() throws Exception {
        // ARRANGE
        MaintenanceResource sut = new MaintenanceResource();
        sut.status = new MaintenanceStatus();
        sut.storage = storageWithKeys("one", "two", "three");
        sut.repository = repoWithChecksums("one");
        when(sut.storage.deleteKeys(any())).thenThrow(new PublicationStorageException(new RuntimeException("")));

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "two", "three");

        // ACT
        sut.doDeleteOrphans();

        // ASSERT
        assertEquals(sut.status.getData(), emptyMap());
    }

    PublicationStorage storageWithKeys(String ...keys) throws PublicationStorageException {
        PublicationStorage storage = mock(PublicationStorage.class);
        Set<String> keySet = new HashSet<>(Arrays.asList(keys));
        when(storage.listKeys()).thenReturn(keySet);
        return storage;
    }

    PublicationRepository repoWithChecksums(String ...keys) throws PublicationRepositoryException {
        PublicationRepository repository = mock(PublicationRepository.class);
        Set<String> keySet = new HashSet<>(Arrays.asList(keys));
        when(repository.allChecksums()).thenReturn(keySet);
        return repository;
    }

    PublicationRepository exceptionThrowingRepo() throws PublicationRepositoryException {
        PublicationRepository repository = mock(PublicationRepository.class);
        when(repository.allChecksums()).thenThrow(new PublicationRepositoryException("", new RuntimeException("")));
        return repository;
    }

    PublicationStorage exceptionThrowingStorage() throws PublicationStorageException {
        PublicationStorage storage = mock(PublicationStorage.class);
        when(storage.listKeys()).thenThrow(new PublicationStorageException(new RuntimeException("")));
        return storage;
    }

//
//    @GET
//    @Produces({ MediaType.APPLICATION_JSON })
//    @Path("orphans")
//    public Set<String> getOrphans() {
//
//        try {
//            Set<String> inStorage = storage.listKeys();
//            Set<String> inRepo = repository.allChecksums();
//            inStorage.removeAll(inRepo);
//            return inStorage;
//        } catch (PublicationStorageException e) {
//            LOG.error("Failed to get keys from storage");
//            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
//        } catch (PublicationRepositoryException e) {
//            LOG.error("Failed to get keys from repository");
//            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
//        }
//    }

}
