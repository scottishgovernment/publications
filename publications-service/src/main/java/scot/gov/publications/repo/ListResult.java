package scot.gov.publications.repo;

import java.util.List;

public class ListResult {

    private int totalSize;
    private int page;
    private int pageSize;
    private List<Publication> publications;

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<Publication> getPublications() {
        return publications;
    }

    public void setPublications(List<Publication> publications) {
        this.publications = publications;
    }

    public static ListResult result(List<Publication> publications, int totalSize, int page, int pageSize) {
        ListResult result = new ListResult();
        result.totalSize = totalSize;
        result.page = page;
        result.pageSize = pageSize;
        result.publications = publications;
        return result;
    }
}
