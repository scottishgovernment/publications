package scot.gov.publications.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Collections.emptyMap;

/**
 * Maintenance endpoints.  Currently allows the identification and delettions of 'orphans', files that are in s3 but
 * are not refered to in the database.
 */
@Path("maintenance")
public class MaintenanceResource {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceResource.class);

    @Inject
    MaintenanceStatus status;

    @Inject
    PublicationRepository repository;

    @Inject
    PublicationStorage storage;

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("orphans")
    public Set<String> getOrphans() {
        try {
            Set<String> inStorage = storage.listKeys();
            Set<String> inRepo = repository.allChecksums();
            inStorage.removeAll(inRepo);
            return inStorage;
        } catch (PublicationStorageException e) {
            LOG.error("Failed to get keys from storage");
            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
        } catch (PublicationRepositoryException e) {
            LOG.error("Failed to get keys from repository");
            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
        }
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("orphans")
    public MaintenanceStatus deleteOrphans() {
        status.assertAvailable();
        status.startMaintainance();
        executorService.submit(this::doDeleteOrphans);
        return status;
    }

    void doDeleteOrphans() {
        Set<String> orphans = getOrphans();
        try {
            Map<String, String> results = storage.deleteKeys(orphans);
            status.endMaintainance(results);
        } catch (PublicationStorageException e) {
            status.endMaintainance(emptyMap());
            LOG.error("Failed to delete orphans", e);
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public MaintenanceStatus getStatus() {
        return status;
    }

}
