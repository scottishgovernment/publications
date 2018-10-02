package scot.gov.publications;

import scot.gov.publications.hippo.DocumentUploader;
import scot.gov.publications.hippo.ImageUploader;
import scot.gov.publications.hippo.PublicationNodeUpdater;
import scot.gov.publications.hippo.pages.PublicationPageUpdater;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestExtractor;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Imports zip files into Hippo.
 */
public class ApsZipImporter {

    Session session;

    ManifestExtractor manifestExtractor = new ManifestExtractor();

    MetadataExtractor metadataExtractor = new MetadataExtractor();

    PublicationNodeUpdater publicationNodeUpdater;

    PublicationPageUpdater publicationPageUpdater;

    ImageUploader imageUploader;

    DocumentUploader documentUploader;

    public ApsZipImporter(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.publicationNodeUpdater = new PublicationNodeUpdater(session, configuration);
        this.publicationPageUpdater = new PublicationPageUpdater(session, configuration);
        this.imageUploader = new ImageUploader(session);
        this.documentUploader = new DocumentUploader(session, configuration);
    }

    public void importApsZip(ZipFile zipFile) throws ApsZipImporterException {
        Manifest manifest = manifestExtractor.extract(zipFile);
        Metadata metadata = metadataExtractor.extract(zipFile);
        Node publicationFolder = publicationNodeUpdater.createOrpdatePublicationNode(metadata);
        Map<String, String> imgMap = imageUploader.createImages(zipFile, publicationFolder);
        Map<String, Node> docMap = documentUploader.uploadDocuments(zipFile, publicationFolder, manifest, metadata);
        publicationPageUpdater.addPages(
                zipFile,
                publicationFolder,
                imgMap,
                docMap,
                metadata.getPublicationDate());

        try {
            session.save();
        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to save session", e);
        }
    }
}
