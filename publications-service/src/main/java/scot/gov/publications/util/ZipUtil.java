package scot.gov.publications.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

public class ZipUtil {

    FileUtil fileUtil = new FileUtil();

    public String getDirname(ZipFile zipFile) {
        ZipEntry firstEntry = zipFile.entries().nextElement();
        return firstEntry.getName();
    }

    public File extractNestedZipFile(File file) throws IOException {
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
        return fileUtil.createTempFile("extractedFromZip", "zip", zipFile.getInputStream(entry));
    }
}
