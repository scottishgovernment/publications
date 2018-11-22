package scot.gov.publications.rest;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporter;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.FileUtil;
import scot.gov.publications.util.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Uploads zip files from PublicationStorage (s3) to Hippo, recording status information in the
 * PublicationRepository (postgres).
 */
public class PublicationUploader  {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsResource.class);

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    PublicationStorage storage;

    @Inject
    PublicationRepository repository;

    @Inject
    ApsZipImporter apsZipImporter;

    FileUtil fileUtil = new FileUtil();

    ZipUtil zipUtil = new ZipUtil();

    public void importPublication(final Publication publication) {
        File downloadedFile = null;
        try {
            // mark it as processing
            publication.setState(State.PROCESSING.name());
            repository.update(publication);

            // download the file from s3
            InputStream storageStream = storage.get(publication);
            downloadedFile = fileUtil.createTempFile("downloadedPublicationFromS3", "zip", storageStream);
            File extractedZip = zipUtil.extractNestedZipFile(downloadedFile);
            ZipFile zipFile = new ZipFile(extractedZip);

            // try to import it
            apsZipImporter.importApsZip(zipFile);

            // save it as done
            publication.setState(State.DONE.name());
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
        }
    }

    private void populateErrorInformation(Publication publication, String details, Throwable t) {
        LOG.error("{} {}", details, t);
        publication.setState(State.FAILED.name());
        publication.setStatedetails(details);
        publication.populateStackTrace(t);
    }
}