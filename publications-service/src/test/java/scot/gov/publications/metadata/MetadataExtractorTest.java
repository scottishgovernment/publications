package scot.gov.publications.metadata;

import org.junit.Test;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.hippo.ZipFixtures;

import static org.junit.Assert.assertNotNull;

public class MetadataExtractorTest {

    @Test
    public void canExtractMetadata() throws Exception {
        MetadataExtractor sut = new MetadataExtractor();
        Metadata metadata = sut.extract(ZipFixtures.exampleZip());
        assertNotNull(metadata);
    }

    @Test(expected = ApsZipImporterException.class)
    public void execptionThrownIfMultipleMetatdataFiles() throws Exception {
        MetadataExtractor sut = new MetadataExtractor();
        sut.extract(ZipFixtures.zipWithTwoMetadataFiles());
    }

    @Test(expected = ApsZipImporterException.class)
    public void execptionThrownIfNoMetatdataFiles() throws Exception {
        MetadataExtractor sut = new MetadataExtractor();
        sut.extract(ZipFixtures.zipWithNoMetadata());
    }

    @Test(expected = ApsZipImporterException.class)
    public void execptionThrownIfInvalidMetatdataFiles() throws Exception {
        MetadataExtractor sut = new MetadataExtractor();
        sut.extract(ZipFixtures.zipWithInvalidMetadata());
    }
}
