package scot.gov.publications.util;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TempFileUtil {

    private TempFileUtil() {
        // static only class
    }

    public static File createTempFile(String prefix, FileType type) throws IOException {
        return createTempFile(prefix, type.getExtension());
    }

    public static File createTempFile(String prefix, String extension) throws IOException {
        return File.createTempFile(prefix + "-tmp-", "." + extension);
    }

    public static File createTempFile(String prefix, FileType type, InputStream content) throws IOException {
        return createTempFile(prefix, type.getExtension(), content);
    }

    public static File createTempFile(String prefix, String extenstion, InputStream content) throws IOException {
        File file = createTempFile(prefix, extenstion);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            IOUtils.copy(content, stream);
            return file;
        } finally {
            IOUtils.closeQuietly(content);
            IOUtils.closeQuietly(stream);
        }
    }
}
