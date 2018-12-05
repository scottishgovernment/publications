package scot.gov.publications.hippo;

import scot.gov.publications.metadata.Metadata;

import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Strategy for determining the path that should be used in the JCR repo for a given publication metedata.
 **/
public class PublicationPathStrategy {

    /**
     * Return the path to use for a set of publication metadata.
     * Path will be Publications/<publication type>/<Publication Year>/<Publication Month>/
     *
     * @param metadata Metadata for a publiction
     * @return A list containing the required path elements fir this metadata
     */
    public List<String> path(Metadata metadata) {
        LocalDate pubDate = metadata.getPublicationDate().toLocalDate();
        String yearString = Integer.toString(pubDate.getYear());
        String monthString = String.format("%02d", pubDate.getMonthValue());
        String title = Sanitiser.sanitise(metadata.getTitle());
        return asList(
                "Publications",
                defaultIfBlank(metadata.getPublicationType(), "Publication"),
                yearString,
                monthString,
                title);
    }

}
