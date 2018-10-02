package scot.gov.publications.util;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtil {

    private ZipUtil() {
        // prevent instantiation
    }

    public static String getDirname(ZipFile zipFile) {
        ZipEntry firstEntry = zipFile.entries().nextElement();
        return firstEntry.getName();
    }
}
