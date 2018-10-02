package scot.gov.publications.hippo;

import scot.gov.publications.metadata.Metadata;

import javax.jcr.RepositoryException;
import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

public class PublicationPathStrategy {

    public List<String> path(Metadata metadata) throws RepositoryException {
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
