package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.metadata.Metadata;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static scot.gov.publications.hippo.XpathQueryHelper.topicHandleQuery;

/**
 * Specifies how topics should be mapped.
 */
public class TopicMappings {

    private static final Logger LOG = LoggerFactory.getLogger(TopicMappings.class);

    private static final String RESEARCH = "Research";

    private Map<String, String> topics = new HashMap<>();

    Session session;

    HippoUtils hippoUtils = new HippoUtils();

    public TopicMappings(Session session) {
        this.session = session;

        // Mappings from legacy topic to the new topics.
        topics.put("Agriculture", "Farming and rural");
        topics.put("Arts and Culture", "arts-culture-and-sport");
        topics.put("Business and Industry", "business-industry-and-innovation");
        topics.put("Economy", "economy");
        topics.put("Education and Training", "education");
        topics.put("Environment", "environment-and-climate-change");
        topics.put("Energy", "energy");
        topics.put("Health and Community Care", "health-and-social-care");
        topics.put("Housing", "housing");
        topics.put("International", "international");
        topics.put("Law, Order and Public Safety", "law-and-order");
        topics.put("Marine and Fisheries", "marine-and-fisheries");
        topics.put("People and Society", "communities-and-third-sector");
        topics.put("Planning", RESEARCH.toLowerCase());
        topics.put("Public Sector", "public-sector");
        topics.put("Regeneration", "building-planning-and-design");
        topics.put(RESEARCH, RESEARCH.toLowerCase());
        topics.put("Statistics", "statistics");
        topics.put("Sustainable Development", "environment-and-climate-change");
    }

    /**
     * The initial version of the metadata was backed by the business solutions api had a single topic.  This is
     * contained in the metadata.topic field.
     *
     * We now want to consume this old metadata but also the new format which contains an array of topics.  This
     * will allow APS to start supplying the ne format.
     *
     * If the publicationNode already contains a topics node that was manually edited by a hippo user then this
     * code will not overwrite them and any topics in the metadata will be ignored.
     *
     * @param publicationNode The publication publicationNode to add the topics to.
     *
     * @param metadata
     * @throws RepositoryException
     */
    public void addTopicsIfAbsent(Node publicationNode, Metadata metadata) throws RepositoryException {

        // if this publicationNode already has topics set then do not do anything
        if (publicationNode.hasNode("govscot:topics")) {
            LOG.debug("Node already has topics, will not change them. {}", publicationNode.getPath());
            return;
        }

        for (String topic : getTopicsToAdd(metadata)) {
            addTopic(publicationNode, topic);
        }
    }

    private List<String> getTopicsToAdd(Metadata metadata) {
        // The initial version of metadata had a single topic (getTopic).  The new version specifies multiple
        // topics (getTopics).  To make this code backwardly compatible we build a list of all non blank
        // topics in both fields
        List<String> allTopics = new ArrayList<>();
        allTopics.add(metadata.getTopic());
        allTopics.addAll(metadata.getTopics());
        return allTopics.stream()
                .filter(topic -> isNotBlank(topic))
                .collect(toList());
    }

    private void addTopic(Node publicationNode, String topic) throws RepositoryException {
        // map the topic from the publications api to the one in Hippo
        String mappedTopic = getTopic(topic, publicationNode);
        if (mappedTopic == null) {
            return;
        }

        // get the topic node for this topic. Note, we do not need to escape the topic here since the topic will be a
        // known value from the topics map.
        Node topicNode = hippoUtils.findOneXPath(session, topicHandleQuery(mappedTopic));

        if (topicNode != null) {
            Node mirror = publicationNode.addNode("govscot:topics", "hippo:mirror");
            mirror.setProperty("hippo:docbase", topicNode.getIdentifier());
        } else {
            LOG.error("No topic found for topic {} for publicationNode {}", topic, publicationNode.getPath());
        }
    }

    private String getTopic(String key, Node node) throws RepositoryException {
        String value = topics.get(key);
        if (value == null) {
            LOG.warn("No mapping for topic: {}, topic will be ignored for node {}", key, node.getPath());
        }
        return value;
    }

}
