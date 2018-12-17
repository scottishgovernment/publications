package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.metadata.Metadata;

import java.time.LocalDate;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

/**
 * Strategy for determining the path that should be used in the JCR repo for a given publication metedata.
 *
 * This is used when creating new publicaitons only.
 *
 **/
public class PublicationPathStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationPathStrategy.class);

    // xpath template used to find if a publication with this title already exists.
    private static final String XPATH_TEMPLATE =
            "/jcr:root/content/documents/govscot/publications" +
            "//element(%s, hippostd:folder)/element(*, hippo:handle)/element(*, govscot:SimpleContent)";

    Session session;

    HippoUtils hippoUtils;

    HippoPaths hippoPaths;

    public PublicationPathStrategy(Session session) {
        this.session = session;
        this.hippoPaths = new HippoPaths(session);
        this.hippoPaths = new HippoPaths(session);
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
        String disambiguatedTitle = disambiguatedTitle(sanitizedTitle);

        return asList(
                "Publications",
                defaultIfBlank(metadata.getPublicationType(), "Publication"),
                yearString,
                monthString,
                disambiguatedTitle);
    }

    private String disambiguatedTitle(String title) throws RepositoryException {
        if (!titleAlreadyExists(title)) {
            return title;
        }
        return disambiguatedTitle(title, 2);
    }

    private String disambiguatedTitle(String title, int i) throws RepositoryException {
        String candidateTitle = String.format("%s %d", title, i);
        if (!titleAlreadyExists(candidateTitle)) {
            return candidateTitle;
        }

        // that title already exists - disambiguate it by adding a number at the end.
        // we could also use the ibn but :shrug:
        return disambiguatedTitle(title, i + 1);
    }

    private boolean titleAlreadyExists(String title) throws RepositoryException {
        String slug = hippoPaths.slugify(title);
        String xpath = String.format(XPATH_TEMPLATE, slug);
        Query query = session.getWorkspace().getQueryManager().createQuery(xpath, Query.XPATH);
        QueryResult result = query.execute();
        boolean exists = result.getNodes().getSize() != 0;
        if (exists) {
            LOG.info("The title \"{}\" is already used by another publication", title);
        }
        return exists;
    }
}
