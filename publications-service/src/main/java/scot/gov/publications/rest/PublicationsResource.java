package scot.gov.publications.rest;

import org.apache.commons.io.FileUtils;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
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
     * @param queryString optional query string used to filter by title or isbn
     * @return A paged list of publications.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response list(
            @DefaultValue("1") @QueryParam("page") int page,
            @DefaultValue("10") @QueryParam("size") int size,
            @DefaultValue("") @QueryParam("q") String queryString) {
        try {
            ListResult result = repository.list(page, size, queryString);
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
     * Upload a new zip file to be processed.
     *
     * @param fileUpload A multipart file upload containing a zip in the expecte format.
     * @return Response indicating if the zip has been accepted.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_JSON })
    public Response postFormData(@MultipartForm UploadRequest fileUpload) {

        // extract the zip file from the uploaded file
        File zipFile = null;
        File extractedZipFile = null;
        try {
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
            publication = newPublication(zipFile, extractedZipFile);

            // upload the file to s3
            storage.save(publication, zipFile);

            // save the details in the repository
            repository.create(publication);

            // submit the publication to the processing queue and return accepted status code
            importPublication(publication);
            return Response.accepted(UploadResponse.accepted(publication)).build();
        } catch (ApsZipImporterException e) {
            String msg = "Failed to extract metadata from zip";
            LOG.error(msg,  e);
            return Response.status(400).entity(UploadResponse.error(msg)).build();
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

    private void importPublication(Publication publication) {
        executor.submit(() -> publicationUploader.importPublication(publication));
    }

    private Publication newPublication(File zip, File extractedZip) throws ApsZipImporterException {
        Metadata metadata = metadataExtractor.extract(extractedZip);
        Publication publication = new Publication();
        publication.setId(UUID.randomUUID().toString());
        publication.setIsbn(metadata.getIsbn());
        publication.setTitle(metadata.getTitle());
        publication.setState(State.PENDING.name());
        Instant publishInstant = metadata.getPublicationDate().toInstant(ZoneOffset.UTC);
        publication.setEmbargodate(Timestamp.from(publishInstant));
        try {
            publication.setChecksum(fileUtil.hash(zip));
        } catch (IOException e) {
            throw new ApsZipImporterException("Unable to calculate checksum", e);
        }
        return publication;
    }

}
