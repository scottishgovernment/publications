package scot.gov.publications.metadata;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetadataParserTest {

    @Test
    public void canParseMetadata() throws Exception {

        // ARRANGE
        InputStream in = new ByteArrayInputStream(metadata("example").getBytes());
        MetadataParser sut = new MetadataParser();

        // ACT
        Metadata actual = sut.parse(in);

        // ASSERT
        assertEquals(actual.getId(), "5131");
        assertEquals(actual.getPublicationDate(), LocalDateTime.of(2018, 9, 11, 9, 0, 0));
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfIdIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("example").replaceAll("5131", "\"\"");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfIsbnIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("example").replaceAll("9781787811980", "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationTypeIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("example").replaceAll("Publication", "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationTitleIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("example").replaceAll(
                "this is the title",
                "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationDateIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("example").replaceAll("2018-09-11T09:00:00", "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void ioExceptionThrowsAsMetadataParserException() throws Exception {

        // ARRANGE
        InputStream in = exceptionThowingInputStream();
        MetadataParser sut = new MetadataParser();

        // ACT
        sut.parse(in);

        // ASSERT --  expected exception
    }

    @Test
    public void zonedPublishedDatIsExpectedInSummer() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("summerExample");
        ZonedDateTime expected = ZonedDateTime.of(
                LocalDate.of(2018, 6, 1),
                LocalTime.of(9, 0),
                ZoneId.of("Europe/London"));

        // ACT
        ZonedDateTime actual = sut.parse(new ByteArrayInputStream(input.getBytes())).getPublicationDateWithTimezone();

        // ASSERT
        assertEquals(expected, actual);
        assertEquals(actual.getOffset().getTotalSeconds(), 60 * 60);
    }

    @Test
    public void zonedPublishedDatIsExpectedInWinter() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata("winterExample");
        ZonedDateTime expected = ZonedDateTime.of(
                LocalDate.of(2018, 1, 1),
                LocalTime.of(9, 0),
                ZoneId.of("Europe/London"));

        // ACT
        ZonedDateTime actual = sut.parse(new ByteArrayInputStream(input.getBytes())).getPublicationDateWithTimezone();

        // ASSERT
        assertEquals(expected, actual);
        assertEquals(actual.getOffset().getTotalSeconds(), 0);
    }


    InputStream exceptionThowingInputStream() throws IOException {
        InputStream in = mock(InputStream.class);
        when(in.read(any(byte[].class))).thenThrow(new IOException("arg"));
        return in;
    }

    String metadata(String name) throws IOException {
        InputStream inputStream = MetadataParserTest.class.getResourceAsStream("/metadata/" + name + ".json");
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }

}
