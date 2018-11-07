package scot.gov.publications;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import scot.gov.publications.repo.Publication;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;

/**
 * Store and fetch zip files in s3.
 */
public class PublicationStorage {

    @Inject
    PublicationsConfiguration configuration;

    @Inject
    AmazonS3Client s3client;

    public void save(Publication publication, File file) throws PublicationStorageException {
        String path = getPath(publication);
        PutObjectRequest put = new PutObjectRequest(configuration.getS3().getBucketName(), path, file);
        try {
            s3client.putObject(put);
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    public InputStream get(Publication publication) throws PublicationStorageException {
        String path = getPath(publication);
        GetObjectRequest get = new GetObjectRequest(configuration.getS3().getBucketName(), path);
        try {
            S3Object s3Object = s3client.getObject(get);
            return s3Object.getObjectContent();
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    private String getPath(Publication publication) {
        return Paths.get(configuration.getS3().getPath()).resolve(publication.getChecksum()).toString();
    }
}
