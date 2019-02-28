package scot.gov.publications.hippo;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.repo.Publication;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static scot.gov.publications.hippo.Constants.GOVSCOT_GOVSCOTURL;
import static scot.gov.publications.hippo.Constants.GOVSCOT_TITLE;

/**
 * Responsible for creating and updating publication nodes in Hippo based on the metedata from an APS  zip file.
 */
public class PublicationNodeUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationNodeUpdater.class);

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
     * Ensure that a publications node exists containing the data contained in the metadata.
     *
     * @return Node representing the folder the publication is contained in.
     */
    public Node createOrUpdatePublicationNode(Metadata metadata, Publication publication)
            throws ApsZipImporterException {

        try {
            Node node = doCreateOrUpdate(metadata);
            setPublicationAuditFields(node, publication);
            nodeFactory.addBasicFields(node, metadata.getTitle());

            // these fields are edited by users, do not overwrite them if they already have a value
            hippoUtils.setPropertyIfAbsent(node, GOVSCOT_TITLE, metadata.getTitle());
            hippoUtils.setPropertyIfAbsent(node, "govscot:summary", metadata.getDescription());
            hippoUtils.setPropertyIfAbsent(node, "govscot:seoTitle", metadata.getTitle());
            hippoUtils.setPropertyIfAbsent(node, "govscot:metaDescription", metadata.getDescription());
            hippoUtils.setPropertyIfAbsent(node, "govscot:notes", "");
            hippoUtils.addHtmlNodeIfAbsent(node, "govscot:content", metadata.getExecutiveSummary());
            hippoUtils.setPropertyStringsIfAbsent(node, "hippostd:tags", emptyList());
            topicMappings.updateTopics(node, metadata.getTopic());

            // always set these properties

            // we set the publication type to the slugified version of the publication type but want to ensure that
            // we do not remove stopwords when we do this.
            node.setProperty("govscot:publicationType", hippoPaths.slugify(metadata.getPublicationType(), false));
            node.setProperty("govscot:isbn", metadata.normalisedIsbn());
            populateUrls(node, metadata);
            node.setProperty("govscot:publicationDate", GregorianCalendar.from(metadata.getPublicationDateWithTimezone()));
            hippoUtils.ensureHtmlNode(node, "govscot:contact", mailToLink(publication.getContact()));
            return node.getParent().getParent();

        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to create or update publication node", e);
        }
    }

    private String mailToLink(String email) {
        return String.format("<p>Email: <a href=\"mailto:%s\">Claire McHarrie</a></p>", email);
    }

    /**
     * Add properties from the Publications to allow us to connect the publication in the repo to the publications
     * database.
     */
    private void setPublicationAuditFields(Node node, Publication publication) throws RepositoryException {
        node.setProperty("govscot:publicaitonId", publication.getId());
        node.setProperty("govscot:publicationFilename", publication.getFilename());
        node.setProperty("govscot:publicationUsername", publication.getUsername());
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
        Node pubNode = findPublicationNodeToUpdate(metadata);

        if (pubNode == null) {
            List<String> newPath = pathStrategy.path(metadata);
            Node pubFolder = hippoPaths.ensurePath(newPath);
            pubFolder.setProperty("hippo:name", metadata.getTitle());
            Node handle = nodeFactory.newHandle(metadata.getTitle(), pubFolder, "index");
            pubNode = nodeFactory.newDocumentNode(
                    handle,
                    "index",
                    metadata.getTitle(),
                    "govscot:Publication",
                    metadata.getPublicationDateWithTimezone());
            return pubNode;
        } else {
            // remove any other nodes there are ...
            hippoUtils.removeSiblings(pubNode);

            // the node already exists, make sure its publications status matches what is in the zip
            nodeFactory.ensurePublicationStatus(pubNode, metadata.getPublicationDateWithTimezone());
        }

        return pubNode;
    }

    /**
     * Ensure that this publications folder is in the right place.  The folder is based on its type and publicaiton
     * date and so if either changed since the last time the publicaiton was uploaded then it might have to be moved.
     */
    public void ensureMonthNode(Node publicationFolder, Metadata metadata) throws RepositoryException {
        List<String> monthPath = pathStrategy.monthFolderPath(metadata);
        Node monthNode = hippoPaths.ensurePath(monthPath);
        Node existingMonthNode = publicationFolder.getParent();
        // if the exiting node has a different folder than the new folder then move it into the right folder
        if (!existingMonthNode.getIdentifier().equals(monthNode.getIdentifier())) {
            String newPath = monthNode.getPath() + "/" + publicationFolder.getName();
            LOG.info("moving {} to {}", publicationFolder.getPath(), newPath);
            session.move(publicationFolder.getPath(), newPath);
        }
    }

    /**
     * If a publication with this isbn already exists then we want to update it.  To make sire we update the right
     * node we want to find all of then and then decide which noide to use if there are multiple drafts.  If a
     * published node exists then use that. Then fall back to using the upublished one and then finally to draft.
     */
    private Node findPublicationNodeToUpdate(Metadata metadata) throws RepositoryException {
        // Query to see if a publications with this ISBN already exist.  If it does then we will update the existing
        // node rather than create a new one
        String sql = String.format("SELECT * FROM govscot:Publication WHERE govscot:isbn = '%s'", metadata.getIsbn());
        Query query = session.getWorkspace().getQueryManager().createQuery(sql, Query.SQL);
        QueryResult result = query.execute();
        Map<String, Node> byState = new HashMap<>();
        NodeIterator it = result.getNodes();
        while (it.hasNext()) {
            Node node = it.nextNode();
            String state = node.getProperty("hippostd:state").getString();
            byState.put(state, node);
        }
        return firstNonNull(
                byState.get("published"),
                byState.get("unpublished"),
                byState.get("draft"));
    }

    public Calendar toCalendar(LocalDateTime dt) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zdt = dt.atZone(zoneId);
        return GregorianCalendar.from(zdt);
    }
}
