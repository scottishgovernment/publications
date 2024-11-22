package scot.gov.publications.hippo;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import java.util.*;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static scot.gov.publications.hippo.Constants.HIPPOSTD_STATE;

public class HippoUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HippoUtils.class);

    @FunctionalInterface
    public interface ThrowingPredicate {
        boolean test(Node t) throws RepositoryException;
    }

    @FunctionalInterface
    public interface ThrowingConsumer {
        void accept(Node t) throws RepositoryException;
    }

    public Node find(NodeIterator it, ThrowingPredicate predicate) throws RepositoryException {
        while (it.hasNext()) {
            Node node = it.nextNode();
            if (predicate.test(node)) {
                return node;
            }
        }
        return null;
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

    public Node addHtmlNodeIfAbsent(Node node, String name, String value) throws RepositoryException {
        // if the node already exists then return it
        if (node.hasNode(name)) {
            return node.getNode(name);
        }

        // it does not already exist, create it
        return createHtmlNode(node, name, value);
    }

    public Node ensureHtmlNode(Node node, String name, String value) throws RepositoryException {
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

    public Node ensureNode(Node parent, String name, String primaryType, String... mixins) throws RepositoryException {
        if (parent.hasNode(name)) {
            return parent.getNode(name);
        }
        return createNode(parent, name, primaryType, mixins);
    }

    public Node createNode(Node parent, String name, String primaryType, String... mixins) throws RepositoryException {
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

    public void ensurePropertyRemoved(Node node, String name) throws RepositoryException {
        if (node.hasProperty(name)) {
            node.getProperty(name).remove();
        }
    }

    public void ensureMixinRemoved(Node node, String type) throws RepositoryException {
        if(node.isNodeType(type)) {
            node.removeMixin(type);
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
        return findOneQuery(session, queryTemplate, Query.SQL, args);
    }

    public Node findFirst(Session session, String queryTemplate, Object... args) throws RepositoryException {
        String sql = String.format(queryTemplate, args);
        Query queryObj = session.getWorkspace().getQueryManager().createQuery(sql, Query.SQL);
        QueryResult result = queryObj.execute();
        return result.getNodes().hasNext()
                ? result.getNodes().nextNode()
                : null;
    }

    public Node findOneQuery(Session session, String queryTemplate, String type, Object... args) throws RepositoryException {
        String sql = String.format(queryTemplate, args);
        Query queryObj = session.getWorkspace().getQueryManager().createQuery(sql, type);
        QueryResult result = queryObj.execute();
        LOG.info("findOneQuery: {}", sql);
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

    public void sortChildren(Node node) throws RepositoryException {
        sortChildren(node, false);
    }

    public void sortChildren(Node node, boolean reverse) throws RepositoryException {
        List<String> sortedNames = sortedNames(node.getNodes());
        if (reverse) {
            Collections.reverse(sortedNames);
        }
        for (int i = sortedNames.size() - 1; i >= 0; i--) {
            String before = sortedNames.get(i);
            String after = i < sortedNames.size() - 1 ? sortedNames.get(i + 1) : null;
            node.orderBefore(before, after);
        }
    }

    /**
     * Sort the nodes in an iterator, Folders in alphabetical order first then other documents in alphabetical order.
     */
    private List<String> sortedNames(NodeIterator it) throws RepositoryException {

        List<String> folders = new ArrayList<>();
        List<String> others = new ArrayList<>();
        while (it.hasNext()) {
            Node next = it.nextNode();
            if (isHippoFolder(next)) {
                folders.add(next.getName());
            } else {
                others.add(next.getName());
            }
        }
        folders.sort(String::compareToIgnoreCase);
        others.sort(String::compareToIgnoreCase);
        List<String> names = new ArrayList<>();
        names.addAll(folders);
        names.addAll(others);
        return names;
    }

    public boolean isHippoFolder(Node node) throws RepositoryException {
        return "hippostd:folder".equals(node.getPrimaryNodeType().getName());
    }

    void createMirror(Node publicationNode, String propertyName, Node handle) throws RepositoryException {
        Node mirror = createNode(publicationNode, propertyName, "hippo:mirror");
        mirror.setProperty("hippo:docbase", handle.getIdentifier());
    }

    public Node getVariant(Node node) throws RepositoryException {
        return getVariant(node.getNodes(node.getName()));
    }

    public Node getVariant(NodeIterator it) throws RepositoryException {
        Map<String, Node> byState = new HashMap<>();
        apply(it,
                this::hasState,
                node -> byState.put(node.getProperty(HIPPOSTD_STATE).getString(), node));
        return firstNonNull(
                byState.get("published"),
                byState.get("unpublished"),
                byState.get("draft"));
    }

    boolean hasState(Node node) throws RepositoryException {
        return node.hasProperty(HIPPOSTD_STATE);
    }

    public void apply(NodeIterator it, ThrowingPredicate predicate, ThrowingConsumer consumer) throws RepositoryException {
        while (it.hasNext()) {
            Node node = it.nextNode();
            if (predicate.test(node)) {
                consumer.accept(node);
            }
        }
    }

    public void removePublicationFolderQuietly(Node publicationFolder, Node imagesFolder) {

        if (publicationFolder == null) {
            return;
        }

        try {
            if (imagesFolder != null) {
                imagesFolder.remove();
            }
            Session session = publicationFolder.getSession();
            publicationFolder.remove();
            session.save();
        } catch (RepositoryException e) {
            LOG.error("Failed to remove publication folder after exception", e);
        }
    }
}
