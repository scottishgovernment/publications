package scot.gov.publications.manifest;

import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.util.ZipUtil;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts Manifest from a ZipFile and parses it using a ManifestParser
 */
public class ManifestExtractor {

    ZipUtil zipUtil = new ZipUtil();

    ManifestParser manifestParser = new ManifestParser();

    /**
     * Extract the manifest from a zip file.
     *
     * @param zipFile A zip file
     * @return The parsed Manifest contained in this zip
     * @throws ApsZipImporterException If the manifest is not present or is not parsable.
     */
    public Manifest extract(ZipFile zipFile) throws ApsZipImporterException {
        ZipEntry entry = zipFile.getEntry(zipUtil.getDirname(zipFile) + "manifest.txt");
        if (entry == null) {
            throw new ApsZipImporterException("No manifest file in zip");
        }
        try {
            return manifestParser.parse(zipFile.getInputStream(entry));
        } catch (ManifestParserException e) {
            throw new ApsZipImporterException("Invalid manifest file", e);
        } catch (IOException e) {
            throw new ApsZipImporterException("Failed to read manifest file", e);
        }
    }
}
