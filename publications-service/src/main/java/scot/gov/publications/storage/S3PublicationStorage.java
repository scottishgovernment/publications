package scot.gov.publications.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * Store and fetch zip files in s3.
 */
public class S3PublicationStorage implements PublicationStorage {

    private static final Logger LOG = LoggerFactory.getLogger(S3PublicationStorage.class);

    private static String README = "README.txt";

    @Inject
    PublicationsConfiguration.S3 configuration;

    @Inject
    S3Client s3;

    /**
     * Determine if the storage service is healthly by trying to ensure that the readme file exists.
     *
     * @return true if the repository ois health, false otherwise
     * @throws PublicationStorageException If we fail to talk to s3.
     */
    public boolean ok() throws PublicationStorageException {
        try {
            String path = path(README);
            String bucketName = configuration.getBucket();
            if (doesObjectExist(bucketName, path)) {
                return true;
            }
            byte[] content = S3PublicationStorage.class.getResourceAsStream("/StorageReadme.txt").readAllBytes();
            s3.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(path).build(),
                RequestBody.fromBytes(content)
            );
            return true;
        } catch (SdkException | IOException e) {
            throw new PublicationStorageException(e);
        }
    }

    private boolean doesObjectExist(String bucket, String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            LOG.warn("no such key", e);
            return false;
        }
    }

    public void save(Publication publication, File file) throws PublicationStorageException {
        String path = getPath(publication);
        try {
            s3.putObject(
                PutObjectRequest.builder().bucket(configuration.getBucket()).key(path).build(),
                RequestBody.fromFile(file)
            );
        } catch (SdkException e) {
            throw new PublicationStorageException(e);
        }
    }

    public InputStream get(Publication publication) throws PublicationStorageException {
        String path = getPath(publication);
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(configuration.getBucket()).key(path).build());
        } catch (SdkException e) {
            throw new PublicationStorageException(e);
        }
    }

    public Set<String> listKeys() throws PublicationStorageException {
        Set<String> keys = new HashSet<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(configuration.getBucket())
                    .prefix(configuration.getPath())
                    .build();
            ListObjectsV2Response response;
            do {
                response = s3.listObjectsV2(request);
                response.contents().stream()
                        .map(S3Object::key)
                        .map(key -> key.substring(configuration.getPath().length() + 1))
                        .forEach(keys::add);
                if (response.isTruncated()) {
                    request = request.toBuilder()
                            .continuationToken(response.nextContinuationToken())
                            .build();
                }
            } while (response.isTruncated());
            keys.remove(README);
            return keys;
        } catch (SdkException e) {
            throw new PublicationStorageException(e);
        }
    }

    public Map<String, String> deleteKeys(Collection<String> keys) throws PublicationStorageException {
        List<List<String>> chunks = partition(keys, 1000);
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
        List<ObjectIdentifier> identifiers = chunk.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .collect(toList());
        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(configuration.getBucket())
                .delete(Delete.builder().objects(identifiers).build())
                .build();

        DeleteObjectsResponse response = s3.deleteObjects(deleteRequest);

        Map<String, String> results = new HashMap<>();
        response.deleted().forEach(d -> results.put(d.key(), "deleted"));
        response.errors().forEach(e -> {
            LOG.warn("Failed to delete {}: {}", e.key(), e.message());
            results.put(e.key(), e.message());
        });
        return results;
    }

    private String getPath(Publication publication) {
        return path(publication.getChecksum());
    }

    private String path(String name) {
        return Paths.get(configuration.getPath()).resolve(name).toString();
    }

}
