package scot.gov.publications.repo;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class PublicationRepository {

    @Inject
    QueryRunner queryRunner;

//    id character varying(255) NOT NULL,
//    title character varying NOT NULL,
//    isbn character varying(25) NOT NULL,
//    empbargo_date timestamp,
//    state character varying(20) NOT NULL,
//    state_details character varying,
//    checksum character varying NOT NULL,
//    created_date timestamp,
//    last_modified_date timestamp,

    public void create(Publication publication) throws PublicationRepositoryException {
        String sql = "INSERT INTO publication" +
                "(id, title, isbn, empbargo_date, state, state_details, checksum, created_date, last_modified_date) " +
                "values(?,?,?,?,?,?,?,?,?)";
        try {
            queryRunner.update(sql, queryArgs(publication));
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to create publication", e);
        }
    }

    public void update(Publication publication) throws PublicationRepositoryException {
    }

    public Publication get() throws PublicationRepositoryException {
        return null;
    }

    public Collection<Publication> list(int page, int size) throws PublicationRepositoryException {
        String sql = "SELECT * FROM publication LIMIT 10 OFFSET ?";
        Object [] args = {
                new Integer(page * size)
        };
        try {
            ResultSetHandler<List<Publication>> handler = new BeanListHandler(Publication.class);
            return queryRunner.query(sql, handler, args);
        } catch (SQLException e) {
            throw new PublicationRepositoryException("Failed to create publication", e);
        }
    }

    private Object [] queryArgs(Publication publication) {
        return new Object [] {
                publication.getId(),
                publication.getTitle(),
                publication.getIsbn(),
                publication.getEmbargoDate(),
                publication.getState(),
                publication.getStateDetails(),
                publication.getChecksum(),
                publication.getCreatedDate(),
                publication.getLastModifiedDate()
        };
    }
}
