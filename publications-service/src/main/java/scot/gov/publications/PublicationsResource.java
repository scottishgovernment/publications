package scot.gov.publications;

import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.util.TempFileUtil;
import scot.gov.publications.util.ZipUtil;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
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

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Collection<Publication> list() {
        try {
            return repository.list(0, 100);
        } catch (PublicationRepositoryException e) {
            throw new RuntimeException("arg", e);
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_JSON })
    public Response postFormData(@MultipartForm FileUpload fileUpload) {

        // extract the zip file from the uploaded file
        File extractedZipFile = null;
        try {
            extractedZipFile = ZipUtil.extractNestedZipFile(fileUpload);
        } catch (IOException e) {
            FileUtils.deleteQuietly(extractedZipFile);
            return Response.status(400).entity(UploadResponse.error("Failed to extract zip file", e)).build();
        }

        // get the publication details form the zip
        Publication publication = null;
        try {
            publication = newPublication(extractedZipFile);
        } catch (ApsZipImporterException e) {
            return Response.status(400).entity(UploadResponse.error("Failed to extract metadata from zip", e)).build();
        }

        // upload the file to s3
        try {
            storage.save(publication, extractedZipFile);
        } catch (PublicationStorageException e) {
            return Response.status(500).entity(UploadResponse.error("Failed to upload zip file to s3", e)).build();
        }

        try {
            repository.create(publication);
        } catch (PublicationRepositoryException e) {
            return Response.status(500).entity(UploadResponse.error("Failed to save publication to the repository", e)).build();
        }

        // submit the publication to the processing queue
        importPublication(publication);
        FileUtils.deleteQuietly(extractedZipFile);
        return Response.accepted(UploadResponse.accepted(publication)).build();
    }

    private void importPublication(final Publication publication) {
        executor.submit(() -> doImportPublication(publication));
    }

    private void doImportPublication(final Publication publication) {
// TODO: add stacktrace field to the publicaiton table
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
            publication.setState(State.FAILED.name());
            publication.setStateDetails("Failed to get JCR session");
            return;
        } catch (IOException e) {
            publication.setState(State.FAILED.name());
            publication.setStateDetails("Failed to save publication as a temp file");
            return;
        } catch (PublicationStorageException e) {
            publication.setState(State.FAILED.name());
            publication.setStateDetails("Failed to get publication from s3");
            return;
        } catch(PublicationRepositoryException e) {
            publication.setState(State.FAILED.name());
            publication.setStateDetails("Failed to save publication to repostitory");
        } catch (ApsZipImporterException e) {
            publication.setState(State.FAILED.name());
            publication.setStateDetails(e.getMessage());
        } catch (Throwable t) {
            publication.setState(State.FAILED.name());
            publication.setStateDetails(t.getMessage());
        } finally {
            FileUtils.deleteQuietly(downloadedFile);
        }

        try {
            repository.update(publication);
        } catch (PublicationRepositoryException e) {
            System.out.println("Failed to save pub");
        }

    }

    private Publication newPublication(File zip) throws ApsZipImporterException {
        Metadata metadata = metadataExtractor.extract(zip);
        Publication publication = new Publication();
        publication.setId(UUID.randomUUID().toString());
        publication.setIsbn(metadata.getIsbn());
        publication.setTitle(metadata.getTitle());
        publication.setState(State.PENDING.name());
        // TODO: decide how to handle dates ...
        // publication.setEmbargoDate(metadata.getPublicationDate());
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
