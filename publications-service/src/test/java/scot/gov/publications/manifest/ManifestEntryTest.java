package scot.gov.publications.manifest;

import org.junit.Test;
import scot.gov.publications.manifest.ManifestEntry;

import static org.junit.Assert.assertEquals;

public class ManifestEntryTest {

    @Test
    public void getTitleOrFilenameReturnsFilenameIfTitleIsNull() {
        ManifestEntry sut = new ManifestEntry("filename", null);
        assertEquals(sut.getTitleOrFilename(), "filename");
    }

    @Test
    public void getTitleOrFilenameReturnsFilenameIfTitleIsEmpty() {
        ManifestEntry sut = new ManifestEntry("filename", "");
        assertEquals(sut.getTitleOrFilename(), "filename");
    }

    @Test
    public void getTitleOrFilenameReturnsTitleIfNotEmpty() {
        ManifestEntry sut = new ManifestEntry("filename", "title");
        assertEquals(sut.getTitleOrFilename(), "title");
    }

}
