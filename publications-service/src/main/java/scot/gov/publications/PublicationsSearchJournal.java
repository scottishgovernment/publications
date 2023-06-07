package scot.gov.publications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.hippo.HippoUtils;
import scot.gov.publishing.searchjournal.FeatureFlag;
import scot.gov.publishing.searchjournal.FunnelbackCollection;
import scot.gov.publishing.searchjournal.SearchJournal;
import scot.gov.publishing.searchjournal.SearchJournalEntry;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.time.ZonedDateTime;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Logic for determining what journal entries should be recorded when a publication is uploaded.
 *
 * We depublish first based on the content in the cms.  This is because the new upload may have different pages and so
 * some might have to be removed.
 *
 * Once we know what pages will be published after the upload, we can remove any unpublish entries that are not needed.
 */
public class PublicationsSearchJournal {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsSearchJournal.class);

    HippoUtils hippoUtils = new HippoUtils();

    void recordJournalEntries(Session session, List<SearchJournalEntry> entries) throws RepositoryException {
        SearchJournal journal = new scot.gov.publishing.searchjournal.SearchJournal(session);
        List<SearchJournalEntry> entriesToRecord = entriesToRecord(entries);
        for (SearchJournalEntry entry : entriesToRecord) {
            LOG.info("record {} {} {} {}", calString(entry.getTimestamp()), entry.getSequence(), entry.getAction(), entry.getUrl());
            journal.record(entry);
        }
    }

    public String calString(Calendar calendar) {
        GregorianCalendar cal = (GregorianCalendar) calendar;
        ZonedDateTime zdt = cal.toZonedDateTime();
        return zdt.toString() + " " + calendar;
    }

    List<SearchJournalEntry> entriesToRecord(List<SearchJournalEntry> entries) {
        // the list contains unpublish and publish journal entries, some of which may not be needed
        // these are unpublish events that we are subsequently going to be published
        Set<String> publishUrls = publishUrls(entries);
        return entries.stream().filter(entry -> shouldInclude(entry, publishUrls)).collect(toList());
    }

    Set<String> publishUrls(List<SearchJournalEntry> entries) {
        return entries.stream()
                .filter(this::isPublish)
                .map(SearchJournalEntry::getUrl)
                .collect(toSet());
    }

    boolean shouldInclude(SearchJournalEntry entry, Set<String> publishUrls) {
        if ("publish".equals(entry.getAction())) {
            return true;
        }

        return !publishUrls.contains(entry.getUrl());
    }

    boolean isPublish(SearchJournalEntry entry) {
        return "publish".equals(entry.getAction());
    }

    List<SearchJournalEntry> getJournalEntries(String action, Session session, Node publicationFolder)  throws RepositoryException {
        List<SearchJournalEntry> entries = new ArrayList<>();
        FeatureFlag featureFlag = new FeatureFlag(session, "SearchJournalEventListener");
        if (!featureFlag.isEnabled()) {
            return entries;
        }
        Node publication = publicationFolder.getNode("index").getNode("index");
        FunnelbackCollection collection = funnelbackCollection(publication);
        String publicationUrl = publicationUrl(publication);
        long sequence = 1;
        entries.add(journalEntry(action, publicationUrl, collection, sequence));
        NodeIterator pageIt = publicationFolder.getNode("pages").getNodes();
        if (!pageIt.hasNext()) {
            return entries;
        }

        entries.add(journalEntry(action, publicationUrl + "documents/", collection, ++sequence));
        boolean seenFirstPage = false;
        while (pageIt.hasNext()) {
            Node pageHandle = pageIt.nextNode();
            if (!isContentsPage(pageHandle)) {
                // we do not want to index the first visible (i.e. first non contents) page
                if (!seenFirstPage) {
                    seenFirstPage = true;
                } else {
                    String url = pageUrl(publication, pageHandle);
                    entries.add(journalEntry(action, url, collection, ++sequence));
                }
            }
        }
        return entries;
    }

    FunnelbackCollection funnelbackCollection(Node pub) throws RepositoryException {
        String publicationType = pub.getProperty("govscot:publicationType").getString();
        return FunnelbackCollection.getCollectionByPublicationType(publicationType);
    }

    String publicationUrl(Node pub) throws RepositoryException {
        String slug = pub.getProperty("govscot:slug").getString();
        return "https://www.gov.scot/publications/" + slug + "/";
    }

    String pageUrl(Node pub, Node page) throws RepositoryException {
        String publicationUrl = publicationUrl(pub);
        return publicationUrl + "pages/" + page.getName() + "/";
    }

    boolean isContentsPage(Node handle) throws RepositoryException {
        Node variant = hippoUtils.getVariant(handle);
        return variant.hasProperty("govscot:contentsPage")
                && variant.getProperty("govscot:contentsPage").getBoolean();
    }

    SearchJournalEntry journalEntry(String action, String url, FunnelbackCollection collection, long sequence) {
        SearchJournalEntry entry = new SearchJournalEntry();
        entry.setAction(action);
        entry.setAttempt(0);
        entry.setTimestamp(Calendar.getInstance());
        entry.setCollection(collection.getCollectionName());
        entry.setUrl(url);
        entry.setSequence(sequence);
        return entry;
    }
}
