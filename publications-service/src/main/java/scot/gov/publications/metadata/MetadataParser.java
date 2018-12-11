package scot.gov.publications.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Parser for metadata file contained in an aps zip
 */
public class MetadataParser {

    public Metadata parse(InputStream in) throws MetadataParserException {
        try {
            Metadata metadata = doParse(in);
            assertRequiredFields(metadata);
            calculateZonedPublicationDatetime(metadata);
            return metadata;
        } catch (IOException e) {
            throw new MetadataParserException("Failed to parse metadata", e);
        }
    }

    private Metadata doParse(InputStream in) throws IOException {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Wrapper wrapper = om.readValue(in, Wrapper.class);
        return wrapper.getMetadata();
    }

    private void assertRequiredFields(Metadata metadata) throws MetadataParserException {
        // assert that we have required fields
        if (isBlank(metadata.getId())) {
            throw new MetadataParserException("Missing required field: id");
        }

        if (isBlank(metadata.getIsbn())) {
            throw new MetadataParserException("Missing required field: isbn");
        }

        if (isBlank(metadata.getPublicationType())) {
            throw new MetadataParserException("Missing required field: publicationType");
        }

        if (isBlank(metadata.getTitle())) {
            throw new MetadataParserException("Missing required field: title");
        }

        if (metadata.getPublicationDate() == null) {
            throw new MetadataParserException("Missing required field: publicationDate");
        }
    }

    private void calculateZonedPublicationDatetime(Metadata metadata) {
        // the publication date contained in the metadata is specified without a timezone.
        // To ensure it is published at the right time we convert this to the right timezone.
        TimeZone timezone = TimeZone.getTimeZone("Europe/London");
        ZonedDateTime zonedDateTime = metadata.getPublicationDate().atZone(timezone.toZoneId());
        metadata.setPublicationDateWithTimezone(zonedDateTime);
    }
}
