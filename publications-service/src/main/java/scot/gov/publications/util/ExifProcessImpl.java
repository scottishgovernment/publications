package scot.gov.publications.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

public class ExifProcessImpl implements Exif {

    private static final Logger LOG = LoggerFactory.getLogger(ExifProcessImpl.class);


    public long pageCount(Binary binary, String mimeType) {

        if (FileType.forMimeType(mimeType) != FileType.PDF) {
            return 0;
        }

        // write the pdf as a temporary file
        File tmp = null;
        try {
            tmp = writeBinaryToTempFile(binary);
            List<String> exifOutput = runExiftool(tmp);
            return extractPageCount(exifOutput);
        } catch (IOException | RepositoryException | InterruptedException e ) {
            LOG.warn("Failed to get page count", e);
        } finally {
            FileUtils.deleteQuietly(tmp);
        }

        return 0;
    }

    private static File writeBinaryToTempFile(Binary binary) throws IOException, RepositoryException {
        File file = File.createTempFile("exit-tmp-", "tmp");
        FileOutputStream out = new FileOutputStream(file);
        IOUtils.copy(binary.getStream(), out);
        IOUtils.closeQuietly(out);
        return file;
    }

    private static List<String> runExiftool(File file) throws IOException, InterruptedException {
        // could block indefinitely because it doesn't read stderr
        ProcessBuilder builder =
                new ProcessBuilder("exiftool",
                        "-s2",
                        "-d",
                        "%FT%T%z",
                        "-PageCount",
                        "-ModifyDate",
                        file.getAbsolutePath());

        Process process = builder.start();
        List<String> output = IOUtils.readLines(process.getInputStream(), UTF_8);

        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("returned code from the process is " + code);
        }
        return output;
    }

    static long extractPageCount(List<String> output) {
        Map<String, String> map = output.stream()
                .map(line -> line.split(": "))
                .filter(splits -> splits.length == 2)
                .collect(toMap(s -> s[0], s -> s[1]));

        if (map.containsKey("PageCount")) {
            String pageCount = map.get("PageCount");
            try {
                return Integer.parseInt(pageCount);
            } catch (NumberFormatException e) {
                LOG.info("Invalid page count {}", pageCount, e);
            }
        }
        return 0;
    }

}