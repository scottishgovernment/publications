package scot.gov.publications.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository for storing publication uploads.
 *
 * This repo uses apache dbutils to store and fetch details from a posgres database.
 */
public class PublicationRepository {

    @Inject
    QueryRunner queryRunner;

    @Inject
    Clock clock;

    QueryLoader queryLoader = new QueryLoader();

    String insertSQL;

    String updateSQL;

    String listSQL;

    String waitingSQL;

    PublicationRepository() {
        insertSQL = queryLoader.loadSQL("/sql/insert.sql");
        updateSQL = queryLoader.loadSQL("/sql/update.sql");
        listSQL = queryLoader.loadSQL("/sql/list.sql");
        waitingSQL = queryLoader.loadSQL("/sql/waiting.sql");
    }

    /**
     * Create a new publication.
     *
     * @param publication Publication details to create.
     * @throws PublicationRepositoryException if the create failed.
     */
    public void create(Publication publication) throws PublicationRepositoryException {
        try {
            queryRunner.update(insertSQL, insertQueryArgs(publication));
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to create publication", e);
        }
    }

    /**
     * Update a publication.
     *
     * @param publication Publication details to update.
     * @throws PublicationRepositoryException if the update the publication.
     */
    public void update(Publication publication) throws PublicationRepositoryException {
        publication.setLastmodifieddate(Timestamp.from(clock.instant()));
        try {
            Object[] args = updateQueryArgs(publication);
            queryRunner.update(updateSQL, args);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to update publication", e);
        }
    }

    /**
     * Fetch a publication using its id.
     *
     * @param id id to retrieve
     * @return Publication with that id, null if none exists
     * @throws PublicationRepositoryException if the create failed.
     */
    public Publication get(String id) throws PublicationRepositoryException {
        String sql = "SELECT * FROM publication WHERE id = ?";
        try {
            return queryRunner.query(sql, new BeanHandler<>(Publication.class), id);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to get publication", e);
        }
    }

    /**
     * Paged list of publications with an optional search.
     *
     * @param page the page number to fetch
     * @param size the size of the page
     * @param queryTerm Optional search term.  This will perform a case insensitive partial match against the
     *                  title, filename and isbn columns.
     * @return Collection of matching publications
     * @throws PublicationRepositoryException if it fails to list publications
     */
    public ListResult list(int page, int size, String queryTerm) throws PublicationRepositoryException {
        BeanListHandler<Publication> handler = new BeanListHandler<>(Publication.class);
        try {

            List<Publication> publications;

            // Performs a paged query. If there is a query string we use a LIKE with a towlwere to get a case
            // insensitive partial match.  If there is no query string then it is or'd with true to return all
            // results.
            //
            // The join is in order to get the total number fo results to support paging.
            boolean hasQuery = StringUtils.isNotBlank(queryTerm);
            String queryExpression = hasQuery ? String.format("%%%s%%", queryTerm) : "";
            publications = queryRunner.query(listSQL,
                    handler,
                    !hasQuery,
                    queryExpression,
                    queryExpression,
                    queryExpression,
                    size,
                    (page - 1) * size,
                    !hasQuery,
                    queryExpression,
                    queryExpression,
                    queryExpression);
            int totalsize = publications.isEmpty() ? 0 : publications.get(0).getFullcount();
            return ListResult.result(publications, totalsize, page, size);

        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list publications", e);
        }
    }

    /**
     * Get a list of publications that are waiting to be processed, i.e. have not reached a terminal state.
     *
     * @return List of publications in a non terminal state.
     * @throws PublicationRepositoryException
     */
    public Collection<Publication> waitingPublications() throws PublicationRepositoryException {
        BeanListHandler<Publication> handler = new BeanListHandler<>(Publication.class);
        try {
            return queryRunner.query(waitingSQL, handler);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list publications", e);
        }
    }

    /**
     * Get the set of checksums stored in the repo.
     *
     * @return Set of checksums.
     * @throws PublicationRepositoryException
     */
    public Set<String> allChecksums() throws PublicationRepositoryException {
        try {
            List<String> checksumList = queryRunner.query("SELECT checksum FROM publication", new ColumnListHandler<String>(1));
            return new HashSet<>(checksumList);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list checksums", e);
        }
    }

    private Object[] insertQueryArgs(Publication publication) {
        Timestamp now = Timestamp.from(clock.instant());
        return new Object[] {
                publication.getId(),
                publication.getUsername(),
                publication.getTitle(),
                publication.getIsbn(),
                publication.getFilename(),
                publication.getEmbargodate(),
                publication.getState(),
                publication.getStatedetails(),
                publication.getChecksum(),
                now,
                now
        };
    }

    private Object[] updateQueryArgs(Publication publication) {
        Timestamp now = Timestamp.from(clock.instant());
        return new Object[] {
                publication.getUsername(),
                publication.getTitle(),
                publication.getIsbn(),
                publication.getFilename(),
                publication.getEmbargodate(),
                publication.getState(),
                publication.getStatedetails(),
                publication.getChecksum(),
                now,
                publication.getId()
        };
    }

}
