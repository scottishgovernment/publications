package scot.gov.publications.metadata;

import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.MetadataParser;
import scot.gov.publications.metadata.MetadataParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetadataParserTest {

    String metadata = "{\n" +
            "  \"metadata\": {\n" +
            "    \"id\": 5131,\n" +
            "    \"wpid\": null,\n" +
            "    \"title\": \"this is the title\",\n" +
            "    \"publicationDate\": \"2018-09-11T09:00:00\",\n" +
            "    \"url\": null,\n" +
            "    \"alternateUrl\": \"\",\n" +
            "    \"executiveSummary\": \"This is a summary of the full Equalities Impact Assessment conducted on the Best Start Grant to accompany The Early Years Assistance (Best Start Grants) (Scotland) Regulations 2018.The Scottish Government is committed to replacing the UK Government's Sure Start Materntiy Grant with the Best Start Grant, a new, expanded benefit to provide financial support to lower income families during a child's early years.\",\n" +
            "    \"description\": \"Equality Impact Assessment considering the potential effects of the Best Start Grant and how it impacts on people with one or more protected characteristics.\",\n" +
            "    \"isbn\": \"9781787811980\",\n" +
            "    \"topic\": \"People and Society\",\n" +
            "    \"publicationType\": \"Publication\",\n" +
            "    \"keywords\": \"Social Security; Best Start Grants; Equality Impact Assessment; Early Years Assistance; Regulations\",\n" +
            "    \"researchCategory\": \"\",\n" +
            "    \"statisticsCategory\": \"\",\n" +
            "    \"equalityInfo\": {\n" +
            "      \"gender\": true,\n" +
            "      \"age\": true,\n" +
            "      \"disability\": true,\n" +
            "      \"religion\": true,\n" +
            "      \"ethnicity\": true,\n" +
            "      \"orientation\": true\n" +
            "    },\n" +
            "    \"links\": []\n" +
            "  }\n" +
            "}";

    @Test
    public void canParseMetadata() throws Exception {

        // ARRANGE
        InputStream in = new ByteArrayInputStream(metadata.getBytes());
        MetadataParser sut = new MetadataParser();

        // ACT
        Metadata actual = sut.parse(in);

        // ASSERT
        Assert.assertEquals(actual.getId(), "5131");
        Assert.assertEquals(actual.getPublicationDate(), LocalDateTime.of(2018, 9, 11, 9, 0, 0));
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfIdIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata.replaceAll("5131", "\"\"");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfIsbnIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata.replaceAll("9781787811980", "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationTypeIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata.replaceAll("Publication", "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationTitleIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata.replaceAll(
                "this is the title",
                "");

        // ACT
        sut.parse(new ByteArrayInputStream(input.getBytes()));

        // ASSERT -- expected exception
    }

    @Test(expected = MetadataParserException.class)
    public void exceptionThrowsIfPublicationDateIsBlank() throws Exception {
        MetadataParser sut = new MetadataParser();
        String input = metadata.replaceAll("2018-09-11T09:00:00", "");

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

    InputStream exceptionThowingInputStream() throws IOException {
        InputStream in = mock(InputStream.class);
        when(in.read(any(byte[].class))).thenThrow(new IOException("arg"));
        return in;
    }

}
