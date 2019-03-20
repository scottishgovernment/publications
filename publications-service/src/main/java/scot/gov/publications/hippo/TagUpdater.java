package scot.gov.publications.hippo;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.*;

import static scot.gov.publications.hippo.Constants.HIPPOSTD_TAGS;

/**
 * Logic used to update tags.
 */
public class TagUpdater {

    HippoUtils hippoUtils = new HippoUtils();

    /**
     * Update the tags on the publicationNode.  This will add any tags in the metadata that do not already appear.
     */
    public void updateTags(Node publicationNode, List<String> tags) throws RepositoryException {
        Set<String> allTags = existingTags(publicationNode);
        allTags.addAll(tags);
        hippoUtils.setPropertyStrings(publicationNode, HIPPOSTD_TAGS, allTags);
    }

    private Set<String> existingTags(Node publicationNode) throws RepositoryException {
        if (!publicationNode.hasProperty(HIPPOSTD_TAGS)) {
            return new HashSet<>();
        }

        Set<String> allTags = new HashSet<>();
        Value[] tagsValues = publicationNode.getProperty(HIPPOSTD_TAGS).getValues();
        for (Value tagvalue : tagsValues) {
            allTags.add(tagvalue.getString());
        }
        return allTags;
    }
}
