package scot.gov.publications.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

/**
 * Repository for storing publicaiton uploads.
 *
 * This repo uses apache dbutils to store and fetch details from a psogres database.
 */
public class PublicationRepository {

    @Inject
    QueryRunner queryRunner;

    @Inject
    TimestampSource timestampSource;

    /**
     * Create a new publication.
     *
     * @param publication Publication details to create.
     * @throws PublicationRepositoryException if the create failed.
     */
    public void create(Publication publication) throws PublicationRepositoryException {
        String sql = "INSERT INTO publication" +
                "(id, title, isbn, embargodate, state, statedetails, stacktrace, checksum, createddate, lastmodifieddate) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            queryRunner.update(sql, insertQueryArgs(publication));
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to create publication", e);
        }
    }

    /**
     * Update a publication.
     *
     * @param publication Publication details to update.
     * @throws PublicationRepositoryException if the update the publicaiton.
     */
    public void update(Publication publication) throws PublicationRepositoryException {
        publication.setLastmodifieddate(timestampSource.now());
        String sql = "UPDATE publication SET " +
                "title = ?, " +
                "isbn = ?, " +
                "embargodate = ?, " +
                "state = ?, " +
                "statedetails = ?, " +
                "stacktrace = ?, " +
                "checksum = ?, " +
                "lastmodifieddate = ? " +
                "WHERE id = ?";
        try {
            Object[] args = updateQueryArgs(publication);
            queryRunner.update(sql, args);
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
     * @param queryTerm Optional search term.  This will perform a case insensitive partial match against the title
     *                  and isbn columns.
     * @return Collection of matching publications
     * @throws PublicationRepositoryException if it fails to list publicaitons
     */
    public ListResult list(int page, int size, String queryTerm) throws PublicationRepositoryException {
        BeanListHandler<Publication> handler = new BeanListHandler<>(Publication.class);
        try {
            List<Publication> publications;

            if (StringUtils.isNotBlank(queryTerm)) {
                String sql =
                        "SELECT *, count(*) OVER() AS fullcount FROM publication " +
                        "WHERE isbn ~* ? OR title ~* ? " +
                        "ORDER BY lastmodifieddate DESC " +
                        "LIMIT ? OFFSET ?";
                // perform a case insensitive pattern match
                String queryExpression = String.format(".*%s.*", queryTerm);
                publications = queryRunner.query(sql.toString(), handler, queryExpression, queryExpression, size, (page - 1) * size);
            } else {
                String sql = "SELECT *, count(*) OVER() AS fullcount FROM publication ORDER BY lastmodifieddate DESC LIMIT ? OFFSET ?";
                publications = queryRunner.query(sql, handler, size, (page - 1) * size);
            }

            int totlasize = publications.isEmpty() ? 0 : publications.get(0).getFullcount();
            return ListResult.result(publications, totlasize, page, size);

        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list publications", e);
        }
    }

    /**
     * Get a list of publicaitons that are waiting to be processed, i.e. have not reached a terminal state.
     *
     * @return Lisot of publications in a non terminal state.
     * @throws PublicationRepositoryException
     */
    public Collection<Publication> waitingPublications() throws PublicationRepositoryException {
        BeanListHandler<Publication> handler = new BeanListHandler<>(Publication.class);
        try {
            String sql = "SELECT * FROM publication WHERE state in ('PENDING', 'PROCESSING') ORDER BY lastmodifieddate DESC";
            return queryRunner.query(sql.toString(), handler);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to list publications", e);
        }
    }

    private Object[] insertQueryArgs(Publication publication) {
        Timestamp now = timestampSource.now();
        return new Object[] {
                publication.getId(),
                publication.getTitle(),
                publication.getIsbn(),
                publication.getEmbargodate(),
                publication.getState(),
                publication.getStatedetails(),
                publication.getStacktrace(),
                publication.getChecksum(),
                now,
                now
        };
    }

    private Object[] updateQueryArgs(Publication publication) {
        Timestamp now = timestampSource.now();
        return new Object[] {
                publication.getTitle(),
                publication.getIsbn(),
                publication.getEmbargodate(),
                publication.getState(),
                publication.getStatedetails(),
                publication.getStacktrace(),
                publication.getChecksum(),
                now,
                publication.getId()
        };
    }

}
