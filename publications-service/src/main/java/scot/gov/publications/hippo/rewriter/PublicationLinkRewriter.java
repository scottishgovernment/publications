package scot.gov.publications.hippo.rewriter;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static scot.gov.publications.hippo.rewriter.LinkRewriter.CONTENT_ATTRIB;

public class PublicationLinkRewriter {

    LinkRewriter linkRewriter = new LinkRewriter();

    Map<String, Node> pageNodesByEntryname;

    public PublicationLinkRewriter(Map<String, Node> pageNodesByEntryname) {
        this.pageNodesByEntryname = pageNodesByEntryname;
    }

    public void rewrite(Node publicationFolder) throws RepositoryException {
        NodeIterator pageIterator = publicationFolder.getNode("pages").getNodes();
        while (pageIterator.hasNext()) {
            Node pageHandle = pageIterator.nextNode();
            Node page = pageHandle.getNodes().nextNode();
            rewritePage(page);
        }
    }

    private void rewritePage(Node pageNode) throws RepositoryException {
        Node htmlNode = pageNode.getNode("govscot:content");
        String html = htmlNode.getProperty(CONTENT_ATTRIB).getString();
        Document htmlDoc = Jsoup.parse(html);
        List<Element> links = htmlDoc.select("a[href]")
                .stream().filter(this::isLocalLink)
                .collect(toList());
        for (Element link : links) {
            rewriteLink(link.attr("href"), pageNode);
        }
    }

    private void rewriteLink(String href, Node pageNode) throws RepositoryException {
        if (!pageNodesByEntryname.containsKey(href)) {
            return;
        }

        Node pagenode = pageNodesByEntryname.get(href);
        linkRewriter.rewriteLink(pageNode, href, pagenode);
    }

    private boolean isLocalLink(Element link) {
        String href = link.attr("href");
        if (StringUtils.isEmpty(href)) {
            return false;
        }

        // what about mailtos ftp etc?
        return !href.startsWith("http");
    }
}
