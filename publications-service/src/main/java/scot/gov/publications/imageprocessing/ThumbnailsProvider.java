package scot.gov.publications.imageprocessing;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.util.FileType;
import scot.gov.publications.util.IconNames;
import scot.gov.publications.util.TempFileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

public class ThumbnailsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ThumbnailsProvider.class);

    private static final List<Integer> SIZES = asList(
            330,
            214,
            165,
            107);

    ImageProcessing imageProcessing = new ImageProcessing();

    /**
     * Create thumbnails for document attachments.
     */
    public Map<Integer, File> thumbnails(InputStream documentStream, String mimeType) throws IOException {

        FileType type = FileType.forMimeType(mimeType);

        // if we did not recognise the file type then use the fallback icon
        if (type == null) {
            LOG.warn("Unrecognised mime type: {}", mimeType);
            return fixedThumbnails(IconNames.FALLBACK);
        }

        if (type == FileType.PDF) {
            return pdfThumbnails(documentStream);
        }

        if (type.isImage()) {
            return imageThumbnails(documentStream, type);
        }

        return fixedThumbnails(type.getIconName());
    }

    private Map<Integer, File> pdfThumbnails(InputStream documentStream) throws IOException {
        File pdfImage = pdfImageStream(documentStream);
        Map<Integer, File> thumbnails = imageThumbnails(pdfImage);
        FileUtils.deleteQuietly(pdfImage);
        return thumbnails;
    }

    private File pdfImageStream(InputStream documentStream) throws IOException {
        File pdfImg = null;
        try {
            pdfImg = imageProcessing.extractPdfCoverImage(documentStream);
            return pdfImg;
        } catch (Exception e) {
            FileUtils.deleteQuietly(pdfImg);
            throw new IOException(e);
        }
    }

    private Map<Integer, File> imageThumbnails(InputStream input, FileType type) throws IOException {
        File imageFile = null;
        try {
            imageFile = TempFileUtil.createTempFile("imagethumbs", type, input);
            return imageThumbnails(imageFile);
        } finally {
            FileUtils.deleteQuietly(imageFile);
        }
    }

    private Map<Integer, File> imageThumbnails(File image) throws IOException {
        Map<Integer, File> thumbs = new HashMap<>();
        for (Integer size : SIZES) {
            File thumb = imageThumbnail(image, size);
            thumbs.put(size, thumb);
        }
        return thumbs;
    }

    private File imageThumbnail(File image, int size) throws IOException {
        File thumbnail = null;
        try {
            thumbnail = imageProcessing.thumbnail(new FileInputStream(image), size);
            return thumbnail;
        } catch (Exception e) {
            FileUtils.deleteQuietly(thumbnail);
            throw new IOException(e);
        }
    }

    private Map<Integer, File> fixedThumbnails(String iconname) throws IOException {
        Map<Integer, File> thumbs = new HashMap<>();
        for (Integer size : SIZES) {
            File thumb = fixedThumbnail(iconname, size);
            thumbs.put(size, thumb);
        }
        return thumbs;
    }

    private File fixedThumbnail(String iconname, int size) throws IOException {

        // lookup based on the required size
        String filename = String.format("/thumbnails/%s_%dpx.png", iconname, size);
        InputStream inputStream = ThumbnailsProvider.class.getResourceAsStream(filename);
        if (isNull(inputStream)) {
            // check we have default icon for this size
            filename = String.format("/thumbnails/gen_%dpx.png", size);
            inputStream = ThumbnailsProvider.class.getResourceAsStream(filename);
        }
        if (isNull(inputStream)) {
            throw new IOException("Could not load thumbnail " + filename);
        }

        return TempFileUtil.createTempFile("fixedthumbnila", FileType.PNG, inputStream);
    }

}
