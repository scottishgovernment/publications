package scot.gov.publications.hippo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

public class TopicMappings {

    private static final Logger LOG = LoggerFactory.getLogger(TopicMappings.class);

    private static final String RESEARCH = "Research";

    private Map<String, String> topics = new HashMap<>();

    Session session;

    HippoUtils hippoUtils = new HippoUtils();


    public TopicMappings(Session session) {
        this.session = session;

        // Verify if this is the right list there are some topics missing I think - e.g. Building?
        topics.put("Agriculture", "Farming and rural");
        topics.put("Arts and Culture", "Arts, culture and sport");
        topics.put("Business and Industry", "Business, industry and innovation");
        topics.put("Economy", "Economy");
        topics.put("Education and Training", "Education");
        topics.put("Environment", "Environment and climate change");
        topics.put("Energy", "Energy");
        topics.put("Health and Community Care", "Health and social care");
        topics.put("Housing", "Housing");
        topics.put("International", "International");
        topics.put("Law, Order and Public Safety", "Law and order");
        topics.put("Marine and Fisheries", "Marine and fisheries");
        topics.put("People and Society", "Communities and third sector");
        topics.put("Planning", RESEARCH);
        topics.put("Public Sector", "Public sector");
        topics.put("Regeneration", "Building, planning and design");
        topics.put(RESEARCH, RESEARCH);
        topics.put("Statistics", "Statistics");
        topics.put("Sustainable Development", "Environment and climate change");
    }

    public void updateTopics(Node node, String topic) throws RepositoryException {

        // if this node already has topics set then do not do anything
        if (node.hasNode("govscot:topics")) {
            LOG.info("{} already had topics, ignoring", node.getPath());
            return;
        }

        // map the topic from the publications api to the one in Hippo
        String mappedTopic = getTopic(topic);
        if (mappedTopic == null) {
            return;
        }

        // get the topic node for this topic
        String template = "SELECT * FROM govscot:Topic WHERE hippostd:state = 'published' AND govscot:title = '%s'";
        Node topicNode = hippoUtils.findOne(session, template, mappedTopic);

        if (topicNode != null) {
            Node mirror = node.addNode("govscot:topics", "hippo:mirror");
            mirror.setProperty("hippo:docbase", topicNode.getIdentifier());
        } else {
            LOG.info("No topic found for {}", topic);
        }
    }

    private String getTopic(String key) {
        String value = topics.get(key);
        if (value == null) {
            LOG.warn("{} topic is not mapped, ignoring topic field", key);
        }
        return value;
    }

}
