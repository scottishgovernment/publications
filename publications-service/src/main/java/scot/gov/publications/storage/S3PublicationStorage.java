package scot.gov.publications.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Store and fetch zip files in s3.
 */
public class S3PublicationStorage implements PublicationStorage {

    private static final Logger LOG = LoggerFactory.getLogger(S3PublicationStorage.class);

    private static String README = "README.txt";

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
            // test if we can determine the existence of the readme file
            String path = path(README);
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

    public Set<String> listKeys() throws PublicationStorageException{
        Set<String> keys = new HashSet<>();
        try {
            ObjectListing objects = s3.listObjects(configuration.getBucket(), configuration.getPath());
            while (!objects.getObjectSummaries().isEmpty()) {
                keys.addAll(objects.getObjectSummaries()
                        .stream()
                        .map(S3ObjectSummary::getKey)
                        .map(key -> key.substring(configuration.getPath().length() + 1))
                        .collect(toList()));
                // don't need to check the next listing if this one is not truncated
                if (!objects.isTruncated()) {
                    break;
                }
                objects = s3.listNextBatchOfObjects(objects);
            }

            // we do not want to remove the readme file used by the ok method
            keys.remove(README);
            return keys;
        } catch (AmazonClientException e) {
            throw new PublicationStorageException(e);
        }
    }

    public Map<String, String> deleteKeys(Collection<String> keys) throws PublicationStorageException {
        // split into arrays no larger than 1000
        List<List<String>> chunks = partition(keys, 1000);

        // delete each chunk and record the results
        Map<String, String> results = new HashMap<>();
        for (List<String> chunk : chunks) {
            results.putAll(deleteChunk(chunk));
        }
        return results;
    }

    <T> List<List<T>> partition(Collection<T> collection, int size) {
        List<List<T>> chunks = new ArrayList<>();
        List<T> list = new ArrayList<>(collection);
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    private Map<String, String> deleteChunk(List<String> chunk) {
        String[] s3KeysArray = chunk.toArray(new String[chunk.size()]);
        DeleteObjectsRequest deleteRequest =
                new DeleteObjectsRequest(configuration.getBucket()).withKeys(s3KeysArray);

        try {
            DeleteObjectsResult result = s3.deleteObjects(deleteRequest);

            List<DeleteObjectsResult.DeletedObject> deletedObjects = result.getDeletedObjects();
            return deletedObjects.stream().map(DeleteObjectsResult.DeletedObject::getKey).collect(toMap(identity(), key -> "deleted"));

        } catch (MultiObjectDeleteException e) {
            LOG.warn("Multiple delete threw exception.", e);
            // some items could not be deleted, collect the successful and error responses into the result map.
            Map<String, String> ok = e.getDeletedObjects()
                    .stream()
                    .map(DeleteObjectsResult.DeletedObject::getKey)
                    .collect(toMap(identity(), key -> "deleted"));
            Map<String, String> errors = e.getErrors()
                    .stream()
                    .collect(toMap(MultiObjectDeleteException.DeleteError::getKey, MultiObjectDeleteException.DeleteError::getMessage));
            Map<String, String> results = new HashMap<>();
            results.putAll(ok);
            results.putAll(errors);
            return results;
        }
    }

    private String getPath(Publication publication) {
        return path(publication.getChecksum());
    }

    private String path(String name) {
        return Paths.get(configuration.getPath()).resolve(name).toString();
    }

}
