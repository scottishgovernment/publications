package scot.gov.publications.hippo;

import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import static scot.gov.publications.hippo.Constants.GOVSCOT_GOVSCOTURL;
import static scot.gov.publications.hippo.Constants.GOVSCOT_TITLE;

public class PublicationNodeUpdater {

    Session session;

    HippoPaths hippoPaths;

    HippoNodeFactory nodeFactory;

    TopicMappings topicMappings;

    PublicationPathStrategy pathStrategy = new PublicationPathStrategy();

    HippoUtils hippoUtils = new HippoUtils();

    public PublicationNodeUpdater(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.hippoPaths = new HippoPaths(session);
        this.nodeFactory = new HippoNodeFactory(session, configuration);
        this.topicMappings = new TopicMappings(session);
    }

    /**
     * Ensure that a publications node exists containin the data contained in the metadata.
     *
     * @return Node representing the folder the publiation is contained in.
     */
    public Node createOrpdatePublicationNode(Metadata metadata) throws ApsZipImporterException {

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

            // TODO: we seem to be missing this from the metadata ... have asked Jon to add, waiting on GDPR issue being resolved
            //            if (publication.getContact() != null) {
            //                hippoUtils.addHtmlNodeIfAbsent(node, "govscot:contact", metadata.getContact().asHtml());
            //            }
            hippoUtils.setPropertyStringsIfAbsent(node, "hippostd:tags", Collections.emptyList());
            topicMappings.updateTopics(node, metadata.getTopic());

            // always set these properties
            node.setProperty("govscot:publicationType", hippoPaths.slugify(metadata.getPublicationType()));
            node.setProperty("govscot:isbn", metadata.normalisedIsbn());

            node.setProperty(GOVSCOT_GOVSCOTURL, metadata.getUrl());
            node.setProperty("govscot:publicationDate", toCalendar(metadata.getPublicationDate()));

            Node handle = node.getParent();
            // return the folder
            return handle.getParent();

        } catch (RepositoryException e) {
            throw new ApsZipImporterException("Failed to create or update publicaiton node", e);
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
                    metadata.getPublicationDate());
        }
        return pubNode;
    }

    private Node findPublishedNode(Metadata metadata) throws RepositoryException {
        // TODO escape isbn
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
