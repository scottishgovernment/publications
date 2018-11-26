package scot.gov.publications.storage;

import scot.gov.publications.repo.Publication;

import java.io.File;
import java.io.InputStream;

public interface PublicationStorage {

    /**
     * Determine if the storage service is healthy
     *
     * @return true if the repository is healthy, false otherwise
     * @throws PublicationStorageException If we fail to talk to the repository.
     */
    boolean ok() throws PublicationStorageException;

    /**
     * Save a publication to the repository.
     * @param publication the publication to save.
     * @param file The file to save for the publication
     * @throws PublicationStorageException IIf we cannot store the file.
     */
    void save(Publication publication, File file) throws PublicationStorageException;

    /**
     * Read the contents of a publication from the repository.
     * @param publication the publication to read
     * @return Input stream of the content of the publication.
     * @throws PublicationStorageException If we cannot read the content.
     */
    InputStream get(Publication publication) throws PublicationStorageException;

}