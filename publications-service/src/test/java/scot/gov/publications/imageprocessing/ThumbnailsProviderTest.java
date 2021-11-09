package scot.gov.publications.imageprocessing;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.util.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ThumbnailsProviderTest {

    @Test(expected = IOException.class)
    public void imageProcessingExcpetionThrown() throws Exception {
        ImageProcessing imageProcessing = mock(GraphicsMagickImageProcessingImpl.class);
        Mockito.when(imageProcessing.extractPdfCoverImage(Mockito.any())).thenThrow(new ImageProcessingException("arg"));
        ThumbnailsProvider sut = new ThumbnailsProvider(imageProcessing);
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplepdf.pdf");
        String mimeType = FileType.PDF.getMimeType();
        sut.thumbnails(docStream, mimeType);
    }

// these tests fail due to graphiks magik and ExifProcessImpl tool not being installed on jenkins.
//    @Test
//    public void pdfGreenpath() throws Exception {
//        ThumbnailsProvider sut = new ThumbnailsProvider();
//        sut.imageProcessing = new GraphicsMagickImageProcessingImpl();
//        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplepdf.pdf");
//        String mimeType = FileType.PDF.getMimeType();
//        Map<Integer, File> thumbs = sut.thumbnails(docStream, mimeType);
//        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
//        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
//    }

//    @Test
//    public void jpgGreenpath() throws Exception {
//        ThumbnailsProvider sut = new ThumbnailsProvider();
//        sut.imageProcessing = new GraphicsMagickImageProcessingImpl();
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
        ImageProcessing imageProcessing = mock(ImageProcessing.class);
        ThumbnailsProvider sut = new ThumbnailsProvider(imageProcessing);
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplexls.xls");
        String mimeType = FileType.XLS.getMimeType();
        Map<Integer, File> thumbs = sut.thumbnails(docStream, mimeType);
        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
        thumbs.values().stream().forEach(FileUtils::deleteQuietly);
    }

    @Test
    public void unrecognisedExtensionReturnfallback() throws Exception {
        ImageProcessing imageProcessing = mock(ImageProcessing.class);
        ThumbnailsProvider sut = new ThumbnailsProvider(imageProcessing);
        InputStream docStream = ThumbnailsProviderTest.class.getResourceAsStream("/examplexls.xlsww");
        Map<Integer, File> thumbs = sut.thumbnails(docStream, "XXXXXX");
        assertTrue(thumbs.containsKey(Integer.valueOf(330)));
        assertTrue(thumbs.containsKey(Integer.valueOf(214)));
        assertTrue(thumbs.containsKey(Integer.valueOf(165)));
        assertTrue(thumbs.containsKey(Integer.valueOf(107)));
        thumbs.values().stream().forEach(FileUtils::deleteQuietly);
    }

}
