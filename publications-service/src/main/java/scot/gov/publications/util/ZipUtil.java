package scot.gov.publications.util;

import org.apache.commons.io.FileUtils;
import scot.gov.publications.rest.UploadRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

public class ZipUtil {

    private ZipUtil() {
        // prevent instantiation
    }

    public static String getDirname(ZipFile zipFile) {
        ZipEntry firstEntry = zipFile.entries().nextElement();
        return firstEntry.getName();
    }

    /**
     * Extract nested ZIP file.
     *
     * The caller should delete the returned temporary file.
     */
    public static File extractNestedZipFile(UploadRequest fileUpload) throws IOException {
        // save the data as a temp file...
        File file = TempFileUtil.createTempFile("zipUpload", "zip", new ByteArrayInputStream(fileUpload.getFileData()));
        try {
            return extractNestedZipFile(file);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    public static File extractNestedZipFile(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        List<ZipEntry> zipEntries = zipFile.stream()
                .filter(ZipEntryUtil::isZip)
                .filter(entry -> !entry.getName().startsWith("__MACOSX/"))
                .collect(toList());
        if (zipEntries.isEmpty()) {
            throw new IllegalArgumentException("No zip in the zip!");
        }
        if (zipEntries.size() > 1) {
            throw new IllegalArgumentException("More than one zip in the zip!");
        }

        ZipEntry entry = zipEntries.get(0);
        return TempFileUtil.createTempFile("extractedFromZip", "zip", zipFile.getInputStream(entry));
    }
}
