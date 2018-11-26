package scot.gov.publications.storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import org.junit.Assert;
import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3PublicationStorageTest {

    @Test
    public void okReturnsTrueIfReadmeExists() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(AmazonS3.class);
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");
        when(sut.s3.doesObjectExist(any(), any())).thenReturn(true);

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        Assert.assertTrue(actual);
    }

    @Test
    public void okReturnsTrueIfReadmeDoesNotExistsButWritingItWasSucessful() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(AmazonS3.class);
        when(sut.s3.doesObjectExist(any(), any())).thenReturn(false);
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");

        // ACT
        boolean actual = sut.ok();

        // ASSERT
        Assert.assertTrue(actual);
    }

    @Test(expected = PublicationStorageException.class)
    public void okThrowsExceptionIfS3ThrowsException() throws Exception {

        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.s3 = mock(AmazonS3Client.class);
        when(sut.s3.doesObjectExist(any(), any())).thenThrow(new AmazonClientException(""));
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");

        // ACT
        boolean actual = sut.ok();

        // ASSERT - see excpetion
        assertFalse(actual);
    }

    @Test
    public void saveGreenPath() throws Exception {
        // ARRANGE
        S3PublicationStorage sut = new S3PublicationStorage();
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");
        sut.configuration.getS3().setBucketName("bucket");
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
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");
        sut.configuration.getS3().setBucketName("bucket");
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
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");
        sut.configuration.getS3().setBucketName("bucket");
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
        sut.configuration = new PublicationsConfiguration();
        sut.configuration.getS3().setPath("path");
        sut.configuration.getS3().setBucketName("bucket");
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

}
