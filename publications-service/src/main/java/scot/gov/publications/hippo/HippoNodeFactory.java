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
        handle.setProperty(HIPPO_NAME, Sanitiser.sanitise(title));
        return handle;
    }

    public Node newDocumentNode(
            Node handle,
            String slug,
            String title,
            String type,
            ZonedDateTime publishDateTime) throws RepositoryException {

        Node node = hippoUtils.createNode(handle, slug, type, DOCUMENT_MIXINS);
        node.setProperty(HIPPO_NAME, Sanitiser.sanitise(title));
        node.setProperty("hippotranslation:locale", "en");
        node.setProperty("hippotranslation:id", UUID.randomUUID().toString());
        Calendar now = Calendar.getInstance();
        node.setProperty("hippostdpubwf:createdBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:creationDate", now);
        node.setProperty("hippostdpubwf:lastModifiedBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:lastModificationDate", now);

        // if the publish date of this item is in the future then create a request to publish it.
        if (publishDateTime.isBefore(ZonedDateTime.now())) {
            node.setProperty("hippo:availability", new String[]{"live", "preview"});
            node.setProperty("hippostd:state", "published");
            node.setProperty("hippostd:stateSummary", "live");
        } else {
            node.setProperty("hippostd:state", "unpublished");
            node.setProperty("hippostd:stateSummary", "new");
            addWorkflowJob(handle, publishDateTime);
        }
        return node;
    }

    public void addWorkflowJob(Node handle, ZonedDateTime publishDateTime) throws RepositoryException {
        Node job = handle.addNode("hippo:request", "hipposched:workflowjob");
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
        hippoUtils.setPropertyIfAbsent(node, "hippo:name", Sanitiser.sanitise(name));
        node.setProperty("hippotranslation:locale", "en");
        node.setProperty("hippotranslation:id", UUID.randomUUID().toString());
        Calendar now = Calendar.getInstance();
        node.setProperty("hippostdpubwf:createdBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:creationDate", now);
        hippoUtils.setPropertyIfAbsent(node, "hippostdpubwf:lastModifiedBy", configuration.getHippo().getUser());
        node.setProperty("hippostdpubwf:lastModificationDate", now);
    }

}
