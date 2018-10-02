package scot.gov.publications.hippo;

public final class Constants {

    private Constants() {
        // prevent instantiation
    }

    public static final String HIPPO_CONTAINER = "hippo:container";
    public static final String HIPPO_DERIVED = "hippo:derived";
    public static final String HIPPO_FILENAME = "hippo:filename";
    public static final String HIPPO_NAMED = "hippo:named";
    public static final String HIPPO_NAME = "hippo:name";

    public static final String HIPPOSTD_CONTAINER = "hippostd:container";
    public static final String HIPPOSTD_PUBLISHABLE = "hippostd:publishable";
    public static final String HIPPOSTD_PUBLISHABLESUMMARY = "hippostd:publishableSummary";
    public static final String HIPPOSTD_RELAXED = "hippostd:relaxed";
    public static final String HIPPOSTDDPUBWF_DOCUMENT = "hippostdpubwf:document";

    public static final String MIX_REFERENCEABLE = "mix:referenceable";
    public static final String MIX_SIMPLE_VERSIONABLE = "mix:simpleVersionable";

    public static final String GOVSCOT_TITLE = "govscot:title";
    public static final String GOVSCOT_CONTENT = "govscot:content";
    public static final String GOVSCOT_PAGE_COUNT = "govscot:pageCount";
    public static final String GOVSCOT_GOVSCOTURL = "govscot:govscoturl";


    public static final String JCR_DATA = "jcr:data";
    public static final String JCR_MIMETYPE = "jcr:mimeType";
    public static final String JCR_LAST_MODIFIED = "jcr:lastModified";

    public static final String[] DOCUMENT_MIXINS = {
            MIX_REFERENCEABLE,
            HIPPO_CONTAINER,
            HIPPOSTD_CONTAINER,
            HIPPO_DERIVED,
            HIPPOSTD_PUBLISHABLE,
            HIPPOSTD_PUBLISHABLESUMMARY,
            HIPPOSTD_RELAXED,
            HIPPOSTDDPUBWF_DOCUMENT
    };

    public static final String[] HANDLE_MIXINS = {
            MIX_REFERENCEABLE,
            HIPPO_NAMED,
            MIX_SIMPLE_VERSIONABLE
    };
}
