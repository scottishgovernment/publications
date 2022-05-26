package scot.gov.publications.rest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.hippo.HippoPaths;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Healthcheck endpoint for publications api.
 *
 * Checks that it can connect to the sql database, JCR repo and the 3s bucket used to upload publications to.
 *
 * In additions it also checks if jobs have been waitinbg longer than 5 minutes or that the totel number of waiting
 * jobs does not rise above 5.
 */
@Path("health")
public class HealthCheckResource {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckResource.class);

    // The maximum wait time considered acceptable by the healthcheck (5 minutes)
    public static final long WAIT_THRESHOLD = 1000 * 60 * 5;

    private static String WAIT_THRESHOLD_STRING = DurationFormatUtils.formatDurationHMS(WAIT_THRESHOLD);

    @Inject
    PublicationRepository publicationRepository;

    @Inject
    SessionFactory sessionFactory;

    @Inject
    PublicationStorage publicationStorage;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response health() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode result = factory.objectNode();
        ArrayNode errors = factory.arrayNode();
        addRepositoryInfo(errors);
        addJCRInfo(errors);
        addStorageInfo(errors);

        boolean ok = errors.size() == 0;
        result.put("ok", ok);

        if (!ok) {
            result.set("errors", errors);
        }

        int status = ok ? 200 : 503;
        return Response.status(status)
                .entity(result)
                .build();
    }

    /**
     * Check that we can query the publications repo and that publications are not waiting too long to be processed.
     */
    private void addRepositoryInfo(ArrayNode errors) {
        try {
            Collection<Publication> waitingPublications = publicationRepository.waitingPublications();

            // has any publication has been waiting for more than 5 minutes?
            long waitingTooLong = waitingPublications.stream().filter(this::hasBeenWaitingTooLong).count();
            if (waitingTooLong > 0) {
                errors.add(String.format("%d publications waiting for more than %s", waitingTooLong, WAIT_THRESHOLD_STRING));
            }

            // are more then 5 publications waiting?
            if (waitingPublications.size() > 5) {
                errors.add(String.format("%d publications are waiting", waitingPublications.size()));
            }
        } catch (PublicationRepositoryException e) {
            LOG.error("Failed to add repository info to healthcheck", e);
            errors.add("Failed to query publications database: " + e.getCause().getMessage());
        }
    }

    /**
     * Check that we can connect to the jcr repository and can see the root node.  Specify a timeout period so that
     * the healthcheck will return in a timely way even if the repo is not responding
     */
    private void addJCRInfo(ArrayNode errors) {
        FutureTask<Void> future = new FutureTask<>(() -> addJCRInfoBlocking(errors));
        Executors.newSingleThreadExecutor().execute(future);
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException| TimeoutException e) {
            LOG.error("Timeout trying to contact JCR repository : " + e.getMessage(), e);
            errors.add("Timeout trying to contact JCR repository:" + e.getMessage());
        }
    }

    private Void addJCRInfoBlocking(ArrayNode errors) {
        try {
            Session session = sessionFactory.newSession();
            session.itemExists(HippoPaths.ROOT);
            session.logout();
        } catch (RepositoryException | RemoteRuntimeException e) {
            LOG.error("Failed to contact JCR repository : " + e.getMessage(), e);
            errors.add("Unable to contact the JCR repository:" + e.getMessage());
        }
        return null;
    }

    private void addStorageInfo(ArrayNode errors) {
        try {
            publicationStorage.ok();
        } catch (PublicationStorageException e) {
            LOG.error("Publication storage is unhealthy: {}", e);
            errors.add("Publication storage is unhealthy:" + e.getMessage());
        }
    }

    private boolean hasBeenWaitingTooLong(Publication publication) {
        long waitTime = publication.getLastmodifieddate().getTime() - publication.getCreateddate().getTime();
        return waitTime > WAIT_THRESHOLD;
    }
}
