package scot.gov.publications.rest;

import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporter;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.ListResult;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.TempFileUtil;
import scot.gov.publications.util.ZipUtil;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;
import javax.jcr.Session;

/**
 * REST endpoint to allow uploading APS zip files to be imported into Hippo.
 */
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
    SessionFactory sessionFactory;

    MetadataExtractor metadataExtractor = new MetadataExtractor();

    ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Get a paged list of publications with an optional queryString that can be used to perform a partial and case insensitive
     * match against the list of publicaitons.
     *
     * @param page the page to feths
     * @param size the number of items to fetch
     * @param queryString optional query string used to filter by title or isbm
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
            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
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
    public Publication get(@PathParam("id") String id) {
        try {
            Publication publication = repository.get(id);
            if (publication == null) {
                throw new WebApplicationException(Response.status(404).entity("Not Found").build());
            }
            return publication;
        } catch (PublicationRepositoryException e) {
            LOG.error("Failed to get publication {}", id, e);
            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_JSON })
    public Response postFormData(@MultipartForm UploadRequest fileUpload) {

        // extract the zip file from the uploaded file
        File extractedZipFile = null;
        try {
            extractedZipFile = ZipUtil.extractNestedZipFile(fileUpload);
        } catch (IOException e) {
            LOG.error("Failed to extract zip", e);
            FileUtils.deleteQuietly(extractedZipFile);
            return Response.status(400).entity(UploadResponse.error("Failed to extract zip file", e)).build();
        }

        // get the publication details form the zip
        Publication publication = null;
        try {
            publication = newPublication(extractedZipFile);

            // upload the file to s3
            storage.save(publication, extractedZipFile);

            // save the details in the repository
            repository.create(publication);

            // submit the publication to the processing queue and return accepted status code
            importPublication(publication);
            return Response.accepted(UploadResponse.accepted(publication)).build();
        } catch (ApsZipImporterException e) {
            String msg = "Failed to extract metadata from zip";
            LOG.error(msg,  e);
            return Response.status(400).entity(UploadResponse.error(msg, e)).build();
        } catch (PublicationStorageException e) {
            String msg = "Failed to upload zip file to s3";
            LOG.error(msg,  e);
            return Response.status(500).entity(UploadResponse.error(msg, e)).build();
        } catch (PublicationRepositoryException e) {
            String msg = "Failed to save publication to the repository";
            LOG.error(msg, e);
            return Response.status(500).entity(UploadResponse.error("Failed to save publication to the repository", e)).build();
        } finally {
            FileUtils.deleteQuietly(extractedZipFile);
        }
    }

    private void importPublication(final Publication publication) {
        executor.submit(() -> doImportPublication(publication));
    }

    private void doImportPublication(final Publication publication) {
        File downloadedFile = null;
        try {
            Session session = sessionFactory.newSession();

            // mark it as processing
            publication.setState(State.PROCESSING.name());
            repository.update(publication);

            // download the file from s3
            downloadedFile = TempFileUtil.createTempFile("downloadedPublicationFromS3", "zip", storage.get(publication));
            ZipFile zipFile = new ZipFile(downloadedFile);

            // try to import it
            ApsZipImporter apsZipImporter = new ApsZipImporter(session, configuration);
            apsZipImporter.importApsZip(zipFile);

            // save it as done
            publication.setState(State.DONE.name());
            repository.update(publication);
        } catch (RepositoryException e) {
            populateErrorInformation(publication, "Failed to talk to JCR repository", e);
        } catch (RemoteRuntimeException e) {
            populateErrorInformation(publication, "JCR repo is not running", e);
        } catch (IOException e) {
            populateErrorInformation(publication, "Failed to save publication as a temp file", e);
        } catch (PublicationStorageException e) {
            populateErrorInformation(publication, "Failed to get publication from s3", e);
        } catch(PublicationRepositoryException e) {
            populateErrorInformation(publication, "Failed to save publication to database", e);
        } catch (ApsZipImporterException e) {
            populateErrorInformation(publication, "Failed to import contents of zip", e);
        } finally {
            FileUtils.deleteQuietly(downloadedFile);
        }

        try {
            repository.update(publication);
        } catch (PublicationRepositoryException e) {
            LOG.error("Failed to save publicaiton status", e);
            publication.setState(State.FAILED.name());
            publication.setStatedetails(e.getMessage());
            publication.populateStackTrace(e);
            throw new WebApplicationException(e, Response.status(500).entity("Server error").build());
        }
    }

    private void populateErrorInformation(Publication publication, String details, Throwable t) {
        LOG.error("{} {}", details, t);
        publication.setState(State.FAILED.name());
        publication.setStatedetails(details);
        publication.populateStackTrace(t);
    }

    private Publication newPublication(File zip) throws ApsZipImporterException {
        Metadata metadata = metadataExtractor.extract(zip);
        Publication publication = new Publication();
        publication.setId(UUID.randomUUID().toString());
        publication.setIsbn(metadata.getIsbn());
        publication.setTitle(metadata.getTitle());
        publication.setState(State.PENDING.name());
        Instant publishInstant = metadata.getPublicationDate().toInstant(ZoneOffset.UTC);
        publication.setEmbargodate(Timestamp.from(publishInstant));
        publication.setChecksum(hash(zip));
        return publication;
    }

    private String hash(File zip) throws ApsZipImporterException {
        InputStream in = null;
        try {
            in = new FileInputStream(zip);
            return BinaryUtils.toHex(Md5Utils.computeMD5Hash(in));
        } catch (IOException e) {
            throw new ApsZipImporterException("Can not calculate checksum", e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

}
