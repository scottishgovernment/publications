package scot.gov.publications.hippo.rewriter;

import org.junit.Test;
import org.mockito.Mockito;
import scot.gov.publications.hippo.HippoUtilsTest;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.util.*;

import static org.mockito.Mockito.*;
import static scot.gov.publications.hippo.rewriter.LinkRewriter.CONTENT_ATTRIB;

public class PublicationLinkRewriterTest {


    @Test
    public void rewritesAllLocalLinks() throws Exception {
        // ARRANGE
        Node publicationFolderNode = mock(Node.class);
        Node pagesNode = mock(Node.class);

        Node page1 = pageNodeWithContent(htmlWithLinkToPage2());
        Node page2 = pageNodeWithContent(htmlWithLinkToPage1());
        Node page3 = pageNodeWithContent(htmlWithEmptyAnchors());
        Node page4 = pageNodeWithContent(htmlWithExternalLinks());
        Node page5 = pageNodeWithContent(htmlWithInternalLinkThatIsNotRecognised());
        Node page6 = pageNodeWithContent(htmlWithLinkToLocalAnchorOnOtherPage());

        List<Node> pageHandles = new ArrayList<>();
        Collections.addAll(pageHandles,
                handleWithNode(page1, "page1"),
                handleWithNode(page2, "page2"),
                handleWithNode(page3, "page3"),
                handleWithNode(page4, "page4"),
                handleWithNode(page5, "page5"),
                handleWithNode(page6, "page6"));
        when(pagesNode.getNodes()).thenReturn(HippoUtilsTest.iterator(pageHandles));
        when(publicationFolderNode.getNode("pages")).thenReturn(pagesNode);

        Map<String, Node> pageNodesByEntryname = new HashMap<>();
        pageNodesByEntryname.put("page1", page1);
        pageNodesByEntryname.put("page2", page2);
        PublicationLinkRewriter sut = new PublicationLinkRewriter("publication-slug", pageNodesByEntryname);
        sut.linkRewriter = mock(LinkRewriter.class);

        // ACT
        sut.rewrite(publicationFolderNode);

        // ASSERT
        Mockito.verify(sut.linkRewriter).rewriteLinkToFacet(page1, "page2", page2.getParent());
        Mockito.verify(sut.linkRewriter).rewriteLinkToFacet(page2, "page1", page1.getParent());
        Mockito.verify(sut.linkRewriter).rewriteWithoutFacet(page6, "page2#localanchor", "/publications/publication-slug/pages/page2#localanchor");
    }

    String htmlWithLinkToPage2() {
        return "<a href=\"page2\">link text</a>";
    }

    String htmlWithLinkToPage1() {
        return "<a href=\"page1\">link text</a>";
    }

    String htmlWithEmptyAnchors() {
        return "<a href=\"\"></a>";
    }

    String htmlWithExternalLinks() {
        return
                "<a href=\"https://www.google.com\"></a>" +
                "<a href=\"http://www.gov.scot/\"></a>";
    }

    String htmlWithInternalLinkThatIsNotRecognised() {
        return "<a href=\"unrecognised\"></a>";
    }

    String htmlWithLinkToLocalAnchorOnOtherPage() {
        return "<a href=\"page2#localanchor\"></a>";
    }

    Node handleWithNode(Node child, String name) throws RepositoryException {
        Node handle = mock(Node.class);
        when(child.getName()).thenReturn(name);
        when(handle.getNodes()).thenReturn(HippoUtilsTest.iterator(Collections.singletonList(child)));
        return handle;
    }

    Node pageNodeWithContent(String content) throws RepositoryException {
        Node node = Mockito.mock(Node.class);
        Node contentnode = Mockito.mock(Node.class);
        when(node.getNode("govscot:content")).thenReturn(contentnode);
        Property contentProp = mock(Property.class);
        when(contentProp.getString()).thenReturn(content);
        when(contentnode.getProperty(CONTENT_ATTRIB)).thenReturn(contentProp);
        return node;
    }

}
