package scot.gov.publications;

/**
 * Exception throws by the ApsZipImporter class.
 */
public class ApsZipImporterException extends Exception {

    public ApsZipImporterException(String msg) {
        super(msg);
    }

    public ApsZipImporterException(String msg, Exception e) {
        super(msg, e);
    }
}
