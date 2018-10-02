package scot.gov.publications.hippo.rewriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

public class LinkRewriter {

    private static final Logger LOG = LoggerFactory.getLogger(LinkRewriter.class);

    private static final String[] EMPTY = new String[0];

    public static final String CONTENT_ATTRIB  = "hippostd:content";

    public void rewriteLink(Node pageNode, String from, Node to) throws RepositoryException {
        // determine of we already link to this
        Node contentNode = pageNode.getNode("govscot:content");
        Node facet = ensueFacetSelect(contentNode, to);
        String fromhtml = contentNode.getProperty(CONTENT_ATTRIB).getString();
        String toHtml = fromhtml.replaceAll(from, facet.getName());
        contentNode.setProperty(CONTENT_ATTRIB, toHtml);

        LOG.debug("Rewriting {} -> {} in page {}, created new facet for {}",
                from,
                facet.getName(),
                pageNode.getPath(),
                to.getPath());
    }

    Node ensueFacetSelect(Node contentNode, Node to) throws RepositoryException {
        NodeIterator it = contentNode.getNodes();
        while (it.hasNext()) {
            Node node = it.nextNode();
            if ("hippo:facetselect".equals(node.getPrimaryNodeType().getName())) {
                String docbase = node.getProperty("hippo:docbase").getString();
                if (docbase.equals(to.getIdentifier())) {
                    return node;
                }
            }
        }

        // no facet exists for this node
        Node facet = contentNode.addNode(to.getIdentifier(), "hippo:facetselect");
        facet.setProperty("hippo:docbase", to.getIdentifier());
        facet.setProperty("hippo:facets", EMPTY);
        facet.setProperty("hippo:modes", EMPTY);
        facet.setProperty("hippo:values", EMPTY);
        return facet;
    }

}
