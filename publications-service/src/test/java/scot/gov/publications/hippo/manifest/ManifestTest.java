package scot.gov.publications.hippo.manifest;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.hippo.ZipFixtures;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestEntry;

import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

public class ManifestTest {


    @Test
    public void canFindManifestEntry() throws Exception {

        // ARRANGE
        ZipFile zipFile = ZipFixtures.exampleZip();
        ManifestEntry entry = new ManifestEntry("SCT04185156361.pdf", "Social Security Scotland Digital and Technology Strategy - Chief Digital Officer Division (Social Security)");

        // ACT
        ZipEntry actual = new Manifest().findZipEntry(zipFile, entry);

        // ASSERT
        Assert.assertNotNull(actual);
    }

    @Test
    public void returnsNullForUnfoundEntry() throws Exception {

        // ARRANGE
        ZipFile zipFile = ZipFixtures.exampleZip();
        ManifestEntry entry = new ManifestEntry("unfound", "");

        // ACT
        ZipEntry actual = new Manifest().findZipEntry(zipFile, entry);

        // ASSERT
        Assert.assertNull(actual);
    }

//    public ZipEntry findZipEntry(ZipFile zipFile, ManifestEntry manifestEntry) {
//
//        List<ZipEntry> zipEntries = zipFile.stream()
//                .filter(zipEntry -> isManifestEntry(zipEntry, manifestEntry))
//                .collect(toList());
//
//        if (zipEntries.isEmpty()) {
//            return null;
//        }
//
//        return zipEntries.get(0);
//    }
//
//    private boolean isManifestEntry(ZipEntry zipEntry, ManifestEntry manifestEntry) {
//        String filename = StringUtils.substringAfterLast(zipEntry.getName(), "/");
//        return manifestEntry.getFilename().equals(filename);
//    }

}
