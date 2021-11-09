package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipFixtures {

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
        Path path = Files.createTempFile(fixturesDirectory(), "zip", ".zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()));
        Path namePrefix = Paths.get("directory");
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = namePrefix.resolve(source.relativize(dir)) + "/";
                zos.putNextEntry(new ZipEntry(name));
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = namePrefix.resolve(source.relativize(file)).toString();
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(file, zos);
                zos.closeEntry();
                return super.visitFile(file, attrs);
            }
        });
        zos.close();
        return new ZipFile(path.toFile());
    }

    public static void deleteFixtures() throws IOException {
        Path path = fixturesDirectory();
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.equals(path)) {
                    Files.delete(file);
                }
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return super.postVisitDirectory(dir, exc);
            }
        });
    }

    public static Path fixturesDirectory() {
        URL url = ZipFixtures.class.getProtectionDomain().getCodeSource().getLocation();
        URI uri = toUri(url);
        return Paths.get(uri).getParent().resolve("fixtures");
    }

    public static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Path copyFixture(String prefix) throws IOException {
        return copyFixture(prefix, "/fixtures/exampleZipContents");
    }

        /**
         * Copy a fixture contained in a resources folder to a temp directory.  This can then be manpulated to create the
         * scenario being tested and then zipped ready for a test.
         */
    public static Path copyFixture(String prefix, String zipContentPath) throws IOException {
        Path path = fixturesDirectory();
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
        Path directory = Files.createTempDirectory(path, prefix);
        LOG.info("copyFixtureToTmpDirectory path is {}", directory);
        URI source = toUri(ZipFixtures.class.getResource(zipContentPath));
        copyRecursively(Paths.get(source), directory);
        return directory;
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)));
                return super.visitFile(file, attrs);
            }
        });
    }

    static ZipFile zip(String name) throws IOException {
        URL resource = ZipFixtures.class.getResource("/" + name + ".zip");
        File file = new File(toUri(resource));
        return new ZipFile(file);
    }

}
