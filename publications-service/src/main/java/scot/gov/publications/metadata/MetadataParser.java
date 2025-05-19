package scot.gov.publications.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
            assertValidFields(metadata);
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
        MetadataWrapper wrapper = om.readValue(in, MetadataWrapper.class);
        return wrapper.getMetadata();
    }

    private void assertRequiredFields(Metadata metadata) throws MetadataParserException {

        List<String> missingFields = new ArrayList<>();

        if (isBlank(metadata.getId())) {
            missingFields.add("id");
        }

        if (isBlank(metadata.getIsbn())) {
            missingFields.add("isbn");
        }

        if (isBlank(metadata.getPublicationType())) {
            missingFields.add("publicationType");
        }

        if (isBlank(metadata.getTitle())) {
            missingFields.add("title");
        }

        if (metadata.getPublicationDate() == null) {
            missingFields.add("publicationDate");
        }

        if (StringUtils.equals(metadata.getPublicationType(), "consultation-paper")) {
            // if the type is consultation paper, and the JSON contains a 'consultation' element then
            // assert that there has to be: openingDate, closingDate, responseUrl
            assertRequiredConsultationFields(metadata, missingFields);
        }

        if (!missingFields.isEmpty()) {
            String missignFieldsString = String.join(", ", missingFields);
            throw new MetadataParserException("Metadata missing required field(s): " + missignFieldsString);
        }
    }

    void assertRequiredConsultationFields(Metadata metadata, List<String> missingFields) {
        if (metadata.getConsultation() == null) {
            return;
        }
        Consultation consultation = metadata.getConsultation();
        if (consultation.getOpeningDate() == null) {
            missingFields.add("consultation.openingDate");
        }
        if (consultation.getClosingDate() == null) {
            missingFields.add("consultation.closingDate");
        }
    }

    private void assertValidFields(Metadata metadata) throws MetadataParserException {
        // the isbn should only contain letters, numbers and hyphens
        if (!validISBN(metadata.normalisedIsbn())) {
            throw new MetadataParserException("Invalid field: isbn = " + metadata.normalisedIsbn());
        }
    }

    private void calculateZonedPublicationDatetime(Metadata metadata) {
        // the publication date contained in the metadata is specified without a timezone.
        // To ensure it is published at the right time we convert this to the right timezone.
        TimeZone timezone = TimeZone.getTimeZone("Europe/London");
        ZonedDateTime zonedDateTime = metadata.getPublicationDate().atZone(timezone.toZoneId());
        metadata.setPublicationDateWithTimezone(zonedDateTime);
    }

    private boolean validISBN(String isbn) {
        // a valid isbn should only contain numbers and hyphens.  However, we sometimes get isbn's from aps that
        // contain postfixes like -resonses and so we just want assert that the isbn only contains letters, number
        // and hyphens.
        return isbn.matches("^[a-zA-Z0-9\\-]+$");
    }

}
