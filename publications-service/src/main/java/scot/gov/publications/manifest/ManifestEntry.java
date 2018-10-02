package scot.gov.publications.manifest;

import org.apache.commons.lang3.StringUtils;

/**
 * An entry in the manifest file contains a filename and its title.
 */
public class ManifestEntry {

    private final String filename;

    private final String title;

    public ManifestEntry(String filename, String title) {
        this.filename = filename;
        this.title = title;
    }

    public String getFilename() {
        return filename;
    }

    public String getTitle() {
        return title;
    }

    public String getTitleOrFilename() {
        return StringUtils.isEmpty(title) ?  getFilename() : title;
    }

}
