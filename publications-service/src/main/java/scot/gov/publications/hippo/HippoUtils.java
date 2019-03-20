package scot.gov.publications.hippo;

import org.apache.commons.lang3.StringUtils;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.substringAfter;

public class HippoUtils {

    @FunctionalInterface
    public interface ThrowingPredicate {
        boolean test(Node t) throws RepositoryException;
    }

    @FunctionalInterface
    public interface ThrowingConsumer {
        void accept(Node t) throws RepositoryException;
    }

    public void apply(NodeIterator it, ThrowingConsumer consumer) throws RepositoryException {
        apply(it, node -> true, consumer);
    }

    public void apply(NodeIterator it, ThrowingPredicate predicate, ThrowingConsumer consumer) throws RepositoryException {
        while (it.hasNext()) {
            Node node = it.nextNode();
            if (predicate.test(node)) {
                consumer.accept(node);
            }
        }
    }

    public List<String> pathFromNode(Node node) throws RepositoryException {
        List<String> path = new ArrayList<>();
        String pathFromGovscot = substringAfter(node.getPath(), "/content/documents/govscot/");
        path.addAll(asList(pathFromGovscot.split("/")));
        return path;
    }

    public void setPropertyIfAbsent(Node node, String property, String value) throws RepositoryException {
        if (node.hasProperty(property)) {
            return;
        }
        node.setProperty(property, value);
    }

    public Node addHtmlNodeIfAbsent(Node node, String name, String value) throws  RepositoryException {
        // if the node already exists then return it
        if (node.hasNode(name)) {
            return node.getNode(name);
        }

        // it does not already exist, create it
        return createHtmlNode(node, name, value);
    }

    public Node ensureHtmlNode(Node node, String name, String value) throws  RepositoryException {
        // if the node exists already then delete it
        ensureRemoved(node, name);

        // create the node
        return createHtmlNode(node, name, value);
    }

    private Node createHtmlNode(Node node, String name, String value) throws RepositoryException {
        Node contentNode = node.addNode(name, "hippostd:html");
        contentNode.setProperty("hippostd:content", StringUtils.defaultString(value, ""));
        return contentNode;
    }

    public Node ensureNode(Node parent, String name, String primaryType, String ...mixins) throws RepositoryException {
        if (parent.hasNode(name)) {
            return parent.getNode(name);
        }
        return createNode(parent, name, primaryType, mixins);
    }

    public Node createNode(Node parent, String name, String primaryType, String ...mixins) throws RepositoryException {
        Node node = parent.addNode(name, primaryType);
        for (String mixin : mixins) {
            node.addMixin(mixin);
        }
        return node;
    }

    public void ensureRemoved(Node node, String name) throws RepositoryException {
        if (node.hasNode(name)) {
            node.getNode(name).remove();
        }
    }

    public void removeChildren(Node node) throws RepositoryException {
        NodeIterator nodeIt = node.getNodes();
        while (nodeIt.hasNext()) {
            nodeIt.nextNode().remove();
        }
    }

    public void removeSiblings(Node node) throws RepositoryException {
        NodeIterator it = node.getParent().getNodes();
        while (it.hasNext()) {
            Node sibling = it.nextNode();
            if (!sibling.getIdentifier().equals(node.getIdentifier())) {
                sibling.remove();
            }
        }
    }

    public void setPropertyStrings(Node node, String property, Collection<String> values) throws RepositoryException {
        node.setProperty(property, values.toArray(new String[values.size()]), PropertyType.STRING);
    }

    public void setPropertyStringsIfAbsent(Node node, String property, Collection<String> values) throws RepositoryException {

        if (node.hasProperty(property)) {
            return;
        }
        setPropertyStrings(node, property, values);
    }

    public Node findOne(Session session, String queryTemplate, Object... args) throws RepositoryException {
        String sql = String.format(queryTemplate, args);
        Query queryObj = session.getWorkspace().getQueryManager().createQuery(sql, Query.SQL);
        QueryResult result = queryObj.execute();
        if (result.getNodes().getSize() == 1) {
            return result.getNodes().nextNode();
        }
        return null;
    }

    public Node findOneXPath(Session session, String xpath) throws RepositoryException {
        Query queryObj = session.getWorkspace().getQueryManager().createQuery(xpath, Query.XPATH);
        QueryResult result = queryObj.execute();
        if (result.getNodes().getSize() == 1) {
            return result.getNodes().nextNode();
        }
        return null;
    }

    public Node mostRecentDraft(Node handle) throws RepositoryException {
        NodeIterator it = handle.getNodes();
        Node node = null;
        while (it.hasNext()) {
            node = it.nextNode();
        }
        return node;
    }

    void createMirror(Node publicationNode, String propertyName, Node handle) throws RepositoryException {
        Node mirror = createNode(publicationNode, propertyName, "hippo:mirror");
        mirror.setProperty("hippo:docbase", handle.getIdentifier());
    }
}
