package scot.gov.publications.hippo;


import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathsTest {

    @Test
    public void ensurePath() throws Exception {

        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one", "two");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootNode();

        when(sut.session.getNode(HippoPaths.ROOT)).thenReturn(root);

        // ACT
        sut.ensurePath(path);
    }

    @Test
    public void ensureImagePath() throws Exception {

        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one", "two");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootImgNode();

        when(sut.session.getNode(HippoPaths.IMG_ROOT)).thenReturn(root);

        // ACT
        sut.ensureImagePath(path);
    }

    @Test
    public void slugifyReturnsExpectedSlugs() {
        // ARRANGE
        Map<String, String> cases = new HashMap<>();
        cases.put("This is a title", "title");
        cases.put("This is a title with apostophies'''", "title-apostophies");
        cases.put("This is a \"title\"", "title");
        cases.put("This is a title : with a subtitle", "title-subtitle");
        cases.put("This is a title (with brackets)", "title-brackets");
        cases.put("This is a title - with - some - hyphens", "title-hyphens");

        HippoPaths sut = new HippoPaths(null);

        // ACT
        for (Map.Entry<String, String> test : cases.entrySet()) {

            // ASSERT
            assertEquals(test.getValue(), sut.slugify(test.getKey()));
        }
    }

    @Test
    public void folderNodeSetsHasFolders() throws Exception {
        // ARRANGE
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node parent = mock(Node.class);
        Node child = mock(Node.class);
        when(parent.addNode(any(), eq("hippostd:folder"))).thenReturn(child);

        // ACT
        sut.folderNode(parent, "test");

        // ASSERT
        verify(parent).setProperty("hippostd:hasfolders", true);
    }

    @Test
    public void ensurePathSetsHasFolders() throws Exception {
        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one", "two");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootNode();
        Node one = root.getNode("one");

        when(sut.session.getNode(HippoPaths.ROOT)).thenReturn(root);

        // ACT
        sut.ensurePath(path);

        // ASSERT — "two" is new so its parent ("one") should have hasfolders set
        verify(one).setProperty("hippostd:hasfolders", true);
    }

    @Test
    public void ensurePathDoesNotSetHasFoldersWhenExists() throws Exception {
        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootNode();

        when(sut.session.getNode(HippoPaths.ROOT)).thenReturn(root);

        // ACT — "one" already exists so no folder is created and hasfolders must not be set
        sut.ensurePath(path);

        // ASSERT
        verify(root, never()).setProperty(eq("hippostd:hasfolders"), eq(true));
    }

    @Test
    public void ensureImagePathSetsHasFolders() throws Exception {
        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one", "two");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootImgNode();
        Node one = root.getNode("one");

        when(sut.session.getNode(HippoPaths.IMG_ROOT)).thenReturn(root);

        // ACT
        sut.ensureImagePath(path);

        // ASSERT — "two" is new so its parent ("one") should have hasfolders set
        verify(one).setProperty("hippostd:hasfolders", true);
    }

    @Test
    public void ensureImagePathDoesNotSetHasFoldersWhenExists() throws Exception {
        // ARRANGE
        List<String> path = new ArrayList<>();
        Collections.addAll(path, "one");
        Session session = mock(Session.class);
        HippoPaths sut = new HippoPaths(session);
        Node root = rootImgNode();

        when(sut.session.getNode(HippoPaths.IMG_ROOT)).thenReturn(root);

        // ACT — "one" already exists so no image folder is created and hasfolders must not be set
        sut.ensureImagePath(path);

        // ASSERT
        verify(root, never()).setProperty(eq("hippostd:hasfolders"), eq(true));
    }

    Node rootNode() throws RepositoryException {
        Node node = mock(Node.class);
        Node one = mock(Node.class);
        Node two = mock(Node.class);
        when(node.hasNode(eq("one"))).thenReturn(true);
        when(node.getNode("one")).thenReturn(one);
        when(one.hasNode("two")).thenReturn(false);
        when(node.addNode(any(), eq("hippostd:folder"))).thenReturn(one);
        when(one.addNode(any(), eq("hippostd:folder"))).thenReturn(two);
        return node;
    }

    Node rootImgNode() throws RepositoryException {
        Node node = mock(Node.class);
        Node one = mock(Node.class);
        Node two = mock(Node.class);
        when(node.hasNode(eq("one"))).thenReturn(true);
        when(node.getNode("one")).thenReturn(one);
        when(one.hasNode("two")).thenReturn(false);
        when(node.addNode(any(), eq("hippogallery:stdImageGallery"))).thenReturn(one);
        when(one.addNode(any(), eq("hippogallery:stdImageGallery"))).thenReturn(two);
        return node;
    }
}
