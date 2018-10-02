package scot.gov.publications.hippo;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.ApsZipImporterException;


import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.*;
import java.util.zip.ZipFile;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ImageUploaderTest {

    @Test(expected = ApsZipImporterException.class)
    public void repoExceptionRethrownAsApsZipImporterException() throws Exception {
        // ARRANGE
        Session session = mock(Session.class);
        ImageUploader sut = new ImageUploader(session);

        sut.hippoUtils = mock(HippoUtils.class);
        when(sut.hippoUtils.pathFromNode(any())).thenThrow(new RepositoryException("arg"));

        // ACT
        sut.createImages(mock(ZipFile.class), mock(Node.class));

        // ASSERT -- see epxected
    }

    @Test
    public void uploadesImagesInZipFile() throws Exception {

        ZipFile zipFile = ZipFixtures.exampleZip();
        Session session = mock(Session.class);
        ImageUploader sut = new ImageUploader(session);
        Node node = mock(Node.class);
        Node pubFolder = Mockito.mock(Node.class);
        Node imgSetNode = Mockito.mock(Node.class);
        Node imgGalleryNode = Mockito.mock(Node.class);

        sut.imageNodeFactory = Mockito.mock(HippoImageNodeFactory.class);
        sut.hippoUtils = mock(HippoUtils.class);
        sut.hippoPaths = mock(HippoPaths.class);

        when(sut.hippoUtils.pathFromNode(any())).thenReturn(singletonList("path"));
        when(sut.hippoPaths.ensureImagePath(any())).thenReturn(node);
        when(imgSetNode.getParent()).thenReturn(imgGalleryNode);
        when(sut.imageNodeFactory.ensureImageSetNodeExists(any(), any(), any(), any(), any())).thenReturn(imgSetNode);

        Set<String> expectedFilenames = new HashSet<>();
        Collections.addAll(expectedFilenames,
                "SCT04185156361_g01.jpg",
                "SCT04185156361_g02.gif",
                "SCT04185156361_g03.gif",
                "SCT04185156361_g04.gif",
                "SCT04185156361_g05.gif",
                "SCT04185156361_g06.gif");

        // ACT
        Map<String, String> actual = sut.createImages(zipFile, pubFolder);

        // ARRANGE
        assertEquals(actual.keySet(), expectedFilenames);
    }
}
