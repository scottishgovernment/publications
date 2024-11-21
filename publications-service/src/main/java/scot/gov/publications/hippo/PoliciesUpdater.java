package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Update relationships with policies based on the metadata.
 */
public class PoliciesUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(PoliciesUpdater.class);

    Session session;

    HippoUtils hippoUtils = new HippoUtils();

    public PoliciesUpdater(Session session) {
        this.session = session;
    }

    /**
     * Ensure that all policies mentioned in the metadata link to this publication.
     */
    public void ensurePolicies(Node publicationNode, Metadata metadata) throws RepositoryException, ApsZipImporterException {
        for (String policy : metadata.getPolicies()) {
            ensurePolicy(publicationNode, policy);
        }
    }

    private void ensurePolicy(Node publicationNode, String policy) throws RepositoryException, ApsZipImporterException {

        LOG.info("ensurePolicy {} -> {} ", policy, publicationNode.getPath());

        // find the policy latest node
        Node policyLatest = findPolicyLatestNode(policy);
        if (policyLatest == null) {
            LOG.warn("Unable to find policy latest page for policy {}", policy);
            throw new ApsZipImporterException(String.format("Unable to find policy latest page for policy: '%s'", policy));
        }

        Node policyLatestHandle = policyLatest.getParent();
        Node publicationHandle = publicationNode.getParent();
        if (alreadyRelated(policyLatest, publicationHandle)) {
            LOG.info("Policy is already related to {}", publicationNode.getPath());
        } else {
            NodeIterator it = policyLatestHandle.getNodes(policyLatestHandle.getName());
            hippoUtils.apply(it, v -> true, v -> hippoUtils.createMirror(v, "govscot:relatedItems", publicationHandle));
        }
    }

    private Node findPolicyLatestNode(String policy) throws RepositoryException {
        String sql = "SELECT * FROM govscot:PolicyLatest WHERE jcr:path LIKE " +
                "'/content/documents/govscot/policies/%s/latest/%%' AND hippostd:state = 'published'";
        return hippoUtils.findOne(session, sql, policy);
    }

    private boolean alreadyRelated(Node policyLatest, Node publication) throws RepositoryException {
        NodeIterator nodeIterator = policyLatest.getNodes("govscot:relatedItems");
        while (nodeIterator.hasNext()) {
            Node relatedItem = nodeIterator.nextNode();
            if (relatedItem.getProperty("hippo:docbase").getString().equals(publication.getIdentifier())) {
                return true;
            }
        }
        return false;
    }

}