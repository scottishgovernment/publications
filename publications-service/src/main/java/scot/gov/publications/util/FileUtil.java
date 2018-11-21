package scot.gov.publications.util;

import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    public File createTempFile(String prefix, FileType type) throws IOException {
        return createTempFile(prefix, type.getExtension());
    }

    public File createTempFile(String prefix, String extension) throws IOException {
        return File.createTempFile(prefix + "-tmp-", "." + extension);
    }

    public File createTempFile(String prefix, FileType type, InputStream content) throws IOException {
        return createTempFile(prefix, type.getExtension(), content);
    }

    public File createTempFile(String prefix, String extenstion, InputStream content) throws IOException {
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

    public String hash(File zip) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(zip);
            return BinaryUtils.toHex(Md5Utils.computeMD5Hash(in));
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
