package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Created by z418868 on 12/05/2022.
 */
public class Sitemap {

    private static final Logger LOG = LoggerFactory.getLogger(Sitemap.class);

    private static final String LAST_MOD = "govscot:lastMod";

    private static final String LATEST_LAST_MOD = "govscot:latestLastMod";

    Session session;

    HippoPaths paths;

    HippoUtils hippoUtils = new HippoUtils();

    Sitemap(Session session) {
        this.session = session;
        this.paths = new HippoPaths(session);
    }

    void removeSitemapEntry(Node node) throws RepositoryException {
        String sitename = "govscot";
        String handleIdentifier = node.getParent().getIdentifier();
        String xpath = String.format("/jcr:root/content/sitemaps/%s//uuid-%s", sitename, handleIdentifier);
        hippoUtils.executeXpathQuery(session, xpath, currentNode -> {
            if (currentNode != null) {
                String url = currentNode.getProperty("govscot:loc").getString();
                LOG.info("removing sitemap node for {}, {}, {}", url, node.getPath(), currentNode.getPath());
                currentNode.remove();
            }
        });
    }


    void ensureSitemapEntry(Node node) throws RepositoryException {


        List<String> path = getPath(node);
        LOG.info("ensureSitemapEntry {}", path);
        Node sitemapNode = paths.ensureSitemapPath(path);
        updateSitemapLatestDate();
        String handleGuid = node.getParent().getIdentifier();
        Node record = sitemapNode.addNode("uuid-" + handleGuid, "nt:unstructured");
        Calendar lastModified = Calendar.getInstance();
        String url = url(node);
        record.setProperty("govscot:loc", url);
        record.setProperty(LAST_MOD, lastModified);
        LOG.info("update sitemap node for {}, {}", url,     record.getPath());
    }

    void updateSitemapLatestDate() throws RepositoryException {
        Node sitemapNode = session.getNode("/content/sitemaps/govscot");
        sitemapNode.setProperty(LATEST_LAST_MOD, Calendar.getInstance());
    }

    String url(Node node) throws RepositoryException {
        return "/publications/" + node.getProperty("govscot:slug").getString() + "/";
    }

    List<String> getPath(Node node) throws RepositoryException {
        List<String> path = new ArrayList<>();
        Calendar date = node.getProperty("hippostdpubwf:lastModificationDate").getDate();
        String year = Integer.toString(date.get(Calendar.YEAR));
        String month = Integer.toString(date.get(Calendar.MONTH) + 1);
        Collections.addAll(path, "govscot", year, month);
        return path;
    }
}
