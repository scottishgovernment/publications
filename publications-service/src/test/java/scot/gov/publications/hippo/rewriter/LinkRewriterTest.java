package scot.gov.publications.hippo.rewriter;

import org.junit.Test;
import scot.gov.publications.hippo.HippoUtilsTest;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkRewriterTest {

    @Test
     public void rewriteLinkToNodeWhenFacetAlreadyExists() throws RepositoryException {

        // ARRANGE
        LinkRewriter sut = new LinkRewriter();

        Node pageNode = mock(Node.class);
        Node contentNode = mock(Node.class);
        when(pageNode.getNode("govscot:content")).thenReturn(contentNode);

        Property htmlProp = mock(Property.class);
        when(htmlProp.getString()).thenReturn("<a href=\"from\"");

        when(contentNode.getProperty(LinkRewriter.CONTENT_ATTRIB)).thenReturn(htmlProp);

        Node facet = mock(Node.class);
        NodeType nodeType = mock(NodeType.class);
        when(nodeType.getName()).thenReturn("hippo:facetselect");
        when(facet.getPrimaryNodeType()).thenReturn(nodeType);
        when(facet.getName()).thenReturn("facetname");
        Property docbaseProp = mock(Property.class);
        when(docbaseProp.getString()).thenReturn("to-node-id");
        when(facet.getProperty("hippo:docbase")).thenReturn(docbaseProp);
        when(contentNode.getNodes()).thenReturn(HippoUtilsTest.iterator(Collections.singleton(facet)));

        Node toNode = mock(Node.class);
        when(toNode.getIdentifier()).thenReturn("to-node-id");

        // ACT
        sut.rewriteLinkToFacet(pageNode, "from", toNode);

        // ARRANGE
        verify(contentNode, never()).addNode(eq("hippo:facetselect"), any());
        verify(contentNode).setProperty(LinkRewriter.CONTENT_ATTRIB, "<a href=\"facetname\"");
    }

    @Test
    public void rewriteLinkToNodeWhenFacetDoesNotExist() throws RepositoryException {

        // ARRANGE
        LinkRewriter sut = new LinkRewriter();

        Node pageNode = mock(Node.class);
        Node contentNode = mock(Node.class);
        when(pageNode.getNode("govscot:content")).thenReturn(contentNode);

        Property htmlProp = mock(Property.class);
        when(htmlProp.getString()).thenReturn("<a href=\"from\"");

        when(contentNode.getProperty(LinkRewriter.CONTENT_ATTRIB)).thenReturn(htmlProp);

        Node facet = mock(Node.class);
        NodeType nodeType = mock(NodeType.class);
        when(nodeType.getName()).thenReturn("hippo:facetselect");
        when(facet.getPrimaryNodeType()).thenReturn(nodeType);
        when(facet.getName()).thenReturn("facetname1");
        Property docbaseProp = mock(Property.class);
        when(docbaseProp.getString()).thenReturn("anotherlink");
        when(facet.getProperty("hippo:docbase")).thenReturn(docbaseProp);

        Node newfacet = mock(Node.class);
        when(contentNode.addNode(any(), eq("hippo:facetselect"))).thenReturn(newfacet);
        when(newfacet.getPrimaryNodeType()).thenReturn(nodeType);
        when(newfacet.getName()).thenReturn("facetname2");
        when(docbaseProp.getString()).thenReturn("anotherlink");
        when(facet.getProperty("hippo:docbase")).thenReturn(docbaseProp);

        List<Node> facets = new ArrayList<>();
        Collections.addAll(facets, facet);
        when(contentNode.getNodes()).thenReturn(HippoUtilsTest.iterator(facets));

        Node toNode = mock(Node.class);
        when(toNode.getIdentifier()).thenReturn("to-node-id");

        // ACT
        sut.rewriteLinkToFacet(pageNode, "from", toNode);

        // ARRANGE
        verify(contentNode).addNode("to-node-id", "hippo:facetselect");
        verify(contentNode).setProperty(LinkRewriter.CONTENT_ATTRIB, "<a href=\"facetname2\"");
    }
}
