package scot.gov.publications.hippo.pages;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.containsAny;

public class LinksWithMarkupTest {

    /**
     This uses the same logic as the PublicationPageUpdater
     assertLinksDoNotContainMarkup() method.
    */

    @Test
    public void allowsKnownGoodUrls() {
        List<String> urls = new ArrayList<>();

        // Known link types
        urls.add("/pathurl");
        urls.add("/pathwithextension.htm");
        urls.add("../relativeurl");
        urls.add("../relativeurlextension.htm");

        // Known valid protocols
        urls.add("https://www.gov.scot/relativeurl");
        urls.add("https://www.gov.scot/pathwithextension.htm");
        urls.add("http://www.gov.scot/relativeurl");
        urls.add("mailto:address@email.com");
        urls.add("tel:010293930"); //These do not seem to be explicitly used in APS ZIPs

        // No chevrons are expected, so assert false
        for (String url : urls) {
            Assert.assertFalse("URL should be valid: " + url, containsMarkup(url));
        }
    }

    @Test
    public void rejectsKnownBadUrls() {
        List<String> urls = new ArrayList<>();

        // Chevrons
        urls.add("/pathurl<abbr>ISBN</abbr>");
        urls.add("/pathwithextension.htm<a href=''></a>");
        urls.add("../relativeurl<abbr>ADP</abbr>");
        urls.add("../relativeurlextension<abbr>test</abbr>.htm");
        urls.add("../relativeurlextension<abbr>test</abbr>.htm");

        // Chevrons are expected, so assert true
        for (String url : urls) {
            Assert.assertTrue("URL should be rejected: " + url, containsMarkup(url));
        }
    }

//    @Test
//    public void worksWithKnownProtocols() {
//        List<String> urls = new ArrayList<>();
//        urls.add("https://www.gov.scot/relativeurl");
//        urls.add("https://www.gov.scot/pathwithextension.htm");
//        urls.add("http://www.gov.scot/relativeurl");
//        urls.add("mailto:address@email.com");
//        urls.add("tel:010293930"); //These do not seem to be explicitly used in APS ZIPs
//
//        // No chevrons are expected, so assert false
//        for (String url : urls) {
//            Assert.assertFalse("URL should be valid: " + url, containsMarkup(url));
//        }
//    }

    public boolean containsMarkup(String url) {
        return containsAny(url, '<', '>');
    }

}