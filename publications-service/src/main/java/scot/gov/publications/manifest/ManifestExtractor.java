package scot.gov.publications.manifest;

import scot.gov.publications.ApsZipImporterException;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static scot.gov.publications.util.ZipUtil.getDirname;

public class ManifestExtractor {

    ManifestParser manifestParser = new ManifestParser();

    public Manifest extract(ZipFile zipFile) throws ApsZipImporterException {
        ZipEntry entry = zipFile.getEntry(getDirname(zipFile) + "manifest.txt");
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
