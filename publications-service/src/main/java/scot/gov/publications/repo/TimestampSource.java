package scot.gov.publications.repo;

import java.sql.Timestamp;

/**
 * Used to geta  timestamp - abstracted to facvilitate testing.
 */
public class TimestampSource {

    Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
