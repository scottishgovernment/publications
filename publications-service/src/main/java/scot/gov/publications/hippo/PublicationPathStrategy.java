package scot.gov.publications.hippo;

import scot.gov.publications.metadata.Metadata;

import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Strategy for determining the path that should be used in the JCR repo for a given publication metedata.
 *
 * This is used when creating new publicaitons only.
 *
 **/
public class PublicationPathStrategy {

    Session session;

    HippoUtils hippoUtils;

    HippoPaths hippoPaths;

    SlugAllocationStrategy slugAllocationStrategy;

    public PublicationPathStrategy(Session session) {
        this.session = session;
        this.hippoPaths = new HippoPaths(session);
        this.hippoPaths = new HippoPaths(session);
        this.slugAllocationStrategy = new SlugAllocationStrategy(session);
    }

    /**
     * Return the path to use for a set of publication metadata.
     * Path will be Publications/<publication type>/<Publication Year>/<Publication Month>/<Publications title>
     *
     * This class will ensure that the path used will not cause a URL clash with another publication.  It does this
     * by querying for other publications with the same title.  If a clash occors a number will be added to the end of
     * the title (starting with 2) until a usable title is found.
     *
     * @param metadata Metadata for a publication
     * @return A list containing the required path elements for this metadata
     */
    public List<String> path(Metadata metadata) throws RepositoryException {
        LocalDate pubDate = metadata.getPublicationDate().toLocalDate();
        String yearString = Integer.toString(pubDate.getYear());
        String monthString = String.format("%02d", pubDate.getMonthValue());
        String sanitizedTitle = Sanitiser.sanitise(metadata.getTitle());
        String slug = slugAllocationStrategy.allocate(sanitizedTitle);

        return asList(
                "Publications",
                defaultIfBlank(metadata.getPublicationType(), "Publication"),
                yearString,
                monthString,
                slug);
    }
}
