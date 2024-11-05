package scot.gov.publications.hippo.pages;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.ApsZipImporterException;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class HtmlUtilTest {

    HtmlUtil sut = new HtmlUtil();

    @Test
    public void getTitleUsesIndexIfNoH2OrH3() throws Exception {
        // ARRANGE
        Element div = mock(Element.class);
        Elements emptyElements = mock(Elements.class);
        when(emptyElements.isEmpty()).thenReturn(true);
        when(div.select("h3")).thenReturn(emptyElements);
        when(div.select("h2")).thenReturn(emptyElements);

        // ACT
        String actual = sut.getTitle(div, 2);

        // ASSERT
        assertEquals("Page 2", actual);
    }

    @Test
    public void getTitleUsesH2IfPresent() throws Exception {
        // ARRANGE
        Element div = mock(Element.class);
        Elements elements = mock(Elements.class);
        Element heading = mock(Element.class);
        when(elements.isEmpty()).thenReturn(false);
        when(heading.text()).thenReturn("heading");
        when(elements.get(0)).thenReturn(heading);
        when(div.select("h2")).thenReturn(elements);

        // ACT
        String actual = sut.getTitle(div, 2);

        // ASSERT
        assertEquals("heading", actual);
    }

    @Test
    public void getTitleUsesH3IfNoH2Present() throws Exception {
        // ARRANGE
        Element div = mock(Element.class);
        Elements emptyElements = mock(Elements.class);
        when(emptyElements.isEmpty()).thenReturn(true);
        Elements elements = mock(Elements.class);
        Element heading = mock(Element.class);
        when(elements.isEmpty()).thenReturn(false);
        when(heading.text()).thenReturn("heading");
        when(elements.get(0)).thenReturn(heading);
        when(div.select("h2")).thenReturn(emptyElements);
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

    @Test
    public void allowsKnownGoodUrls() throws ApsZipImporterException {
        // ARRANGE
        Document doc = htmlDocumentWithLinks(goodUrls());

        // ACT
        sut.assertLinksDoNotContainMarkup(doc);

        // ASSERT - no exception should be thrown
    }

    @Test
    public void rejectsKnownBadUrls() throws ApsZipImporterException {

        // ARRANGE
        List<String> urls = new ArrayList<>();
        urls.addAll(goodUrls());
        urls.addAll(badUrls());
        urls.addAll(goodUrls());
        Document doc = htmlDocumentWithLinks(urls);

        // ACT
        ApsZipImporterException thrown = null;
        try {
            sut.assertLinksDoNotContainMarkup(doc);
        } catch (ApsZipImporterException e) {
            thrown = e;
        }

        // ASSERT - should have thrown an exception with the expected bad urls
        Assert.assertNotNull(thrown);
        String msg = thrown.getMessage();
        List<String> urlsInMessage = Arrays.asList(StringUtils.substringAfter(msg, "Invalid Links: ").split(","));
        Assert.assertEquals("invalid urls not as expected", urlsInMessage, badUrls());
    }

    List<String> goodUrls() {
        List<String> urls = new ArrayList<>();
        urls.add("/pathurl");
        urls.add("/pathwithextension.htm");
        urls.add("../relativeurl");
        urls.add("../relativeurlextension.htm");

        // Known valid protocols
        urls.add("https://www.gov.scot/relativeurl");
        urls.add("https://www.gov.scot/pathwithextension.htm");
        urls.add("http://www.gov.scot/relativeurl");
        urls.add("mailto:address@email.com");
        urls.add("tel:010293930");
        return urls;
    }

    List<String> badUrls() {
        List<String> urls = new ArrayList<>();
        urls.add("/pathurl<abbr>ISBN</abbr>");
        urls.add("/pathwithextension.htm<a href=''></a>");
        urls.add("../relativeurl<abbr>ADP</abbr>");
        urls.add("../relativeurlextension<abbr>test</abbr>.htm");
        urls.add("../relativeurlextension<abbr>test</abbr>.htm");
        return urls;
    }

    Document htmlDocumentWithLinks(List<String> links) {
        StringBuilder stringBuilder = new StringBuilder().append("<html><body>");
        stringBuilder.append(links.stream().map(this::toAnchorTag).collect(Collectors.joining("\n")));
        String html = stringBuilder.toString();
        return Jsoup.parse(html);
    }

    String toAnchorTag(String url) {
        return "<a href=\"" + url + "\">link text</a>";
    }


}
