package scot.gov.publications.manifest;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ManifestParserTest {

    @Test
    public void canParseExampleManifests() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = new ByteArrayInputStream(
                (
                 "filename.pdf : tis is the filename\n" +
                 "filename2.doc"
                ).getBytes());

        // ACT
        Manifest actual = sut.parse(in);

        //ASSERT
        assertEquals(actual.getEntries().size(), 2);
        assertEquals(actual.getEntries().get(0).getFilename(), "filename.pdf");
        assertEquals(actual.getEntries().get(0).getTitle(), "tis is the filename");
        assertEquals(actual.getEntries().get(1).getFilename(), "filename2.doc");
        assertEquals(actual.getEntries().get(1).getTitle(), "");
    }

    @Test
    public void canParseEmptyManifest() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = new ByteArrayInputStream(new byte[] {});

        // ACT
        Manifest actual = sut.parse(in);

        //ASSERT
        assertEquals(actual.getEntries().size(), 0);
    }

    @Test
    public void canParseManifestWhenTitleHasColon() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = new ByteArrayInputStream("filename.pdf : title contains a colon: and a subtitle\n".getBytes());

        // ACT
        Manifest actual = sut.parse(in);

        //ASSERT
        assertEquals(actual.getEntries().size(), 1);
        assertEquals(actual.getEntries().get(0).getFilename(), "filename.pdf");
        assertEquals(actual.getEntries().get(0).getTitle(), "title contains a colon: and a subtitle");
    }

    @Test
    public void ignoresBlankLines() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = new ByteArrayInputStream("filename.pdf : title\n\nfilename2.pdf: title2".getBytes());

        // ACT
        Manifest actual = sut.parse(in);

        //ASSERT
        assertEquals(actual.getEntries().size(), 2);
        assertEquals(actual.getEntries().get(0).getFilename(), "filename.pdf");
        assertEquals(actual.getEntries().get(0).getTitle(), "title");
        assertEquals(actual.getEntries().get(1).getFilename(), "filename2.pdf");
        assertEquals(actual.getEntries().get(1).getTitle(), "title2");
    }

    @Test(expected = ManifestParserException.class)
    public void exceptionThrownIfInputStreamIsNull() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = null;

        // ACT
        sut.parse(in);


        //ASSERT -- see expected exception
    }

    @Test(expected = ManifestParserException.class)
    public void exceptionThrownIfIOExceptionIsThrown() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = exceptionThowingInputStream();

        // ACT
        sut.parse(in);

        //ASSERT -- see expected exception
    }

    @Test(expected = ManifestParserException.class)
    public void exceptionThrownIfThereAreDuplicateFilenamesInTheManifest() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        List<String> lines = new ArrayList<>();
        Collections.addAll(lines,
                "filename.pdf : title contains a colon: and a subtitle",
                "filename2.pdf : title contains a colon: and a subtitle",
                "filename.pdf : title contains a colon: and a subtitle"
                );
        InputStream in = new ByteArrayInputStream(lines.stream().collect(Collectors.joining("\n\n")).getBytes());

        // ACT
        Manifest actual = sut.parse(in);

        //ASSERT -- see expected exception
    }

    @Test(expected = ManifestParserException.class)
    public void rejectsUnrecognisedFileExtensions() throws Exception {
        // ARRANGE
        ManifestParser sut = new ManifestParser();
        InputStream in = new ByteArrayInputStream(
                (
                        "filename.pdf : tis is the filename\n" +
                        "filename2.zip : unsupported zip"
                ).getBytes());


        // ACT
        sut.parse(in);

        // ASSERT -- see expected exception
    }

    InputStream exceptionThowingInputStream() throws IOException {
        InputStream in = mock(InputStream.class);
        when(in.read(any(byte[].class))).thenThrow(new IOException("arg"));
        return in;
    }

}
