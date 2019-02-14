package scot.gov.publications.hippo;

import org.junit.Before;
import org.junit.Test;
import scot.gov.publications.imageprocessing.GraphicsMagickImageProcessingImpl;
import scot.gov.publications.imageprocessing.ImageProcessingException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HippoImageNodeFactoryTest {

    @Before
    public void init() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test(expected = RepositoryException.class)
    public void imageProcessingExceptionRethrownAsRepoException() throws Exception {

        HippoImageNodeFactory sut = new HippoImageNodeFactory();
        sut.hippoUtils = mock(HippoUtils.class);
        sut.imageProcessing = mock(GraphicsMagickImageProcessingImpl.class);
        when(sut.imageProcessing.thumbnail(any(InputStream.class), anyInt()))
                .thenThrow(new ImageProcessingException("arg"));
        sut.binarySource = mock(BinarySource.class);
        Node galleryNode = mock(Node.class);
        Node imgSetHandle = mock(Node.class);
        Node imgSetNode = mock(Node.class);
        Node imgNodeOrig = mock(Node.class);
        Node imgNodeThumb = mock(Node.class);

        ZipFile zipFile = ZipFixtures.exampleZip();
        ZipEntry zipEntry = zipFile.getEntry("SCT04185156361/SCT04185156361_g01.jpg");
        String type = "type";
        String name ="name";

        when(sut.hippoUtils.ensureNode(galleryNode, "name", "hippo:handle", "mix:referenceable")).thenReturn(imgSetHandle);
        when(sut.hippoUtils.ensureNode(imgSetHandle, "name", "type")).thenReturn(imgSetNode);
        when(sut.hippoUtils.ensureNode(imgSetNode, "hippogallery:original", "hippogallery:image")).thenReturn(imgNodeOrig);
        when(sut.hippoUtils.ensureNode(imgSetNode, "hippogallery:thumbnail", "hippogallery:image")).thenReturn(imgNodeThumb);

        // imageSet, name, "hippogallery:image"
        // ACT
        sut.ensureImageSetNodeExists(zipFile, zipEntry, galleryNode, type, name);

        // ASSERT -- see expected exception
    }

}
