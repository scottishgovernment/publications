package scot.gov.publications.rest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.util.Collection;

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
    PublicationStorage publicationStorage;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response health() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode result = factory.objectNode();
        ArrayNode errors = factory.arrayNode();
        addRepositoryInfo(errors);
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
