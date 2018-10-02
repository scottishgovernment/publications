package scot.gov.publications.manifest;

import org.junit.Test;
import scot.gov.publications.hippo.ZipFixtures;

import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;

public class ManifestTest {

    @Test
    public void findZipEntryCanFindManifestEntries() throws Exception {
        ZipFile zip = ZipFixtures.exampleZip();
        Manifest manifest = new ManifestExtractor().extract(zip);
        for (ManifestEntry entry : manifest.getEntries()) {
            assertNotNull(manifest.findZipEntry(zip, entry));
        }
    }
}
