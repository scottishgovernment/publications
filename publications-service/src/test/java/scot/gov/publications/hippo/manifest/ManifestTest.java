package scot.gov.publications.hippo.manifest;

import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.hippo.ZipFixtures;
import scot.gov.publications.manifest.Manifest;
import scot.gov.publications.manifest.ManifestEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

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


    @Test
    public void assignsExpectedFriendlyFilenames() {
        // ARRANGE
        Manifest manifest = new Manifest();
        Collections.addAll(manifest.getEntries(),
                new ManifestEntry("SCT04185156361.pdf", "The most important document in the world"),
                new ManifestEntry("SCT04185156362.pdf", "The most important document in the world"),
                new ManifestEntry("SCT04185156363.doc", "The most important document in the world"),
                new ManifestEntry("SCT04185156364.doc", "The most important document in the world"));
        manifest.assignFriendlyFilenames();
        List<String> expected= new ArrayList<>();
        Collections.addAll(expected,

                // stopword have been applied and the extension added
                "important-document-world.pdf",

                // as above but with -1 to disambiguate
                "important-document-world-1.pdf",

                // indexing should not be here, the extension distoinguishes it from the pdf
                "important-document-world.doc",

                // same as prior one but with an index added
                "important-document-world-1.doc");

        // ACT
        manifest.assignFriendlyFilenames();
        List<String> actual = manifest.getEntries().stream().map(ManifestEntry::getFriendlyFilename).collect(toList());

        // ASSERT
        assertEquals(expected, actual);
    }
}
