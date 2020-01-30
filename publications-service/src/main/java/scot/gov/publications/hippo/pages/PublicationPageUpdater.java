package scot.gov.publications.hippo.pages;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.hippo.HippoNodeFactory;
import scot.gov.publications.hippo.HippoPaths;
import scot.gov.publications.hippo.HippoUtils;
import scot.gov.publications.hippo.TitleSanitiser;
import scot.gov.publications.hippo.rewriter.PublicationLinkRewriter;
import scot.gov.publications.util.ZipEntryUtil;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static scot.gov.publications.hippo.Constants.GOVSCOT_CONTENT;
import static scot.gov.publications.hippo.Constants.GOVSCOT_TITLE;
import static scot.gov.publications.hippo.Constants.HIPPOSTD_FOLDERTYPE;

/**
 * Contains the logic used to ad page nodes from a zip file to a publicaiton folder.
 */
public class PublicationPageUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationPageUpdater.class);

    private static final String PAGES = "pages";

    HippoUtils hippoUtils = new HippoUtils();

    HtmlUtil htmlUtil = new HtmlUtil();

    Session session;

    HippoNodeFactory nodeFactory;

    HippoPaths hippoPaths;

    public PublicationPageUpdater(Session session, PublicationsConfiguration configuration) {
        this.session = session;
        this.nodeFactory = new HippoNodeFactory(session, configuration);
        this.hippoPaths = new HippoPaths(session);
    }

    /**
     * Ensure that the publication folder contains a pages folder containing a page node for each of the html pages
     * contained in the zip file.
     *
     * @param zipFile The zip file containing the publicaiton
     * @param publicationFolder Node of the folder containing the publication in the repo
     * @param filenameToImageId Map image filenames to the node of tat image in the repo
     * @param docnameToNode Map from the document name to the node of that document in the repo
     * @param publishDateTime The embargo date to use when creating page nodes
     * @throws ApsZipImporterException
     */
    public void addPages(
            ZipFile zipFile,
            Node publicationFolder,
            Map<String, String> filenameToImageId,
            Map<String, Node> docnameToNode,
            ZonedDateTime publishDateTime,
            boolean shouldEmbargo) throws ApsZipImporterException {

        try {
            // map the names of all pages we have created
            Map<String, Node> nodesByEntryname = doAddPages(
                    zipFile,
                    publicationFolder,
                    filenameToImageId,
                    publishDateTime,
                    shouldEmbargo);
            nodesByEntryname.putAll(docnameToNode);
            PublicationLinkRewriter linkRewriter = new PublicationLinkRewriter(publicationFolder.getName(), nodesByEntryname);
            linkRewriter.rewrite(publicationFolder);
        } catch (IOException | RepositoryException e) {
            throw new ApsZipImporterException("Failed too upload pages", e);
        }
    }

    private Map<String, Node> doAddPages(
            ZipFile zipFile,
            Node publicationFolder,
            Map<String, String> filenameToImageId,
            ZonedDateTime publishDateTime,
            boolean shouldEmbargo)
                throws IOException, RepositoryException, ApsZipImporterException {

        Node pages = ensurePagesNode(publicationFolder);
        pages.setProperty(HIPPOSTD_FOLDERTYPE, new String[]{"new-publication-page"});
        List<ZipEntry> htmlEntries = zipFile.stream().filter(ZipEntryUtil::isHtml).sorted(Comparator.comparing(ZipEntry::getName)).collect(toList());

        LOG.info("Adding {} pages", htmlEntries.size());
        Map<String, Node> pageNodesByEntry = new HashMap<>();
        int i = 0;
        for (ZipEntry htmlEntry : htmlEntries) {
            InputStream in = zipFile.getInputStream(htmlEntry);
            String pageContent = IOUtils.toString(in, UTF_8);
            Node pageNode = addPage(pages, pageContent, i, filenameToImageId, publishDateTime, shouldEmbargo);

            Path entryPath = java.nio.file.Paths.get(htmlEntry.getName());
            pageNodesByEntry.put(entryPath.getFileName().toString(), pageNode);
            i++;
        }
        return pageNodesByEntry;
    }

    private Node ensurePagesNode(Node publicationsFolder) throws RepositoryException {
        if (publicationsFolder.hasNode(PAGES)) {
            Node pages = publicationsFolder.getNode(PAGES);
            hippoUtils.removeChildren(pages);
            return pages;
        }
        return hippoPaths.folderNode(publicationsFolder, PAGES);
    }

    private Node addPage(
            Node pagesNode,
            String page,
            int index,
            Map<String, String> filenameToImageId,
            ZonedDateTime publishDateTime,
            boolean shouldEmbaro)
                throws RepositoryException, ApsZipImporterException {

        Document htmlDoc = Jsoup.parse(page);
        Element mainTextDiv = htmlUtil.getMainText(htmlDoc);
        String title = TitleSanitiser.sanitise(htmlUtil.getTitle(mainTextDiv, index));
        String slug = Integer.toString(index);
        Node pageHandle = nodeFactory.newHandle(title, pagesNode, slug);
        Node pageNode = nodeFactory.newDocumentNode(
                pageHandle, slug, title, "govscot:PublicationPage", publishDateTime, shouldEmbaro);
        nodeFactory.addBasicFields(pageNode, title);
        pageNode.setProperty(GOVSCOT_TITLE, title);
        createPageContentAndLinkImages(mainTextDiv, pageNode, filenameToImageId);
        return  pageNode;
    }

    private void createPageContentAndLinkImages(
            Element div,
            Node pageNode,
            Map<String, String> filenameToImageId)
                throws RepositoryException {

        Set<String> imageLinks = imageLinks(div, filenameToImageId);
        String rewrittenHtml = rewriteImageLinks(div.html(), imageLinks);
        Node contentNode = hippoUtils.ensureHtmlNode(pageNode, GOVSCOT_CONTENT, rewrittenHtml);
        createImageFacets(contentNode, imageLinks, filenameToImageId);
        pageNode.setProperty("govscot:contentsPage", htmlUtil.isContentsPage(rewrittenHtml));
    }

    private Set<String> imageLinks(Element div, Map<String, String> filenameToImageId) {
        return div
                .select("img")
                .stream()
                .map(el -> el.attr("src"))
                .filter(StringUtils::isNotBlank)
                .filter(src -> filenameToImageId.containsKey(src))
                .collect(toSet());
    }

    private String rewriteImageLinks(String html, Set<String> imageLinks) {
        String rewrittenContent = html;
        for (String imageLink : imageLinks) {
            String from = imageLink;
            String to = String.format("%s/{_document}/hippogallery:original", imageLink);
            rewrittenContent = rewrittenContent.replaceAll(from, to);
        }
        return rewrittenContent;
    }

    private void createImageFacets(
            Node contentNode,
            Set<String> imageLinks,
            Map<String, String> filenameToImageId) throws RepositoryException {

        // create facets for each of the images we know about
        Set<String> imageNames = imageLinks.stream()
                .filter(filenameToImageId::containsKey)
                .collect(toSet());
        for (String imageName : imageNames) {
            Node imgLink = hippoUtils.createNode(contentNode, imageName, "hippogallerypicker:imagelink");
            imgLink.setProperty("hippo:docbase", filenameToImageId.get(imageName));
            imgLink.setProperty("hippo:facets", new String[]{});
            imgLink.setProperty("hippo:modes", new String[]{});
            imgLink.setProperty("hippo:values", new String[]{});
        }
    }
}
