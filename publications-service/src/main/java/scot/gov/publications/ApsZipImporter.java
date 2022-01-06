package scot.gov.publications;

import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.hippo.*;
import scot.gov.publications.hippo.pages.PublicationPageUpdater;
import scot.gov.publications.imageprocessing.ImageProcessing;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestExtractor;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.util.Exif;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipFile;

import static scot.gov.publications.hippo.Constants.HIPPOSTD_FOLDERTYPE;

/**
 * Imports zip files into Hippo.
 */
public class ApsZipImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ApsZipImporter.class);

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    SessionFactory sessionFactory;

    @Inject
    ImageProcessing imageProcessing;

    @Inject
    Exif exif;

    HippoUtils hippoUtils = new HippoUtils();

    ManifestExtractor manifestExtractor = new ManifestExtractor();

    MetadataExtractor metadataExtractor = new MetadataExtractor();

    public String importApsZip(ZipFile zipFile, Publication publication) throws ApsZipImporterException {
        Session session = newJCRSession();
        PublicationNodeUpdater publicationNodeUpdater = new PublicationNodeUpdater(session, configuration);
        PublicationPageUpdater publicationPageUpdater = new PublicationPageUpdater(session, configuration);
        ImageUploader imageUploader = new ImageUploader(session, imageProcessing);
        DocumentUploader documentUploader = new DocumentUploader(session, imageProcessing, exif, configuration);

        try {
            Manifest manifest = manifestExtractor.extract(zipFile);
            Metadata metadata = metadataExtractor.extract(zipFile);

            // ensure that the publication type is valid
            assertValidPublicationType(session, metadata.mappedPublicationType());

            LOG.info("Extracted metadata, isbn is {}, title is {}, publication date is {}",
                    metadata.getIsbn(),
                    metadata.getTitle(),
                    metadata.getPublicationDateWithTimezone());
            Node publicationFolder = publicationNodeUpdater.createOrUpdatePublicationNode(metadata, publication);

            LOG.info("publication folder is {}", publicationFolder.getPath());
            Map<String, String> imgMap = imageUploader.createImages(zipFile, publicationFolder);
            Map<String, Node> docMap = documentUploader.uploadDocuments(zipFile, publicationFolder, manifest, metadata);
            publicationPageUpdater.addPages(
                    zipFile,
                    publicationFolder,
                    imgMap,
                    docMap,
                    metadata.getPublicationDateWithTimezone(),
                    metadata.shoudlEmbargo());
            publicationFolder = publicationNodeUpdater.ensureMonthNode(publicationFolder, metadata);
            ensureFolderActions(publicationFolder);

            session.save();

            // sort the parent folder
            hippoUtils.sortChildren(publicationFolder.getParent());

            return publicationFolder.getPath();
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to save session", e);
        } finally {
            session.logout();
        }
    }

    private void assertValidPublicationType(Session session, String type) throws ApsZipImporterException {
        try {
            HippoPaths paths = new HippoPaths(session);
            String slugType = paths.slugify(type, false);
            String xpath = String.format(
                    "/jcr:root/content/documents/govscot/valuelists" +
                    "/publicationTypes/publicationTypes/selection:listitem[selection:key = '%s']", slugType);
            LOG.info("assertValidPublicationType {}", xpath);
            Node typeNode = hippoUtils.findOneQuery(session, xpath, Query.XPATH);
            if (typeNode == null) {
                throw new ApsZipImporterException("Unrecognised publication type:" + type);
            }
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Unable to fetch publication types", e);
        }
    }

    private void ensureFolderActions(Node publicationFolder) throws RepositoryException {
        // We might have created a new month or year folder ... ensure that they have the right actions
        Node monthFolder = publicationFolder.getParent();
        Node yearFolder = monthFolder.getParent();
        hippoUtils.setPropertyStrings(publicationFolder, HIPPOSTD_FOLDERTYPE, actions());
        hippoUtils.setPropertyStrings(monthFolder, HIPPOSTD_FOLDERTYPE, actions("new-publication-folder", "new-complex-document-folder"));
        hippoUtils.setPropertyStrings(yearFolder, HIPPOSTD_FOLDERTYPE, actions("new-publication-month-folder"));
    }

    private Collection<String> actions(String ...actions) {
        return Arrays.asList(actions);
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
