package scot.gov.publications.hippo.searchjournal;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class SearchJournal {

    private static final Logger LOG = LoggerFactory.getLogger(SearchJournal.class);

    private final Session session;

    private static final String URL_BASE = "https://www.gov.scot/";

    private static final String ACTION = "searchjournal:action";
    private static final String COLLECTION = "searchjournal:collection";
    private static final String URL = "searchjournal:url";
    private static final String TIMESTAMP = "searchjournal:timestamp";
    private static final String ATTEMPT = "searchjournal:attempt";

    FeatureFlag featureFlag;

    public SearchJournal(Session session) {
        this.session = session;
        featureFlag = new FeatureFlag(session, "SearchJournalEventListener");
    }

    public Node record(SearchJournalEntry entry) throws RepositoryException {
        if (!featureFlag.isEnabled()) {
            return null;
        }
        Node record = getNodeForRecord(entry);
        LOG.info("record journal entry {} {} {}, attempt {}, {}",
                entry.getAction(), entry.getCollection(), entry.getUrl(), entry.getAttempt(), ((GregorianCalendar)entry.getTimestamp()).toZonedDateTime());
        record.setProperty(ACTION, entry.getAction());
        record.setProperty(COLLECTION, entry.getCollection());
        record.setProperty(URL, entry.getUrl());
        record.setProperty(TIMESTAMP, entry.getTimestamp());
        record.setProperty(ATTEMPT, entry.getAttempt());
        session.save();
        return record;
    }

    Node getNodeForRecord(SearchJournalEntry entry) throws RepositoryException {
        Node content = session.getNode("/content");
        Node searchjournal = ensurePathNode(content, "searchjournal");
        Calendar date = entry.getTimestamp();
        Node year = ensurePathNode(searchjournal, Integer.toString(date.get(Calendar.YEAR)));
        Node month = ensurePathNode(year, Integer.toString(date.get(Calendar.MONTH)));
        Node day = ensurePathNode(month, Integer.toString(date.get(Calendar.DAY_OF_MONTH)));
        String newName = uniquename(day);
        return day.addNode(newName, "searchjournal:entry");
    }

    Node ensurePathNode(Node parent, String name) throws RepositoryException {
        if (parent.hasNode(name)) {
            return parent.getNode(name);
        }
        return parent.addNode(name, "nt:unstructured");
    }

    String uniquename(Node parent) throws RepositoryException {
        String candidate = RandomStringUtils.randomAlphabetic(4);
        return parent.hasNode(candidate) ? uniquename(parent) : candidate;
    }

    public void recordInSearchJournal(Session session, Metadata metadata, Node publicationFolder)  throws RepositoryException {
        SearchJournal journal = new SearchJournal(session);
        FunnelbackCollection collection = getCollectionByPublicationType(metadata.getPublicationType());
        String publicationUrl = slugUrl(publicationFolder.getNode("index").getNode("index"));
        journal.record(entry(publicationUrl, collection.getCollectionName()));
        journal.record(entry(publicationUrl + "documents/", collection.getCollectionName()));
        NodeIterator pageIt = publicationFolder.getNode("pages").getNodes();
        while (pageIt.hasNext()) {
            Node pageHandle = pageIt.nextNode();
            if (!isContentsPage(pageHandle)) {
                String url = publicationUrl + "pages/" + pageHandle.getName() + "/";
                SearchJournalEntry entry = entry(url, collection.getCollectionName());
                journal.record(entry);
            }
        }
    }

    SearchJournalEntry entry(String url, String collection) {
        SearchJournalEntry entry = new SearchJournalEntry();
        entry.setCollection(collection);
        entry.setTimestamp(Calendar.getInstance());
        entry.setAttempt(0);
        entry.setAction("publish");
        entry.setUrl(url);
        return entry;
    }

    String slugUrl(Node node) throws RepositoryException {
        String slug = node.getProperty("govscot:slug").getString();
        return new StringBuilder(URL_BASE)
                .append("publications")
                .append('/')
                .append(slug)
                .append('/')
                .toString();
    }

    boolean isContentsPage(Node handle) throws RepositoryException {
        Node node = handle.getNode(handle.getName());
        return node.getProperty("govscot:contentsPage").getBoolean();
    }

    FunnelbackCollection getCollectionByPublicationType(String publicationType) {

        switch (publicationType) {
            case "foi-eir-release":
                return FunnelbackCollection.FOI;
            case "statistics":
            case "research-and-analysis":
                return FunnelbackCollection.STATS_AND_RESEARCH;
            default:
                return FunnelbackCollection.PUBLICATIONS;
        }
    }
}
