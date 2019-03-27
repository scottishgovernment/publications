package scot.gov.publications.rest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.ListResult;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.FileUtil;
import scot.gov.publications.util.ZipUtil;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST service that allow uploading APS zip files to be imported into Hippo.  It also allows tracking of
 * publications imports.
 **/
@Path("publications")
public class PublicationsResource {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsResource.class);

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    PublicationStorage storage;

    @Inject
    PublicationRepository repository;

    @Inject
    PublicationUploader publicationUploader;

    FileUtil fileUtil = new FileUtil();

    ZipUtil zipUtil = new ZipUtil();

    MetadataExtractor metadataExtractor = new MetadataExtractor();

    ExecutorService executor = Executors.newSingleThreadExecutor();

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

    /**
     * Upload a new zip file to be processed.
     *
     * @param fileUpload A multipart file upload containing a zip in the expected format.
     * @param username the user posting this content *(set by proxette)
     * @return Response indicating if the zip has been accepted.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_JSON })
    public Response postFormData(@MultipartForm UploadRequest fileUpload, @HeaderParam("X-User") String username) {

        // extract the zip file from the uploaded file
        File zipFile = null;
        File extractedZipFile = null;
        try {
            // assert that the upload contains data and a filename
            assertRequiredFields(fileUpload);

            // save the zip file to disk
            zipFile = fileUtil.createTempFile("zipUpload", "zip", new ByteArrayInputStream(fileUpload.getFileData()));

            // the up-loaded zip file contains a zip - extract it
            extractedZipFile = zipUtil.extractNestedZipFile(zipFile);
        } catch (IllegalArgumentException | IOException e) {
            // return a client error since we were not able to extract the zip file.  ensure that any temp files are deleted.
            LOG.error("Failed to extract zip", e);
            FileUtils.deleteQuietly(zipFile);
            FileUtils.deleteQuietly(extractedZipFile);
            return Response.status(400).entity(UploadResponse.error("Failed to extract zip file")).build();
        }

        Publication publication = null;
        try {
            // get the publication details from the zip
            publication = newPublication(zipFile, extractedZipFile, username, fileUpload.getFilename());

            // upload the file to s3
            storage.save(publication, zipFile);

            // save the details in the repository
            repository.create(publication);

            // submit the publication to the processing queue and return accepted status code
            importPublication(publication);
            return Response.accepted(UploadResponse.accepted(publication)).build();
        } catch (ApsZipImporterException e) {
            LOG.error("Failed to extract metadata from zip",  e);
            return Response.status(400).entity(UploadResponse.error(e.getMessage())).build();
        } catch (PublicationStorageException e) {
            String msg = "Failed to upload zip file to s3";
            LOG.error(msg,  e);
            return Response.status(500).entity(UploadResponse.error(msg)).build();
        } catch (PublicationRepositoryException e) {
            String msg = "Failed to save publication to the repository";
            LOG.error(msg, e);
            return Response.status(500).entity(UploadResponse.error("Failed to save publication to the repository")).build();
        } finally {
            // ensure that temp files are deleted
            FileUtils.deleteQuietly(zipFile);
            FileUtils.deleteQuietly(extractedZipFile);
        }
    }

    private void assertRequiredFields(UploadRequest request) {
        if (request.getFileData() == null) {
            throw new IllegalArgumentException("Upload contains no file");
        }

        if (StringUtils.isBlank(request.getFilename())) {
            throw new IllegalArgumentException("Upload contains no filename");
        }
    }

    private void importPublication(Publication publication) {
        executor.submit(() -> publicationUploader.importPublication(publication));
    }

    private Publication newPublication(
            File zip,
            File extractedZip,
            String username,
            String filename)
                throws ApsZipImporterException {

        Metadata metadata = metadataExtractor.extract(extractedZip);
        Publication publication = new Publication();
        publication.setId(UUID.randomUUID().toString());
        publication.setUsername(username);
        publication.setIsbn(metadata.normalisedIsbn());
        publication.setFilename(filename);
        publication.setTitle(metadata.getTitle());
        publication.setState(State.PENDING.name());
        publication.setEmbargodate(Timestamp.from(metadata.getPublicationDateWithTimezone().toInstant()));
        publication.setContact(contactEmail(metadata));

        try {
            publication.setChecksum(fileUtil.hash(zip));
        } catch (IOException e) {
            throw new ApsZipImporterException("Unable to calculate checksum", e);
        }
        return publication;
    }

    /**
     * The contact email has not always been present in the JSON and so we guard against it being null.
     */
    private String contactEmail(Metadata metadata) {
        if (metadata.getContact() != null) {
            return metadata.getContact().getEmail().trim();
        }
        return "";
    }
}
