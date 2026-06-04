package scot.gov.publications.storage;

import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration.S3;
import scot.gov.publications.repo.Publication;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3PublicationStorageTest {

    @Test
    public void okReturnsTrueIfReadmeExists() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(S3Client.class);
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        when(sut.s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        assertTrue(actual);
    }

    @Test
    public void okReturnsTrueIfReadmeDoesNotExistsButWritingItWasSucessful() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(S3Client.class);
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        when(sut.s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        assertTrue(actual);
    }

    @Test(expected = PublicationStorageException.class)
    public void okThrowsExceptionIfS3ThrowsException() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(S3Client.class);
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        when(sut.s3.headObject(any(HeadObjectRequest.class))).thenThrow(SdkException.create("error", null));

        // ACT
        boolean actual = sut.ok();

        // ASSERT - see exception
        assertFalse(actual);
    }

    @Test
    public void saveGreenPath() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        Publication publication = new Publication();
        publication.setChecksum("checksum");
        File file = File.createTempFile("test", ".zip");
        file.deleteOnExit();

        String expectedBucketName = "bucket";
        String expectedPath = "path/checksum";

        // ACT
        sut.save(publication, file);

        // ASSERT
        verify(sut.s3).putObject(
                argThat((PutObjectRequest put) -> put.bucket().equals(expectedBucketName) && put.key().equals(expectedPath)),
                any(RequestBody.class));
    }


    @Test(expected = PublicationStorageException.class)
    public void saveExceptionWrappedAsExcepted() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        when(sut.s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(SdkException.create("error", null));
        Publication publication = new Publication();
        publication.setChecksum("checksum");
        File file = File.createTempFile("test", ".zip");
        file.deleteOnExit();

        // ACT
        sut.save(publication, file);

        // ASSERT -- see exception
    }

    @Test
    public void getGreenpath() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        ResponseInputStream<GetObjectResponse> mockResponse = mock(ResponseInputStream.class);
        when(sut.s3.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);
        Publication publication = new Publication();
        publication.setChecksum("checksum");

        String expectedBucketName = "bucket";
        String expectedPath = "path/checksum";

        // ACT
        sut.get(publication);

        // ASSERT
        verify(sut.s3).getObject(
                argThat((GetObjectRequest get) -> get.bucket().equals(expectedBucketName) && get.key().equals(expectedPath)));
    }

    @Test(expected = PublicationStorageException.class)
    public void getExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        when(sut.s3.getObject(any(GetObjectRequest.class))).thenThrow(SdkException.create("error", null));
        Publication publication = new Publication();
        publication.setChecksum("checksum");

        // ACT
        sut.get(publication);

        // ASSERT -- see exception
    }

    @Test
    public void listKeysListsAsEpxected() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);

        ListObjectsV2Response response1 = ListObjectsV2Response.builder()
                .isTruncated(true)
                .nextContinuationToken("token")
                .contents(s3object("path/summary1"), s3object("path/summary2"))
                .build();
        ListObjectsV2Response response2 = ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(s3object("path/summary3"))
                .build();
        when(sut.s3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        Set<String> expected = new HashSet<>();
        Collections.addAll(expected, "summary1", "summary2", "summary3");

        // ACT
        Set<String> actual = sut.listKeys();

        // ASSERT
        assertEquals(expected, actual);
    }

    @Test(expected = PublicationStorageException.class)
    public void listKeysExceptionWrappedCorrectly() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        when(sut.s3.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkException.create("arg", null));

        // ACT
        sut.listKeys();

        // ASSERT -- see exception
    }

    @Test
    public void deleteKeysGreenpath() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(S3Client.class);
        DeleteObjectsResponse res = DeleteObjectsResponse.builder()
                .deleted(emptyList())
                .errors(emptyList())
                .build();
        when(sut.s3.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(res);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            keys.add(Integer.toString(i));
        }

        // ACT
        sut.deleteKeys(keys);

        // ASSERT - objects are deleted in three chunks
        verify(sut.s3, times(3)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    public void batchesDeletes() {
        S3PublicationStorage storage = new S3PublicationStorage();
        List<List<Integer>> batches = storage.partition(asList(0, 1, 2, 3, 4), 2);
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).isEqualTo(asList(0, 1));
        assertThat(batches.get(1)).isEqualTo(asList(2, 3));
        assertThat(batches.get(2)).isEqualTo(asList(4));

        assertThat(storage.partition(emptyList(), 5)).isEmpty();
    }

    S3Object s3object(String key) {
        return S3Object.builder().key(key).build();
    }

}
