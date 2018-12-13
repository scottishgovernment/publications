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
import java.util.ArrayList;
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
     * @param title title to match (partial case insensitive
     * @param isbn isbn to match (partial case insensitive
     * @param filename filename to match (partial case insensitive
     * @return Collection of matching publications
     * @throws PublicationRepositoryException if it fails to list publications
     */
    public ListResult list(int page, int size, String title, String isbn, String filename)
            throws PublicationRepositoryException {
        BeanListHandler<Publication> handler = new BeanListHandler<>(Publication.class);
        try {

            List<Publication> publications;

            // Performs a paged query with optional search parameters
            List<Object> whereArgs = new ArrayList<>();
            StringBuilder whereClause = new StringBuilder();
            if (StringUtils.isNotBlank(title)) {
                whereClause.append("LOWER(title) LIKE ? AND ");
                whereArgs.add(like(title));
            }

            if (StringUtils.isNotBlank(isbn)) {
                whereClause.append("LOWER(isbn) LIKE ? AND ");
                whereArgs.add(like(isbn));
            }

            if (StringUtils.isNotBlank(filename)) {
                whereClause.append("LOWER(filename) LIKE ? AND ");
                whereArgs.add(like(filename));
            }

            // any clauses are nded with true - this is just a trick to make the query the same even
            // if no clauses were specified
            whereClause.append("true");

            // we specify the search params twice soince the same ones are used in both sides of the join
            List<Object> args = new ArrayList<>();
            args.addAll(whereArgs);
            args.add(size);
            args.add((page - 1) * size);
            args.addAll(whereArgs);

            String sql = listSQL.replaceAll("<WHERECLAUSE>", whereClause.toString());
            publications = queryRunner.query(sql, handler, args.toArray());
            int totalsize = publications.isEmpty() ? 0 : publications.get(0).getFullcount();
            return ListResult.result(publications, totalsize, page, size);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list publications", e);
        }
    }

    /**
     * Wrap a term in % to perform a partial match
     */
    private String like(String term) {
        return String.format("%%%s%%", term.toLowerCase());
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
