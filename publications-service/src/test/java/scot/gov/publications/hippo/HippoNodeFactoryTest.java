package scot.gov.publications.hippo;

import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration;

import javax.jcr.*;

import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.mockito.Mockito.*;
import static scot.gov.publications.hippo.Constants.DOCUMENT_MIXINS;
import static scot.gov.publications.hippo.Constants.JCR_MIMETYPE;

public class HippoNodeFactoryTest {

    @Test
    public void newDocumentNodePublishesNodeIfPublicationDateHasPassed() throws Exception {
        // ARRANGE
        HippoNodeFactory sut = new HippoNodeFactory(mock(Session.class), new PublicationsConfiguration());
        Node handle = mock(Node.class);
        Node node = mock(Node.class);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.createNode(handle, "slug", "type", DOCUMENT_MIXINS)).thenReturn(node);

        // ACT
        sut.newDocumentNode(handle, "slug", "title", "type", LocalDateTime.now().minusDays(1));

        // ASSERT
        verify(node).setProperty("hippostd:state", "published");
    }

    @Test
    public void newDocumentNodeScheduledToPublishIfPublicationDateHasNotPassed() throws Exception {
        // ARRANGE
        HippoNodeFactory sut = new HippoNodeFactory(mock(Session.class), new PublicationsConfiguration());
        Node handle = mock(Node.class);
        Node node = mock(Node.class);
        Node job = mock(Node.class);
        Node triggers = mock(Node.class);
        Node defaultNode = mock(Node.class);
        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.createNode(handle, "slug", "type", DOCUMENT_MIXINS)).thenReturn(node);
        when(handle.addNode("hippo:request", "hipposched:workflowjob")).thenReturn(job);
        when(job.addNode("hipposched:triggers", "hipposched:triggers")).thenReturn(triggers);
        when(triggers.addNode("default", "hipposched:simpletrigger")).thenReturn(defaultNode);

        // ACT
        sut.newDocumentNode(handle, "slug", "title", "type", LocalDateTime.now().plusDays(1));

        // ASSERT
        verify(node).setProperty("hippostd:state", "unpublished");
    }

    @Test
    public void canCreateResourceNode() throws Exception {
        // ARRANGE
        HippoNodeFactory sut = new HippoNodeFactory(session(), new PublicationsConfiguration());
        ZipFile zipFile = ZipFixtures.exampleZip();
        ZipEntry zipEntry = zipFile.getEntry("SCT04185156361/SCT04185156361.pdf");
        Node parent = mock(Node.class);
        Node resourceNode = mock(Node.class);
        when(parent.addNode("govscot:document", "hippo:resource")).thenReturn(resourceNode);

        // ACT
        sut.newResourceNode(parent, "govscot:document", "SCT04185156361.pdf", zipFile, zipEntry);

        // ASSERT
        verify(resourceNode).setProperty(JCR_MIMETYPE, "application/pdf");
    }

    // TODO finish this
//    @Test
//    public void ioExceptionRethrownAsRepoExceptionByCreateResourceNode() throws Exception {
//        // ARRANGE
//        HippoNodeFactory sut = new HippoNodeFactory(session());
//        ZipFile zipFile = exampleZip();
//        ZipEntry zipEntry = zipFile.getEntry("SCT04185156361/SCT04185156361.pdf");
//        Node parent = mock(Node.class);
//        Node resourceNode = mock(Node.class);
//        when(parent.addNode("govscot:document", "hippo:resource")).thenReturn(resourceNode);
//
//        // ACT
//        sut.newResourceNode(parent, "govscot:document", "SCT04185156361.pdf", zipFile, zipEntry);
//
//        // ASSERT
//        verify(resourceNode).setProperty(JCR_MIMETYPE, "application/pdf");
//    }

    Session session() throws RepositoryException {
        Session session = mock(Session.class);
        ValueFactory vf = mock(ValueFactory.class);
        Binary binary = mock(Binary.class);
        when(session.getValueFactory()).thenReturn(vf);
        when(vf.createBinary(any())).thenReturn(binary);
        return session;
    }
}
