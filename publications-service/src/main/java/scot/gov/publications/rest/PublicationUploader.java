package scot.gov.publications.rest;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporter;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.FileUtil;
import scot.gov.publications.util.ZipUtil;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class PublicationUploader  {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsResource.class);

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    PublicationStorage storage;

    @Inject
    PublicationRepository repository;

    @Inject
    SessionFactory sessionFactory;

    FileUtil fileUtil = new FileUtil();

    ZipUtil zipUtil = new ZipUtil();

    public void importPublication(final Publication publication) {
        File downloadedFile = null;
        try {
            Session session = sessionFactory.newSession();

            // mark it as processing
            publication.setState(State.PROCESSING.name());
            repository.update(publication);

            // download the file from s3
            downloadedFile = fileUtil.createTempFile("downloadedPublicationFromS3", "zip", storage.get(publication));
            ZipFile zipFile = new ZipFile(zipUtil.extractNestedZipFile(downloadedFile));

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
            LOG.error("Failed to save publication status", e);
            publication.setState(State.FAILED.name());
            publication.setStatedetails(e.getMessage());
            publication.populateStackTrace(e);
        }
    }

    private void populateErrorInformation(Publication publication, String details, Throwable t) {
        LOG.error("{} {}", details, t);
        publication.setState(State.FAILED.name());
        publication.setStatedetails(details);
        publication.populateStackTrace(t);
    }
}
