package scot.gov.publications.hippo;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.metadata.Consultation;
import scot.gov.publications.metadata.ConsultationResponseMethod;
import scot.gov.publications.metadata.Metadata;
import scot.gov.publications.metadata.Update;
import scot.gov.publications.repo.Publication;
import scot.gov.publishing.sluglookup.SlugLookups;

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
import java.util.*;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.*;
import static scot.gov.publications.hippo.Constants.*;
import static scot.gov.publications.hippo.XpathQueryHelper.*;

/**
 * Responsible for creating and updating publication nodes in Hippo based on the metedata from an APS  zip file.
 */
public class PublicationNodeUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationNodeUpdater.class);

    private static final String RESPONSIBLE_DIRECTORATE = "govscot:responsibleDirectorate";

    private static final String RESPONSIBLE_ROLE = "govscot:responsibleRole";

    private static final String FEATURED_LINKS = "govscot:featuredLinks";

    Session session;

    HippoPaths hippoPaths;

    HippoNodeFactory nodeFactory;

    TopicsUpdater topicMappings;

    PoliciesUpdater policiesUpdater;

    PublicationPathStrategy pathStrategy;

    HippoUtils hippoUtils = new HippoUtils();

    TagUpdater tagUpdater = new TagUpdater();

    Sitemap sitemap;

    SlugLookups slugLookups;

    public PublicationNodeUpdater(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.hippoPaths = new HippoPaths(session);
        this.nodeFactory = new HippoNodeFactory(session, configuration);
        this.topicMappings = new TopicsUpdater(session);
        this.pathStrategy = new PublicationPathStrategy(session);
        this.policiesUpdater = new PoliciesUpdater(session);
        this.sitemap = new Sitemap(session);
        this.slugLookups = new SlugLookups(session);
    }

    /**
     * Ensure that a publications node exists containing the data contained in the metadata.
     *
     * @return Node representing the folder the publication is contained in.
     */
    public Node createOrUpdatePublicationNode(Metadata metadata, Publication publication)
            throws ApsZipImporterException {
        Node publicationFolder = null;
        try {
            Node publicationNode = doCreateOrUpdate(metadata);
            publicationFolder = publicationNode.getParent().getParent();
            setPublicationAuditFields(publicationNode, publication);
            nodeFactory.addBasicFields(publicationNode, metadata.getTitle());

            /**
             * New update logic:
             * - type, isbn, contact, title, summary, seotitle, metaDescription, description is always set from zip
             * - set if absent: notes
             * -
             * - topics, policies, directorates, roles, tags: merge topics in zip with existing
             */
            // these fields are edited by users, do not overwrite them if they already have a value
            hippoUtils.setPropertyIfAbsent(publicationNode, GOVSCOT_TITLE, metadata.getTitle());
            hippoUtils.setPropertyIfAbsent(publicationNode, "govscot:summary", metadata.getExecutiveSummary());
            hippoUtils.setPropertyIfAbsent(publicationNode, "govscot:seoTitle", metadata.getTitle());
            hippoUtils.setPropertyIfAbsent(publicationNode, "govscot:metaDescription", metadata.getExecutiveSummary());
            hippoUtils.setPropertyIfAbsent(publicationNode, "govscot:notes", "");
            hippoUtils.addHtmlNodeIfAbsent(publicationNode, "govscot:content", metadata.getDescription());
            topicMappings.ensureTopics(publicationNode, metadata);
            policiesUpdater.ensurePolicies(publicationNode, metadata);
            maintainUpdateHistory(publicationNode, metadata);
            createDirectoratesIfAbsent(publicationNode, metadata);
            createRolesIfAbsent(publicationNode, metadata);
            createOrUpdateConsultationFields(publicationNode, metadata);
            createOrUpdateConsultationAnalysisFields(publicationNode, metadata);


            // update the tags - add any that are not already there.
            tagUpdater.updateTags(publicationNode, metadata.getTags());

            // always set these properties
            // we set the publication type to the slugified version of the publication type but want to ensure that
            // we do not remove stopwords when we do this.
            publicationNode.setProperty("govscot:publicationType", hippoPaths.slugify(metadata.mappedPublicationType(), false));
            publicationNode.setProperty("govscot:isbn", metadata.normalisedIsbn());
            populateUrls(publicationNode, metadata);
            Calendar publicationDate = GregorianCalendar.from(metadata.getPublicationDateWithTimezone());
            publicationNode.setProperty("govscot:publicationDate", publicationDate);
            publicationNode.setProperty("govscot:displayDate", publicationDate);
            hippoUtils.ensureHtmlNode(publicationNode, "govscot:contact", mailToLink(publication.getContact()));
            return publicationNode.getParent().getParent();

        } catch (RepositoryException e) {
            removePublicationFolderQuietly(publicationFolder);
            throw new ApsZipImporterException("Failed to create or update publication node", e);
        } catch (ApsZipImporterException e) {
            removePublicationFolderQuietly(publicationFolder);
            throw e;
        }
    }

    void removePublicationFolderQuietly(Node publicationFolder) {
        if (publicationFolder == null) {
            return;
        }
        try {
            publicationFolder.remove();
            session.save();
        } catch (RepositoryException e) {
            LOG.error("Failed to remove publication folder after exception", e);
        }
    }

    /**
     * Update the publication node from the directorates in the metadata. If the node already contains information
     * it will not be overwritten with this data.  We may want to revise this later as we do not want manual edits
     * to be needed.
     */
    private void createDirectoratesIfAbsent(Node publicationNode, Metadata metadata) throws RepositoryException {

        // if there is a primary responsible directorate specified and none existing on the node then create it
        if (shouldUpdateDirectorate(publicationNode, metadata)) {
            createDirectorateLink(publicationNode, RESPONSIBLE_DIRECTORATE, metadata.getPrimaryResponsibleDirectorate());
        }

        if (!publicationNode.hasNode("govscot:secondaryResponsibleDirectorate")) {
            for (String directorate : metadata.getSecondaryResponsibleDirectorates()) {
                createDirectorateLink(publicationNode, "govscot:secondaryResponsibleDirectorate", directorate);
            }
        }
    }

    private boolean shouldUpdateDirectorate(Node publicationNode, Metadata metadata) throws RepositoryException {
        if (isBlank(metadata.getPrimaryResponsibleDirectorate())) {
            return false;
        }

        if (!publicationNode.hasNode(RESPONSIBLE_DIRECTORATE)) {
            return true;
        }

        // some nodes have a responsibleDirectorate of / ... treat these as empty
        Node respDirectorate = publicationNode.getNode(RESPONSIBLE_DIRECTORATE);
        if ("cafebabe-cafe-babe-cafe-babecafebabe".equals(respDirectorate.getProperty(HIPPO_DOCBASE).getString())) {
            respDirectorate.remove();
            return true;
        }

        return false;
    }

    private void createDirectorateLink(
            Node publicationNode,
            String propertyName,
            String directorate) throws RepositoryException {
        Node handle = hippoUtils.findOneXPath(session, directorateHandleQuery(directorate));
        if (handle != null) {
            hippoUtils.createMirror(publicationNode, propertyName, handle);
        } else {
            LOG.warn("No such directorate: '{}'", directorate);
        }
    }

    private void createRolesIfAbsent(Node publicationNode, Metadata metadata)
            throws RepositoryException, ApsZipImporterException {
        if (shouldUpdateRole(publicationNode, metadata)) {
            createRoleLink(publicationNode, RESPONSIBLE_ROLE, metadata.getPrimaryResponsibleRole());
        }

        if (!publicationNode.hasNode("govscot:secondaryResponsibleRole")) {
            for (String role : metadata.getSecondaryResponsibleRoles()) {
                createRoleLink(publicationNode, "govscot:secondaryResponsibleRole", role);
            }
        }
    }

    private void maintainUpdateHistory(Node node, Metadata metadata) throws RepositoryException {

        Update update = metadata.getUpdate();
        if (update == null) {
            return;
        }

        /// if update already exists, do nothing
        Node existingEntry = hippoUtils.find(node.getNodes("govscot:updateHistory"),
                entry -> isExistingEntry(entry, update));
        if (existingEntry != null) {
            return;
        }

        // find the position we want to insert it
        Calendar newLastUpdated = GregorianCalendar.from((update.getLastUpdatedWithTimezone()));
        String beforeNode = beforeNode(node, newLastUpdated);

        // Create the new update history entry
        Node newUpdateEntry = hippoUtils.createNode(node, "govscot:updateHistory", "govscot:UpdateHistory");
        newUpdateEntry.setProperty("govscot:lastUpdated", GregorianCalendar.from(update.getLastUpdatedWithTimezone()));
        newUpdateEntry.setProperty("govscot:updateText", metadata.getUpdate().getUpdateText());

        // find the position to inset this node ...

        ///  this is throwing an exception ... not sutre why
        if (beforeNode !=null) {
            session.save();
            LOG.info("sorting thing {}, {}", beforeNode, newUpdateEntry.getPath());
            node.orderBefore(beforeNode, newUpdateEntry.getPath());
        } else {
            LOG.info("sorting thing nothing to do");
        }
    }

    String beforeNode(Node node, Calendar newDate) throws RepositoryException {
        NodeIterator it = node.getNodes("govscot:updateHistory");
        while (it.hasNext()) {
            Node update = it.nextNode();
            Calendar oldLastUpdated = update.getProperty("govscot:lastUpdated").getDate();
            if (oldLastUpdated.after(newDate)) {
                return update.getPath();
            }
        }
        return null;
    }

    boolean isExistingEntry(Node entry, Update update) throws RepositoryException {
        Calendar newLastUpdated = GregorianCalendar.from((update.getLastUpdatedWithTimezone()));
        Calendar oldLastUpdated = entry.getProperty("govscot:lastUpdated").getDate();
        String newUpdateText = update.getUpdateText();
        String oldUpdateText = entry.getProperty("govscot:updateText").getString();
        return sameInstant(newLastUpdated, oldLastUpdated) && sameText(newUpdateText, oldUpdateText);
    }

    boolean sameText(String left, String right) {
        return left.equals(right);
    }

    boolean sameInstant(Calendar left, Calendar right) {
        return left.getTimeInMillis() == right.getTimeInMillis();
    }

    private void createOrUpdateConsultationAnalysisFields(Node node, Metadata metadata)
            throws RepositoryException, ApsZipImporterException {

        if (!metadata.isConsultationAnalysis()) {
            return;
        }

        // remove any existing featured links
        if (node.hasNode(FEATURED_LINKS)) {
            node.getNode(FEATURED_LINKS).remove();
        }

        Consultation consultation = metadata.getConsultation();
        if (isBlank(consultation.getConsultationUrl())) {
            LOG.warn("Consultation analysis does not specify a consultation {}", metadata.getIsbn());
            return;
        }

        Node consultationHandle = getConsultationNode(consultation.getConsultationUrl());
        if (consultationHandle != null) {
            Node featuredLinks = node.addNode(FEATURED_LINKS, "govscot:FeaturedLinks");
            featuredLinks.setProperty("govscot:label", "Consultation");
            Node linkNode = featuredLinks.addNode("govscot:link", "govscot:DescribedLink");
            linkNode.setProperty("govscot:description", "");
            Node link = linkNode.addNode("govscot:link", HIPPO_MIRROR);
            link.setProperty(HIPPO_DOCBASE, consultationHandle.getIdentifier());
        }
    }

    Node getConsultationNode(String consultationUrl) throws RepositoryException, ApsZipImporterException {
        String slug = getSlugFromConsultationUrl(consultationUrl);
        if (slug == null) {
            throw new ApsZipImporterException("invalid consultation URL");
        }

        String xpath = String.format("//element(*, govscot:Publication)[govscot:slug = '%s'][hippo:availability = 'live'][hippostd:state = 'published']", slug);
        Node consultation = hippoUtils.findOneXPath(session, xpath);
        if (consultation == null) {
            throw new ApsZipImporterException("Unable to find consultation " + consultationUrl);
        }
        return consultation.getParent();
    }

    String getSlugFromConsultationUrl(String url) {
        if (!startsWith(url, "https://www.gov.scot/publications/")) {
            return null;
        }
        url = StringUtils.stripEnd(url, "/");
        String [] parts = url.split("/");
        if (parts.length != 5) {
            return null;
        }

        return parts[4];
    }

    private void createOrUpdateConsultationFields(Node node, Metadata metadata) throws RepositoryException {
        if (!metadata.isConsultation()) {
            return;
        }

        Consultation consultation = metadata.getConsultation();
        LocalDateTime now = LocalDateTime.now();
        boolean open = now.isAfter(consultation.getOpeningDate()) && now.isBefore(consultation.getClosingDate());

        // This is how a consultation LocalDateTime is directly used to set a property
        node.setProperty("govscot:openingDate", GregorianCalendar.from(consultation.getOpeningDate().atZone(ZoneId.systemDefault())));
        node.setProperty("govscot:closingDate", GregorianCalendar.from(consultation.getClosingDate().atZone(ZoneId.systemDefault())));
        node.setProperty("govscot:isOpen", open);
        node.setProperty("govscot:responseUrl", consultation.getResponseUrl());
        // remove any response methods that exist from a previous upload
        NodeIterator it = node.getNodes("govscot:consultationResponseMethods");
        hippoUtils.apply(it, Node::remove);
        for (ConsultationResponseMethod responseMethod : consultation.getAlternativeWaysToRespond()) {
            Node respondMethodNode = hippoUtils.createNode(node,"govscot:consultationResponseMethods",
                    "govscot:ConsultationResponseType", HIPPO_CONTAINER, HIPPOSTD_CONTAINER, "hippostd:relaxed");
            respondMethodNode.setProperty("govscot:type", responseMethod.getTitle());
            hippoUtils.ensureHtmlNode(respondMethodNode, "govscot:content", responseMethod.getText());
        }
    }

    private boolean shouldUpdateRole(Node publicationNode, Metadata metadata) throws RepositoryException {
        if (isBlank(metadata.getPrimaryResponsibleRole())) {
            return false;
        }

        if (!publicationNode.hasNode(RESPONSIBLE_ROLE)) {
            return true;
        }

        // some nodes have a responsibleDirectorate of / ... treat these as empty
        Node respRole = publicationNode.getNode(RESPONSIBLE_ROLE);
        if ("cafebabe-cafe-babe-cafe-babecafebabe".equals(respRole.getProperty(HIPPO_DOCBASE).getString())) {
            respRole.remove();
            return true;
        }

        return false;
    }

    private void createRoleLink(Node publicationNode, String propertyName, String title)
            throws RepositoryException, ApsZipImporterException {
        Node handle = findRoleOrPerson(title);
        if (handle != null) {
            hippoUtils.createMirror(publicationNode, propertyName, handle);
        } else {
            throw new ApsZipImporterException(String.format("No such role: '%s'", title));
        }
    }

    private Node findRoleOrPerson(String roleOrPerson) throws RepositoryException {
        return firstNonNull(
                hippoUtils.findOneXPath(session, roleHandleQuery(roleOrPerson)),
                hippoUtils.findOneXPath(session, personHandleQuery(roleOrPerson)),
                hippoUtils.findOneXPath(session, featuredRoleHandleQuery(roleOrPerson)));
    }

    private String mailToLink(String email) {
        return isBlank(email) ?
                "" : String.format("<p>Email: <a href=\"mailto:%s\">%s</a></p>", email, email);
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
        if (isNotBlank(metadata.getUrl())) {
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
            pubFolder.setProperty(HIPPO_NAME, metadata.getTitle());
            Node handle = nodeFactory.newHandle(metadata.getTitle(), pubFolder, "index");
            String type = metadata.isConsultation() ? "govscot:Consultation" : "govscot:Publication";
            pubNode = nodeFactory.newDocumentNode(
                    handle,
                    "index",
                    metadata.getTitle(),
                    type,
                    metadata.getPublicationDateWithTimezone(),
                    metadata.shouldEmbargo());
        } else {
            // remove any other nodes there are ...
            hippoUtils.removeSiblings(pubNode);

            sitemap.removeSitemapEntry(pubNode);

            // the node already exists, make sure its publications status matches what is in the zip
            nodeFactory.ensurePublicationStatus(
                    pubNode,
                    metadata.getPublicationDateWithTimezone(),
                    metadata.shouldEmbargo());
        }

        // create slug lookup for preview
        String slug = pubNode.getProperty("govscot:slug").getString();
        String path = StringUtils.substringAfter(pubNode.getParent().getPath(), "/content/documents/govscot");
        slugLookups.updateLookup(slug, path, "govscot", "publications", "preview", true);

        // make sure the folder node has the mixin used for bulk publish actions
        pubNode.getParent().getParent().addMixin("bulkpublish:bulkpublishdirectory");

        // if the publication is published then create new sitemap entry and live slug lookup
        if ("published".equals(pubNode.getProperty(HIPPOSTD_STATE).getString())) {
            sitemap.ensureSitemapEntry(pubNode);
            slugLookups.updateLookup(slug, path, "govscot", "publications", "live", true);
        }
        return pubNode;
    }

    /**
     * Ensure that this publications folder is in the right place.  The folder is based on its type and publication
     * date and so if either changed since the last time the publication was uploaded then it might have to be moved.
     */
    public Node ensureMonthNode(Node publicationFolder, Metadata metadata) throws RepositoryException {
        List<String> monthPath = pathStrategy.monthFolderPath(metadata);
        Node monthNode = hippoPaths.ensurePath(monthPath);
        Node existingMonthNode = publicationFolder.getParent();
        // if the exiting node has a different folder than the new folder then move it into the right folder
        if (!existingMonthNode.getIdentifier().equals(monthNode.getIdentifier())) {
            String newPath = monthNode.getPath() + "/" + publicationFolder.getName();
            LOG.info("moving {} to {}", publicationFolder.getPath(), newPath);
            session.move(publicationFolder.getPath(), newPath);
            return session.getNode(newPath);
        } else {
            return publicationFolder;
        }
    }

    /**
     * If a publication with this isbn already exists then we want to update it.  To make sure we update the right
     * node we want to find all of then and then decide which node to use if there are multiple drafts.  If a
     * published node exists then use that. Then fall back to using the unpublished one and then finally to draft.
     */
     public Node findPublicationNodeToUpdate(Metadata metadata) throws RepositoryException {
        // Query to see if a publications with this ISBN already exist.  If it does then we will update the existing
        // node rather than create a new one
        String sql = String.format("SELECT * FROM govscot:Publication WHERE govscot:isbn = '%s'", metadata.normalisedIsbn());
        Query query = session.getWorkspace().getQueryManager().createQuery(sql, Query.SQL);
        QueryResult result = query.execute();
        Map<String, Node> byState = new HashMap<>();
        NodeIterator it = result.getNodes();
        while (it.hasNext()) {
            Node node = it.nextNode();
            String state = node.getProperty(HIPPOSTD_STATE).getString();
            byState.put(state, node);
        }
        return firstNonNull(
                byState.get("published"),
                byState.get("unpublished"),
                byState.get("draft"));
    }

}
