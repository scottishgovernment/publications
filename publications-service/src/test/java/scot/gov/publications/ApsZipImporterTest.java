package scot.gov.publications;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.hippo.DocumentUploader;
import scot.gov.publications.hippo.ImageUploader;
import scot.gov.publications.hippo.PublicationNodeUpdater;
import scot.gov.publications.hippo.pages.PublicationPageUpdater;
import scot.gov.publications.manifest.ManifestExtractor;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataExtractor;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.time.LocalDateTime;
import java.util.zip.ZipFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApsZipImporterTest {

//    @Test
//    public void greenpath() throws Exception {
//        Session session = mock(Session.class);
//        ApsZipImporter sut = sut(session);
//        ZipFile zipFile = mock(ZipFile.class);
//        sut.importApsZip(zipFile);
//    }
//
//    @Test(expected = ApsZipImporterException.class)
//    public void exceptionThrownIfSessionSaveFails() throws Exception {
//        Session session = mock(Session.class);
//        Mockito.doThrow(new RepositoryException("arg")).when(session).save();
//        ApsZipImporter sut = sut(session);
//        ZipFile zipFile = mock(ZipFile.class);
//        sut.importApsZip(zipFile);
//    }
//
//    ApsZipImporter sut(Session session) throws Exception {
//        ApsZipImporter sut = new ApsZipImporter(session, new PublicationsConfiguration());
//        sut.documentUploader = mock(DocumentUploader.class);
//        sut.imageUploader = mock(ImageUploader.class);
//        sut.manifestExtractor = mock(ManifestExtractor.class);
//        sut.metadataExtractor = mock(MetadataExtractor.class);
//        sut.publicationPageUpdater = mock(PublicationPageUpdater.class);
//        Metadata metadata = new Metadata();
//        metadata.setPublicationDate(LocalDateTime.now());
//        when(sut.metadataExtractor.extract(any())).thenReturn(metadata);
//        sut.publicationNodeUpdater = mock(PublicationNodeUpdater.class);
//        Node folder = mock(Node.class);
//        when(sut.publicationNodeUpdater.createOrpdatePublicationNode(metadata)).thenReturn(folder);
//        return sut;
//    }
}
