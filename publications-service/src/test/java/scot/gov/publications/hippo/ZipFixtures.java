package scot.gov.publications.hippo;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.action.GetPropertyAction;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipFile;

import static java.security.AccessController.doPrivileged;
import static org.apache.commons.lang3.StringUtils.startsWith;

public class ZipFixtures {

    private static final SecureRandom random = new SecureRandom();

    private static final Logger LOG = LoggerFactory.getLogger(ZipFixtures.class);

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

    /**
     * Create a zip from a directory
     */
    public static ZipFile zipDirectory(Path source) throws IOException {

        // create a temporary file that will become the zip
        Path zipPath = Files.createTempFile("zipDirectory", ".zip");

        // now zip up the contents of the temp directory
        FileSystem zipfs = zipFileSystem(zipPath);

        // create a sub directory within the zip since the importer expected this structure
        Path directory = zipfs.getPath("directory");
        Files.createDirectory(directory);

        // copy files in the sourceDir into the zip
        for (String file : source.toFile().list()) {
            Files.copy(source.resolve(file), directory.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        zipfs.close();

        // create a zip file from that directory
        return new ZipFile(zipPath.toFile());
    }

    public static void deleteFixtures() {
        File tmpdir = new File(doPrivileged(new GetPropertyAction("java.io.tmpdir")));
        File [] zips = tmpdir.listFiles(file -> startsWith(file.getName(), "zipDirectory"));
        for (File zip : zips) {
            zip.delete();
        }
    }

    private static FileSystem zipFileSystem(Path path) throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        Files.delete(path);
        URI uri = URI.create("jar:file:" + path);
        return FileSystems.newFileSystem(uri, env);
    }

    /**
     * Copy a fixture contained in a resources folder to a temp directory.  This can then be manpulated to create the
     * scenario being tested and then zipped ready for a test.
     */
    public static Path copyFixtureToTmpDirectory(String prefix, String zipContentPath) throws IOException {
        Path tempDirWithPrefix = createTempDirectory(prefix);

        LOG.info("copyFixtureToTmpDirectory path is {}", tempDirWithPrefix);
        InputStream resourceDirStream = ZipFixtures.class.getClassLoader().getResourceAsStream(zipContentPath);
        List<String> filenames = IOUtils.readLines(resourceDirStream, Charsets.UTF_8);

        for (String filename : filenames) {
            String path = "/" + zipContentPath + "/" + filename;
            File dest = tempDirWithPrefix.resolve(filename).toFile();
            InputStream resourceInputStream = ZipFixtures.class.getResourceAsStream(path);
            FileUtils.copyInputStreamToFile(resourceInputStream, dest);
        }

        return tempDirWithPrefix;
    }

    static ZipFile zip(String name) throws IOException {
        File exampleFile = createTempFile(name, "zip");
        InputStream in = ImageUploaderTest.class.getResourceAsStream("/" + name + ".zip");
        OutputStream out = new FileOutputStream(exampleFile);
        IOUtils.copy(in, out);
        return new ZipFile(exampleFile);
    }

    static File createTempFile(String prefix, String extension) throws IOException {
        Path textFixturesDirPath = textFixturesPath();
        textFixturesDirPath.toFile().mkdirs();
        Path path = textFixturesDirPath.resolve(randomFilename(prefix, extension));
        return Files.createFile(path).toFile();
    }

    static Path createTempDirectory(String prefix) {
        String dirname = randomFilename(prefix);
        Path dir = textFixturesPath().resolve(dirname);
        dir.toFile().mkdirs();
        return dir;
    }

    static String randomFilename(String prefix) {
        return String.format("%s%d", prefix, Math.abs(random.nextLong()));
    }

    static String randomFilename(String prefix, String extention) {
        return String.format("%s.%s", randomFilename(prefix), extention);
    }

    static Path textFixturesPath() {
        return Paths.get("target", "tmp", "testfixtures");
    }

}
