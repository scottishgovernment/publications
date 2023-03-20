package scot.gov.publications.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration.S3;
import scot.gov.publications.repo.Publication;

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
        sut.s3 = mock(AmazonS3.class);
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        when(sut.s3.doesObjectExist(any(), any())).thenReturn(true);

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        assertTrue(actual);
    }

    @Test
    public void okReturnsTrueIfReadmeDoesNotExistsButWritingItWasSucessful() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(AmazonS3.class);
        when(sut.s3.doesObjectExist(any(), any())).thenReturn(false);
        sut.configuration = new S3();
        sut.configuration.setPath("path");

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        assertTrue(actual);
    }

    @Test(expected = PublicationStorageException.class)
    public void okThrowsExceptionIfS3ThrowsException() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(AmazonS3Client.class);
        when(sut.s3.doesObjectExist(any(), any())).thenThrow(new AmazonClientException(""));
        sut.configuration = new S3();
        sut.configuration.setPath("path");

        // ACT
        boolean actual = sut.ok();

        // ASSERT - see excpetion
        assertFalse(actual);
    }

    @Test
    public void saveGreenPath() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(AmazonS3Client.class);
        Publication publication = new Publication();
        publication.setChecksum("checksum");
        File file = mock(File.class);

        String expectedBucketName = "bucket";
        String expectedPath = "path/checksum";

        // ACT
        sut.save(publication, file);

        // ASSERT
        verify(sut.s3).putObject(
                argThat(put -> put.getBucketName().equals(expectedBucketName) && put.getKey().equals(expectedPath)));
    }


    @Test(expected = PublicationStorageException.class)
    public void saveExceptionWrappedAsExcepted() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(AmazonS3Client.class);
        when(sut.s3.putObject(any())).thenThrow(new AmazonClientException(""));
        Publication publication = new Publication();
        publication.setChecksum("checksum");
        File file = mock(File.class);

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
        sut.s3 = mock(AmazonS3Client.class);
        S3Object s3Obj = mock(S3Object.class);
        when(sut.s3.getObject(any())).thenReturn(s3Obj);
        Publication publication = new Publication();
        publication.setChecksum("checksum");

        String expectedBucketName = "bucket";
        String expectedPath = "path/checksum";

        // ACT
        sut.get(publication);

        // ASSERT
        verify(sut.s3).getObject(
                argThat(get -> get.getBucketName().equals(expectedBucketName) && get.getKey().equals(expectedPath)));
    }

    @Test(expected = PublicationStorageException.class)
    public void getExceptionWrappedAsExpected() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(AmazonS3Client.class);
        S3Object s3Obj = mock(S3Object.class);
        when(sut.s3.getObject(any())).thenThrow(new AmazonClientException(""));
        Publication publication = new Publication();
        publication.setChecksum("checksum");

        String expectedBucketName = "bucket";
        String expectedPath = "path/checksum";

        // ACT
        sut.get(publication);

        // ASSERT
        verify(sut.s3).getObject(
                argThat(get -> get.getBucketName().equals(expectedBucketName) && get.getKey().equals(expectedPath)));
    }

    @Test
    public void listKeysListsAsEpxected() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new S3();
        sut.configuration.setPath("path");
        sut.configuration.setBucket("bucket");
        sut.s3 = mock(AmazonS3Client.class);

        ObjectListing objectListing1 = listing(true, summary("summary1"), summary("summary2"));
        when(sut.s3.listObjects(any(), any())).thenReturn(objectListing1);

        ObjectListing objectListing2 = listing(false, summary("summary3"));
        when(sut.s3.listNextBatchOfObjects(any(ObjectListing.class))).thenReturn(objectListing2);

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
        sut.s3 = mock(AmazonS3Client.class);

        when(sut.s3.listObjects(any(), any())).thenThrow(new AmazonClientException("arg"));

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
        sut.s3 = mock(AmazonS3Client.class);
        DeleteObjectsResult res = mock(DeleteObjectsResult.class);
        when(sut.s3.deleteObjects(any())).thenReturn(res);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i< 2500; i++) {
            keys.add(Integer.toString(i));
        }

        // ACT
        sut.deleteKeys(keys);

        // ASSERT - objects are deleted in three chunks
        verify(sut.s3, times(3)).deleteObjects(any());
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

    ObjectListing listing(boolean truncted, S3ObjectSummary...summaries) {
        ObjectListing listing = mock(ObjectListing.class);
        when(listing.isTruncated()).thenReturn(truncted);
        when(listing.getObjectSummaries()).thenReturn(asList(summaries));
        return listing;
    }

    S3ObjectSummary summary(String key) {
        S3ObjectSummary summary = mock(S3ObjectSummary.class);
        when(summary.getKey()).thenReturn("path/" + key);
        return summary;
    }

}
