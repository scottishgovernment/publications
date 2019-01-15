package scot.gov.publications.hippo.pages;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;

import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Utility methods used to update publication pages.
 */
class HtmlUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HtmlUtil.class);

    /**
     * Determine the title of a publication page.  If the page contains any h3 elements then the first one will be used
     * as its title. Otherwise the index will be used to format a generic title e.g. "Page 1"
     */
    String getTitle(Element div, int index) {
        List<Element> headings = div.select("h3");
        if (headings.isEmpty()) {
            LOG.warn("Page does not contain an h3, will use page number to format page title {}", index);
            return String.format("Page %d", index);
        }
        return headings.get(0).text();
    }

    /**
     * Extract the contents of the .mainText div.
     *
     * @throws ApsZipImporterException If the document does not contains exactly one mainText div.
     */
    Element getMainText(Document htmlDoc) throws ApsZipImporterException {
        List<Element> elements = htmlDoc.select(".mainText");
        if (elements.size() != 1) {
            throw new ApsZipImporterException("Page does not contain a single .mainText div");
        }
        return elements.get(0);
    }

    /**
     * Determine if theis is a contents page.  Contents pages are ones whose first h3 element is either
     * "Content" or "Table of contents" (case insensitive)
     */
    boolean isContentsPage(String html) {
        if (StringUtils.isEmpty(html)) {
            return false;
        }

        Document htmlDoc = Jsoup.parse(html);
        Element heading = htmlDoc.select("h3").first();
        if (heading == null) {
            return false;
        }
        String txt = heading.childNodes()
                .stream()
                .map(org.jsoup.nodes.Node::toString)
                .map(String::toLowerCase)
                .collect(joining(""))
                .trim()
                .toLowerCase();
        return "contents".equals(txt) || "table of contents".equals(txt);
    }

}
