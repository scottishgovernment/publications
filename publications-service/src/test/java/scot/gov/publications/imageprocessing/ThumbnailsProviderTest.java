package scot.gov.publications.imageprocessing;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.util.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class ThumbnailsProviderTest {

    @Test(expected = IOException.class)
    public void imageProcessingExcpetionThrown() throws Exception {
        ThumbnailsProvider sut = new ThumbnailsProvider();
        sut.imageProcessing = Mockito.mock(ImageProcessing.class);
        Mockito.when(sut.imageProcessing.extractPdfCoverImage(Mockito.any())).thenThrow(new ImageProcessingException("arg"));
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplepdf.pdf");
        String mimeType = FileType.PDF.getMimeType();
        sut.thumbnails(docStream, mimeType);
    }

//    @Test
//    public void pdfGreenpath() throws Exception {
//        ThumbnailsProvider sut = new ThumbnailsProvider();
//        sut.imageProcessing = new ImageProcessing();
//        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplepdf.pdf");
//        String mimeType = FileType.PDF.getMimeType();
//        Map<Integer, File> thumbs = sut.thumbnails(docStream, mimeType);
//        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
//    }
//
//    @Test
//    public void jpgGreenpath() throws Exception {
//        ThumbnailsProvider sut = new ThumbnailsProvider();
//        sut.imageProcessing = new ImageProcessing();
//        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplejpg.jpg");
//        String mimeType = FileType.JPG.getMimeType();
//        Map<Integer, File> thumbs = sut.thumbnails(docStream, mimeType);
//        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
//    }

    @Test
    public void exelGreenpath() throws Exception {
        ThumbnailsProvider sut = new ThumbnailsProvider();
        sut.imageProcessing = new ImageProcessing();
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplexls.xls");
        String mimeType = FileType.XLS.getMimeType();
        Map<Integer, File> thumbs = sut.thumbnails(docStream, mimeType);
        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
    }

    @Test
    public void unrecognisedExtensionReturnfallback() throws Exception {
        ThumbnailsProvider sut = new ThumbnailsProvider();
        sut.imageProcessing = new ImageProcessing();
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplexls.xlsww");
        Map<Integer, File> thumbs = sut.thumbnails(docStream, "XXXXXX");
        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
    }
}
