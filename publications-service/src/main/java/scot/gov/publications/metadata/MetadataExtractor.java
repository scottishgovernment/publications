package scot.gov.publications.metadata;

import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.util.ZipEntryUtil;
import scot.gov.publications.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

/**
 * Extracts and parser the metadata file from a zipfile.
 *
 * The extactor works by expecting a single json file in the zip and then parsing it.  It will throw and exception
 * if there is not exactly one json file in the zip.
 */
public class MetadataExtractor {

    private MetadataParser metadataParser = new MetadataParser();

    private ZipUtil zipUtil = new ZipUtil();

    public Metadata extract(File file) throws ApsZipImporterException {

        try {
            ZipFile zipFile = new ZipFile(file);
            return extract(zipFile);
        } catch (IOException e) {
            throw new ApsZipImporterException("Failed to create zip file", e);

        }
    }

    public Metadata extract(ZipFile zipFile) throws ApsZipImporterException {

        String dir = zipUtil.getDirname(zipFile);
        List<ZipEntry> jsonEntries = zipFile.stream()
                .filter(e -> e.getName().startsWith(dir))
                .filter(ZipEntryUtil::isJson)
                .collect(toList());

        if (jsonEntries.isEmpty()) {
            throw new ApsZipImporterException("No metadata file in zip");
        }

        if (jsonEntries.size() > 1) {
            throw new ApsZipImporterException("More than one JSON file in zip, unable to indentify metadata file");
        }

        try {
            return metadataParser.parse(zipFile.getInputStream(jsonEntries.get(0)));
        } catch (MetadataParserException e) {
            throw new ApsZipImporterException("Unable to parse metadata file", e);
        } catch (IOException e) {
            throw new ApsZipImporterException("Unable to read metadata file", e);
        }
    }
}
