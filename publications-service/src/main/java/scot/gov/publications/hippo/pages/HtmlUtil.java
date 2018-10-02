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

public class HtmlUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HtmlUtil.class);

    String getTitle(Element div, int index) {
        List<Element> headings = div.select("h3");
        if (headings.isEmpty()) {
            LOG.warn("Page does not contain an h3, will use page number to format page title {}", index);
            return String.format("Page %d", index);
        }
        return headings.get(0).text();
    }

    Element getMainText(Document htmlDoc) throws ApsZipImporterException {
        List<Element> elements = htmlDoc.select(".mainText");
        if (elements.size() != 1) {
            throw new ApsZipImporterException("Page does not contain a single .mainText div");
        }
        return elements.get(0);
    }

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
                .toLowerCase();
        return "contents".equals(txt) || "table of contents".equals(txt);
    }

}
