package scot.gov.publications.hippo;

import org.apache.commons.io.IOUtils;
import scot.gov.publications.util.TempFileUtil;

import java.io.*;
import java.util.zip.ZipFile;

public class ZipFixtures {

    public static ZipFile exampleZip() throws IOException {
        return zip("examplezip");
    }

    public static ZipFile zipWithTwoMetadataFiles() throws IOException {
        return zip("zipWithTwoMetadataFiles");
    }

    public static ZipFile zipWithNoManifest() throws IOException {
        return zip("zipWithNoManifest");
    }

    public static ZipFile zipWithNoMetadata() throws IOException {
        return zip("zipWithNoMetadata");
    }

    public static ZipFile zipWithInvalidMetadata() throws IOException {
        return zip("zipWithInvalidMetadata");
    }

    public static ZipFile zip(String name) throws IOException {
        File exampleFile = TempFileUtil.createTempFile(name, "zip");
        InputStream in = ImageUploaderTest.class.getResourceAsStream("/" + name + ".zip");
        OutputStream out = new FileOutputStream(exampleFile);
        IOUtils.copy(in, out);
        return new ZipFile(exampleFile);
    }
}
