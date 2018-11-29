package scot.gov.publications.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;

/**
 * Store and fetch zip files in s3.
 */
public class S3PublicationStorage implements PublicationStorage {

    @Inject
    PublicationsConfiguration.S3 configuration;

    @Inject
    AmazonS3 s3;

    /**
     * Determine if the storage service is healthly by trying to ensure that the readme file exists.
     *
     * @return true if the repository ois health, false otherwise
     * @throws PublicationStorageException If we fail to talk to s3.
     */
    public boolean ok() throws PublicationStorageException {

        try {
            // test if we can determin the existence of the readme file
            String path = path("README.txt");
            if (s3.doesObjectExist(configuration.getBucket(), path)) {
                return true;
            }

            // the file does not exist, try to write it
            ObjectMetadata objectMetadata = new ObjectMetadata();
            String bucketName = configuration.getBucket();
            InputStream in = S3PublicationStorage.class.getResourceAsStream("/StorageReadme.txt");
            PutObjectRequest put = new PutObjectRequest(bucketName, path, in, objectMetadata);

            s3.putObject(put);
            return true;
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    public void save(Publication publication, File file) throws PublicationStorageException {
        String path = getPath(publication);
        PutObjectRequest put = new PutObjectRequest(configuration.getBucket(), path, file);
        try {
            s3.putObject(put);
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    public InputStream get(Publication publication) throws PublicationStorageException {
        String path = getPath(publication);
        GetObjectRequest get = new GetObjectRequest(configuration.getBucket(), path);
        try {
            S3Object s3Object = s3.getObject(get);
            return s3Object.getObjectContent();
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    private String getPath(Publication publication) {
        return path(publication.getChecksum());
    }

    private String path(String name) {
        return Paths.get(configuration.getPath()).resolve(name).toString();
    }

}
