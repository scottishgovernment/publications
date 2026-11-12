package scot.gov.publications.rest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.repo.ListResult;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;

import javax.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.Path;

import java.io.InputStream;

/**
 * REST service that allows tracking of publications imports and downloading of previously uploaded zip files.
 **/
@Path("publications")
public class PublicationsResource {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsResource.class);

    @Inject
    PublicationStorage storage;

    @Inject
    PublicationRepository repository;

    /**
     * Get a paged list of publications with an optional queryString that can be used to perform a partial and case insensitive
     * match against the list of publications.
     *
     * @param page the page to fetch, 1 based
     * @param size the number of items to fetch
     * @param title title to match
     * @param isbn isbn to match
     * @param isbn filename to match
     * @return A paged list of publications.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response list(
            @DefaultValue("1") @QueryParam("page") int page,
            @DefaultValue("10") @QueryParam("size") int size,
            @DefaultValue("") @QueryParam("title") String title,
            @DefaultValue("") @QueryParam("isbn") String isbn,
            @DefaultValue("") @QueryParam("filename") String filename) {
        try {
            ListResult result = repository.list(page, size, title, isbn, filename);
            return Response.ok(result).build();
        } catch (PublicationRepositoryException e) {
            throw new WebApplicationException(e, Response.status(500).build());
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_OCTET_STREAM})
    @Path("{id}/download")
    @PathParam("id")
    public Response download(@PathParam("id") String id) {
        try {
            Publication publication = repository.get(id);
            InputStream is = storage.get(publication);
            StreamingOutput streamingOutput= os -> IOUtils.copy(is, os);
            return Response.ok(streamingOutput)
                    .header("Content-Disposition", "attachment; filename=\"" + publication.getFilename() + "\"" )
                    .build();
        } catch(PublicationRepositoryException | PublicationStorageException e) {
            LOG.error("Failed to download document ", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * The details for a publication for an id.
     *
     * @param id Id of the publication to fetch
     * @return the publications, will throw a web applicaiton exception if it is not found.
     */
    @GET
    @Path("{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(@PathParam("id") String id) {
        try {
            Publication publication = repository.get(id);
            if (publication == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(publication).build();
        } catch (PublicationRepositoryException e) {
            LOG.error("Failed to get publication {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Cancel a job by its id
     */
    @PUT
    @Path("{id}/cancel")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response cancel(@PathParam("id") String id, @HeaderParam("X-User") String username) {
        Publication publication = null;
        try {
            // get the publication details from the zip
            publication = repository.get(id);

            if (publication == null) {
                return Response.status(404).entity("Publication not found").build();
            }

            publication.setState(State.CANCELLED.name());
            repository.update(publication);
            return Response.ok().build();
        } catch (PublicationRepositoryException e) {
            String msg = "Failed to cancel publication";
            LOG.error(msg, e);
            return Response.status(500).entity("Failed to cancel publication").build();
        }
    }
}
