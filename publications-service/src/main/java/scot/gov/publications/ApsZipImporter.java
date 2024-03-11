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
import scot.gov.publishing.searchjournal.SearchJournalEntry;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import java.time.ZonedDateTime;
import java.util.*;
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

    PublicationsSearchJournal searchJournal = new PublicationsSearchJournal();

    public String importApsZip(ZipFile zipFile, Publication publication) throws ApsZipImporterException {
        Session session = newJCRSession();
        PublicationNodeUpdater publicationNodeUpdater = new PublicationNodeUpdater(session, configuration);
        PublicationPageUpdater publicationPageUpdater = new PublicationPageUpdater(session, configuration);
        ImageUploader imageUploader = new ImageUploader(session, imageProcessing);
        DocumentUploader documentUploader = new DocumentUploader(session, imageProcessing, exif, configuration);

        Node publicationFolder = null;
        Node imagesFolder = null;

        try {
            Manifest manifest = manifestExtractor.extract(zipFile);
            Metadata metadata = metadataExtractor.extract(zipFile);

            // ensure that the publication type is valid
            assertValidPublicationType(session, metadata.mappedPublicationType());

            LOG.info("Extracted metadata, isbn is {}, title is {}, publication date is {}",
                    metadata.getIsbn(),
                    metadata.getTitle(),
                    metadata.getPublicationDateWithTimezone());

            // if there is already a publication then unpublish it in funnelback
            Node publicationNode = publicationNodeUpdater.findPublicationNodeToUpdate(metadata);
            List<SearchJournalEntry> searchJournalEntries = new ArrayList<>();
            if (publicationNode != null && "published".equals(publicationNode.getProperty("hippostd:state").getString())) {
                // it is currently published so collect the list of funnelback actions to completely unpublish it
                // this is because we are about to replace it and some pages may not be there afterwards
                searchJournalEntries.addAll(searchJournal.getJournalEntries("depublish", session, publicationNode.getParent().getParent()));
            }
            publicationFolder = publicationNodeUpdater.createOrUpdatePublicationNode(metadata, publication);

            Map<String, String> imgMap = imageUploader.createImages(zipFile, publicationFolder);
            Map<String, Node> docMap = documentUploader.uploadDocuments(zipFile, publicationFolder, manifest, metadata);
            if (!imgMap.isEmpty()) {
                imagesFolder = session.getNodeByIdentifier(imgMap.entrySet().iterator().next().getValue()).getParent();
            }

            publicationPageUpdater.addPages(
                    zipFile,
                    publicationFolder,
                    imgMap,
                    docMap,
                    metadata.getPublicationDateWithTimezone(),
                    metadata.shoudlEmbargo());

            publicationFolder = publicationNodeUpdater.ensureMonthNode(publicationFolder, metadata);
            ensureFolderActions(publicationFolder, metadata.getPublicationType());

            session.save();

            // sort the parent folder
            hippoUtils.sortChildren(publicationFolder.getParent());

            // if the publication is published then create journal entries for all pages
            boolean isPublished = metadata.getPublicationDateWithTimezone().isBefore(ZonedDateTime.now());
            if (isPublished) {
                searchJournalEntries.addAll(searchJournal.getJournalEntries("publish", session, publicationFolder));
            }
            searchJournal.recordJournalEntries(session, searchJournalEntries);
            return publicationFolder.getPath();
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to save session", e);
        } catch (ApsZipImporterException e) {
            LOG.error("Throwable thrown", e);
            removePublicationFolderQuietly(publicationFolder, imagesFolder);
            throw e;
        } finally {
            session.logout();
        }
    }

    void removePublicationFolderQuietly(Node publicationFolder, Node imagesFolder) {
        if (publicationFolder == null) {
            return;
        }

        try {
            if (imagesFolder != null) {
                imagesFolder.remove();
            }
            Session session = publicationFolder.getSession();
            publicationFolder.remove();
            session.save();
        } catch (RepositoryException e) {
            LOG.error("Failed to remove publication folder after exception", e);
        }
    }

    /**
     * work out if the publication is already published or not.  If it is then thos might change what we need to do to
     * reconcile with funnelback
     */
    boolean isAlreadyPublished(PublicationNodeUpdater publicationNodeUpdater, Metadata metadata) throws RepositoryException {
        Node publicationNode = publicationNodeUpdater.findPublicationNodeToUpdate(metadata);

        if (publicationNode == null) {
            return false;
        }

        return "published".equals(publicationNode.getProperty("hippostd:state").getString());
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

    private void ensureFolderActions(Node publicationFolder, String type) throws RepositoryException {
        // We might have created a new month or year folder ... ensure that they have the right actions
        Node monthFolder = publicationFolder.getParent();
        Node yearFolder = monthFolder.getParent();
        hippoUtils.setPropertyStrings(publicationFolder, HIPPOSTD_FOLDERTYPE, actions());
        hippoUtils.setPropertyStrings(monthFolder, HIPPOSTD_FOLDERTYPE, publicationActions(type));
        hippoUtils.setPropertyStrings(yearFolder, HIPPOSTD_FOLDERTYPE, actions("new-publication-month-folder"));
    }

    Collection<String> publicationActions(String type) {
        switch (type) {
            case "minutes":
                return actions("new-minutes-folder");
            case "foi-eir-release":
                return actions("new-foi-folder");
            default:
                return actions("new-publication-folder", "new-complex-document-folder");
        }
    }

    Collection<String> actions(String ...actions) {
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
