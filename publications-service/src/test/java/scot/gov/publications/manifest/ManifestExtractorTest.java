package scot.gov.publications.manifest;

import org.junit.Test;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.hippo.ZipFixtures;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.Collections.enumeration;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ManifestExtractorTest {

    @Test
    public void canExtractManifest() throws Exception {
        ManifestExtractor sut = new ManifestExtractor();
        Manifest manifest = sut.extract(ZipFixtures.exampleZip());
        assertNotNull(manifest);
    }

    @Test(expected = ApsZipImporterException.class)
    public void throwsExceptionIfNoManifest() throws Exception {
        ManifestExtractor sut = new ManifestExtractor();
        Manifest manifest = sut.extract(ZipFixtures.zipWithNoManifest());
        assertNotNull(manifest);
    }

    @Test(expected = ApsZipImporterException.class)
    public void throwsExceptionForUnparseableManifest() throws Exception {
        ManifestExtractor sut = new ManifestExtractor();
        sut.manifestParser = mock(ManifestParser.class);;
        when(sut.manifestParser.parse(any())).thenThrow(new ManifestParserException("arg"));
        sut.extract(ZipFixtures.exampleZip());
    }

    @Test(expected = ApsZipImporterException.class)
    public void ioExceptionRethrown() throws Exception {
        ManifestExtractor sut = new ManifestExtractor();
        ZipFile zip = zipFileWithDirname("dirname/");
        ZipEntry manifestEntry = entryWithName("manifest.txt");
        when(zip.getEntry("dirname/manifest.txt")).thenReturn(manifestEntry);
        when(zip.getInputStream(any())).thenThrow(new IOException("arg"));
        sut.extract(zip);
    }

    ZipEntry entryWithName(String name) {
        ZipEntry entry = mock(ZipEntry.class);
        when(entry.getName()).thenReturn(name);
        return entry;
    }

    @SuppressWarnings("unchecked")
    ZipFile zipFileWithDirname(String name) {
        ZipFile zipFile = mock(ZipFile.class);
        ZipEntry entry = entryWithName(name);
        when(zipFile.entries()).thenReturn(enumeration((Collection) singleton(entry)));
        return zipFile;
    }

}
