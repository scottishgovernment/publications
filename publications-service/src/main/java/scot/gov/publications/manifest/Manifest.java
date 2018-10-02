package scot.gov.publications.manifest;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Parsed version of the manifest file.
 */
public class Manifest {

    private final List<ManifestEntry> entries = new ArrayList<>();

    public List<ManifestEntry> getEntries() {
        return entries;
    }

    public ZipEntry findZipEntry(ZipFile zipFile, ManifestEntry manifestEntry) {

        List<ZipEntry> zipEntries = zipFile.stream()
                .filter(zipEntry -> isManifestEntry(zipEntry, manifestEntry))
                .collect(toList());

        if (zipEntries.isEmpty()) {
            return null;
        }

        return zipEntries.get(0);
    }

    private boolean isManifestEntry(ZipEntry zipEntry, ManifestEntry manifestEntry) {
        String filename = StringUtils.substringAfterLast(zipEntry.getName(), "/");
        return manifestEntry.getFilename().equals(filename);
    }

}
