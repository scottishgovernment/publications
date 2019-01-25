package scot.gov.publications.hippo;

import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.util.MimeTypeUtils;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static scot.gov.publications.hippo.Constants.*;
import static scot.gov.publications.hippo.Constants.HIPPO_FILENAME;

public class HippoNodeFactory {

    PublicationsConfiguration configuration;

    Session session;

    HippoUtils hippoUtils = new HippoUtils();

    public HippoNodeFactory(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.configuration = configuration;
    }

    public Node newHandle(String title, Node parent, String slug) throws RepositoryException {
        Node handle = hippoUtils.createNode(parent, slug, "hippo:handle", HANDLE_MIXINS);
        handle.setProperty(HIPPO_NAME, TitleSanitiser.sanitise(title));
        return handle;
    }

    public Node newDocumentNode(
            Node handle,
            String slug,
            String title,
            String type,
            ZonedDateTime publishDateTime) throws RepositoryException {

        Node node = hippoUtils.createNode(handle, slug, type, DOCUMENT_MIXINS);

        // the folder has been created using the SlugAllocations strategy so we can use it name as the govscot:slug
        // property that is used to determine the url of this publication.
        node.setProperty("govscot:slug", handle.getParent().getName());
        node.setProperty(HIPPO_NAME, TitleSanitiser.sanitise(title));
        node.setProperty("hippotranslation:locale", "en");
        node.setProperty("hippotranslation:id", UUID.randomUUID().toString());
        Calendar now = Calendar.getInstance();
        node.setProperty("hippostdpubwf:createdBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:creationDate", now);
        node.setProperty("hippostdpubwf:lastModifiedBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:lastModificationDate", now);
        ensurePublicationStatus(node, publishDateTime);
        return node;
    }

    /**
     * Ensure that the publication status and wny required workflow job exists for htis publication
     */
    public void ensurePublicationStatus(Node node, ZonedDateTime publishDateTime) throws RepositoryException {
        // remove any workflow job that exists.  If it is needed then we will recreate it
        ensureWorkflowJobDeleted(node);


        if (publishDateTime.isBefore(ZonedDateTime.now())) {
            // the publicaiton can be p
            node.setProperty("hippo:availability", new String[]{"live", "preview"});
            node.setProperty("hippostd:state", "published");
            node.setProperty("hippostd:stateSummary", "live");
        } else {
            node.setProperty("hippo:availability", new String[]{"preview"});
            node.setProperty("hippostd:state", "unpublished");
            node.setProperty("hippostd:stateSummary", "new");
            ensureWorkflowJob(node.getParent(), publishDateTime);
        }
    }

    /**
     * If this publication node has a workflow job attached to its handle then remove it
     */
    public void ensureWorkflowJobDeleted(Node node) throws RepositoryException {
        Node handle = node.getParent();
        if (handle.hasNode(HIPPO_REQUEST)) {
            String requestPath = handle.getNode(HIPPO_REQUEST).getPath();
            session.removeItem(requestPath);
        }
    }

    /**
     * Create a workflow job to publish this publication at the specified publication date
     */
    public void ensureWorkflowJob(Node handle, ZonedDateTime publishDateTime) throws RepositoryException {
        // create the job needed to publish this node
        Node job = handle.addNode(HIPPO_REQUEST, "hipposched:workflowjob");
        job.setProperty("hipposched:attributeNames", new String[] { "hipposched:subjectId", "hipposched:methodName"});
        job.setProperty("hipposched:attributeValues", new String[] { handle.getIdentifier(), "publish"});
        job.setProperty("hipposched:repositoryJobClass",
                "org.onehippo.repository.documentworkflow.task.ScheduleWorkflowTask$WorkflowJob");
        Node triggers = job.addNode("hipposched:triggers", "hipposched:triggers");
        Node defaultNode = triggers.addNode("default", "hipposched:simpletrigger");
        defaultNode.addMixin("mix:lockable");
        defaultNode.addMixin("mix:referenceable");
        Calendar publishTime = GregorianCalendar.from(publishDateTime);
        defaultNode.setProperty("hipposched:nextFireTime", publishTime);
        defaultNode.setProperty("hipposched:startTime", publishTime);
    }

    public Node newResourceNode(Node parent, String property, String filename, ZipFile zipFile, ZipEntry zipEntry)
            throws RepositoryException {

        try {
            String contentType = MimeTypeUtils.detectContentType(filename);


            Node resourceNode = parent.addNode(property, "hippo:resource");
            Binary binary = session.getValueFactory().createBinary(zipFile.getInputStream(zipEntry));

            resourceNode.setProperty(HIPPO_FILENAME, filename);
            resourceNode.setProperty(JCR_DATA, binary);
            resourceNode.setProperty(JCR_MIMETYPE, contentType);
            resourceNode.setProperty(JCR_LAST_MODIFIED, Calendar.getInstance());

            // to avoid costly text extraction, set hippo:text to an empty string
            resourceNode.setProperty("hippo:text", "");
            return resourceNode;
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
    }

    public void addBasicFields(Node node, String name) throws RepositoryException {
        hippoUtils.setPropertyIfAbsent(node, "hippo:name", name);
        node.setProperty("hippotranslation:locale", "en");
        node.setProperty("hippotranslation:id", UUID.randomUUID().toString());
        Calendar now = Calendar.getInstance();
        node.setProperty("hippostdpubwf:createdBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:creationDate", now);
        hippoUtils.setPropertyIfAbsent(node, "hippostdpubwf:lastModifiedBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:lastModificationDate", now);
    }

}
