package scot.gov.publications.manifest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * Parse a Manifest object from an imput stream.
 *
 * The manifest file is a text file where each line consists of a filename and its title separated by a colon.
 *
 * The order of the entries denotes what order the documents should apear in the publicaiton with the first one
 * used as the hero image.
 */
public class ManifestParser {

    public Manifest parse(InputStream inputStream) throws ManifestParserException {
        if (inputStream == null) {
            throw new ManifestParserException("Input stream is null");
        }

        try {
            return doParse(inputStream);
        } catch (IOException e) {
            throw new ManifestParserException("Failed to read manifest", e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private Manifest doParse(InputStream inputStream) throws IOException {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream));
        String line;
        Manifest manifest = new Manifest();
        while ((line = reader.readLine()) != null) {
            manifest.getEntries().add(entry(line));
        }
        return manifest;
    }

    private ManifestEntry entry(String line) {
        String filename = StringUtils.substringBefore(line, ":").trim();
        String title = StringUtils.substringAfter(line, ":").trim();
        return new ManifestEntry(filename, title);
    }
}
