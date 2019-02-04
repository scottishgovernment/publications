package scot.gov.publications.imageprocessing;

import java.io.File;
import java.io.InputStream;

/**
 * Used to create PDF cover page images an thumbnails of images.
 */
public interface ImageProcessing {

    /**
     * Extract a pdf cover image syutable for using as a thumbnail for a publicaiton
     *
     * @param source Input stream for a PDF
     * @return File containing the extracted image.
     * @throws ImageProcessingException If we fail to extract the image for some reason.
     */
    File extractPdfCoverImage(InputStream source) throws ImageProcessingException;

    /**
     * Convert an image into a thumbnails with the specified width.
     *
     * @param source A valid image file.
     * @param width required image width
     * @return A file containing a resized version of the image
     * @throws ImageProcessingException If resizing fails for some reason
     */
    File thumbnail(InputStream source, int width) throws ImageProcessingException;
}
