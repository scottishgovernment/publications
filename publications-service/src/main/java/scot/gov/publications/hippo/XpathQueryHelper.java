package scot.gov.publications.hippo;

/**
 * Helper for creating xpath queries.
 */
public class XpathQueryHelper {

    private XpathQueryHelper() {
        // utility class only
    }

    public static String directorateHandleQuery(String directorate) {
        // find the directorate that is in a folder with the name 'directorate'
        return new StringBuilder("/jcr:root/content/documents/govscot//")
                .append(element(directorate, "hippostd:folder")).append("//")
                .append(element("*", "govscot:Directorate"))
                .append(publishedPredicate())
                .append("/..")
                .toString();
    }

    public static String personHandleQuery(String person) {
        return handleQuery("govscot:Person", person);
    }

    public static String roleHandleQuery(String person) {
        return handleQuery("govscot:Role", person);
    }

    public static String topicHandleQuery(String topic) {
        return new StringBuilder("/jcr:root/content/documents/govscot/topics//")
                .append(element(topic, "govscot:Topic"))
                .append(publishedPredicate())
                .append("/..")
                .toString();
    }

    private static String handleQuery(String type, String name) {
        // find a handle based on the name.  And example query for a role woukd be:
        // /jcr:root/content/documents/govscot/about//element(first-minister, govscot:Role)[hippostd:state = 'published']/..
        return new StringBuilder("/jcr:root/content/documents/govscot//")
                .append(element(name, type))
                .append(publishedPredicate())
                .append("/..")
                .toString();
    }

    private static String element(String name, String type) {
        return String.format("element(%s, %s)", name, type);
    }

    private static String publishedPredicate() {
        return "[hippostd:state = 'published']";
    }

}