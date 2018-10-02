package scot.gov.publications.hippo.pages;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import scot.gov.publications.ApsZipImporterException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class HtmlUtilTest {

    HtmlUtil sut = new HtmlUtil();

    @Test
    public void getTitleUsesIndexIfNoH3() throws Exception {
        // ARRANGE
        Element div = mock(Element.class);
        Elements emptyElements = mock(Elements.class);
        when(emptyElements.isEmpty()).thenReturn(true);
        when(div.select("h3")).thenReturn(emptyElements);

        // ACT
        String actual = sut.getTitle(div, 2);

        // ASSERT
        assertEquals("Page 2", actual);
    }

    @Test
    public void getTitleUsesH3IfPresent() throws Exception {
        // ARRANGE
        Element div = mock(Element.class);
        Elements elements = mock(Elements.class);
        Element heading = mock(Element.class);
        when(elements.isEmpty()).thenReturn(false);
        when(heading.text()).thenReturn("heading");
        when(elements.get(0)).thenReturn(heading);
        when(div.select("h3")).thenReturn(elements);

        // ACT
        String actual = sut.getTitle(div, 2);

        // ASSERT
        assertEquals("heading", actual);
    }

    @Test(expected = ApsZipImporterException.class)
    public void getMainTextThrowsExceptionIfDivNotPresent() throws Exception {
        // ARRANGE
        Document htmlDoc = Jsoup.parse("<html><body><p>hello</p></body></html>");

        // ACT
        sut.getMainText(htmlDoc);

        // ASSERT - see expected
    }

    @Test
    public void getMainTextReturnsExpectedDivIfPresent() throws Exception {
        // ARRANGE
        Document htmlDoc = Jsoup.parse("<html><body><div class=\"mainText\">hello</body></html>");

        // ACT
        String actual = sut.getMainText(htmlDoc).html();

        // ASSERT
        assertEquals("hello", actual);
    }

    @Test
    public void isContentPageReturnsFalseForNull() {
        assertEquals(false, sut.isContentsPage(null));
    }

    @Test
    public void isContentPageReturnsFalseForEmptyString() {
        assertEquals(false, sut.isContentsPage(""));
    }

    @Test
    public void isContentPageReturnsFalseForHtmlWithNoH3() {
        assertEquals(false, sut.isContentsPage("<html><body><h1>hello</h1></body></html"));
    }

    @Test
    public void isContentPageReturnsFalseForHtmlWithNoUnrecognisedH3() {
        assertEquals(false, sut.isContentsPage("<html><body><h3>hello</h3></body></html"));
    }

    @Test
    public void isContentPageReturnsTrueForHtmlWithRecognisedH3() {
        Set<String> recognisedH3s = new HashSet<>();
        Collections.addAll(recognisedH3s,
                "Contents", "contents", "CONTENTS", "Table of Contents",
                "table of contents", "TABLE OF CONTENTS");
        for (String h3 : recognisedH3s) {
            String html = String.format("<html><body><h3>%s</h3></body></html>", h3);
            assertEquals(h3, true, sut.isContentsPage(html));
        }
    }

}
