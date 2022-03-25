package scot.gov.publications.hippo.searchjournal;


public enum FunnelbackCollection {
    NEWS("govscot~ds-news-push"),
    PUBLICATIONS("govscot~ds-publications-push"),
    FOI("govscot~ds-foi-eir-release-push"),
    STATS_AND_RESEARCH("govscot~ds-statistics-research-push"),
    JOURNAL("govscot~ds-journal-push");

    private final String collectionName;

    FunnelbackCollection(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getCollectionName() {
        return collectionName;
    }
}