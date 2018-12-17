package scot.gov.publications.hippo;

import org.apache.commons.lang.StringUtils;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import static scot.gov.publications.hippo.Constants.GOVSCOT_GOVSCOTURL;
import static scot.gov.publications.hippo.Constants.GOVSCOT_TITLE;

/**
 * Responsible for creating and updating publication nodes in Hippo based on the metedata from an APS  zip file.
 */
public class PublicationNodeUpdater {

    Session session;

    HippoPaths hippoPaths;

    HippoNodeFactory nodeFactory;

    TopicMappings topicMappings;

    PublicationPathStrategy pathStrategy;

    HippoUtils hippoUtils = new HippoUtils();

    public PublicationNodeUpdater(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.hippoPaths = new HippoPaths(session);
        this.nodeFactory = new HippoNodeFactory(session, configuration);
        this.topicMappings = new TopicMappings(session);
        this.pathStrategy = new PublicationPathStrategy(session);
    }

    /**
     * Ensure that a publications node exists containin the data contained in the metadata.
     *
     * @return Node representing the folder the publiation is contained in.
     */
    public Node createOrUpdatePublicationNode(Metadata metadata) throws ApsZipImporterException {

        try {
            Node node = doCreateOrUpdate(metadata);
            String title = Sanitiser.sanitise(metadata.getTitle());
            nodeFactory.addBasicFields(node, metadata.getTitle());

            // these fields are edited by users, do not overwrite them if they already have a value
            hippoUtils.setPropertyIfAbsent(node, GOVSCOT_TITLE, title);
            hippoUtils.setPropertyIfAbsent(node, "govscot:summary", metadata.getDescription());
            hippoUtils.setPropertyIfAbsent(node, "govscot:seoTitle", title);
            hippoUtils.setPropertyIfAbsent(node, "govscot:metaDescription", metadata.getDescription());
            hippoUtils.setPropertyIfAbsent(node, "govscot:notes", "");
            hippoUtils.addHtmlNodeIfAbsent(node, "govscot:content", metadata.getExecutiveSummary());
            // Contact seems to be missing this from the metadata ... have asked Jon to add, waiting on GDPR issue being resolved
            hippoUtils.setPropertyStringsIfAbsent(node, "hippostd:tags", Collections.emptyList());
            topicMappings.updateTopics(node, metadata.getTopic());

            // always set these properties
            node.setProperty("govscot:publicationType", hippoPaths.slugify(metadata.getPublicationType()));
            node.setProperty("govscot:isbn", metadata.normalisedIsbn());
            populateUrls(node, metadata);
            node.setProperty("govscot:publicationDate",
                    GregorianCalendar.from(metadata.getPublicationDateWithTimezone()));

            Node handle = node.getParent();
            // return the folder
            return handle.getParent();

        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to create or update publication node", e);
        }
    }

    private void populateUrls(Node node, Metadata metadata) throws RepositoryException, ApsZipImporterException {
        if (StringUtils.isNotBlank(metadata.getUrl())) {
            node.setProperty(GOVSCOT_GOVSCOTURL, oldStyleUrl(metadata));
        }
    }

    private String oldStyleUrl(Metadata metadata) throws ApsZipImporterException {
        // we only want to store the path of the old style url
        try {
            return new URL(metadata.getUrl()).getPath();
        } catch (MalformedURLException e) {
            throw new ApsZipImporterException("Invalid URL:" + metadata.getUrl(), e);
        }
    }

    private Node doCreateOrUpdate(Metadata metadata) throws RepositoryException {
        Node pubNode = findPublishedNode(metadata);
        if (pubNode == null) {
            List<String> path = pathStrategy.path(metadata);
            Node pubFolder = hippoPaths.ensurePath(path);
            Node handle = nodeFactory.newHandle(metadata.getTitle(), pubFolder, "index");
            pubNode = nodeFactory.newDocumentNode(
                    handle,
                    "index",
                    metadata.getTitle(),
                    "govscot:Publication",
                    metadata.getPublicationDateWithTimezone());
        }
        return pubNode;
    }

    private Node findPublishedNode(Metadata metadata) throws RepositoryException {
        // Query to see if a publications with this ISBN already exist.  If it does then we will update the existing
        // node rather than create a new one
        String sql = String.format(
                "SELECT * FROM govscot:Publication WHERE govscot:isbn = '%s' AND hippostd:state = 'published'",
                metadata.getIsbn());
        return hippoUtils.findOne(session, sql);
    }

    public Calendar toCalendar(LocalDateTime dt) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zdt = dt.atZone(zoneId);
        return GregorianCalendar.from(zdt);
    }
}
