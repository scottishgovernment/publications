package scot.gov.publications;

import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.hippo.DocumentUploader;
import scot.gov.publications.hippo.ImageUploader;
import scot.gov.publications.hippo.PublicationNodeUpdater;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.hippo.pages.PublicationPageUpdater;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestExtractor;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Imports zip files into Hippo.
 */
public class ApsZipImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ApsZipImporter.class);

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    SessionFactory sessionFactory;

    ManifestExtractor manifestExtractor = new ManifestExtractor();

    MetadataExtractor metadataExtractor = new MetadataExtractor();

    public String importApsZip(ZipFile zipFile) throws ApsZipImporterException {
        Session session = newJCRSession();
        PublicationNodeUpdater publicationNodeUpdater = new PublicationNodeUpdater(session, configuration);
        PublicationPageUpdater publicationPageUpdater = new PublicationPageUpdater(session, configuration);
        ImageUploader imageUploader = new ImageUploader(session);
        DocumentUploader documentUploader = new DocumentUploader(session, configuration);

        try {
            Manifest manifest = manifestExtractor.extract(zipFile);
            Metadata metadata = metadataExtractor.extract(zipFile);
            LOG.info("Extracted metadata, isbn is {}, title is {}", metadata.getIsbn(), metadata.getTitle());
            Node publicationFolder = publicationNodeUpdater.createOrUpdatePublicationNode(metadata);
            Map<String, String> imgMap = imageUploader.createImages(zipFile, publicationFolder);
            Map<String, Node> docMap = documentUploader.uploadDocuments(zipFile, publicationFolder, manifest, metadata);
            publicationPageUpdater.addPages(
                    zipFile,
                    publicationFolder,
                    imgMap,
                    docMap,
                    metadata.getPublicationDateWithTimezone());
            session.save();
            return publicationFolder.getPath();
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to save session", e);
        } finally {
            session.logout();
        }
    }

    private Session newJCRSession() throws ApsZipImporterException {
        try {
            return sessionFactory.newSession();
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to create a new JCR session", e);
        } catch (RemoteRuntimeException e) {
            throw new ApsZipImporterException("JCR repo is not running", e);
        }
    }
}
