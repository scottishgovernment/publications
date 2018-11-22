package scot.gov.publications.rest;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.metadata.MetadataExtractor;
import scot.gov.publications.repo.ListResult;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.FileUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PublicationsResourceTest {

    @Test
    public void listReturnsResutlsFromRepo() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        ListResult result = new ListResult();
        when(sut.repository.list(1, 10, "queryString")).thenReturn(result);

        // ACT
        Response actual = sut.list(1, 10, "queryString");

        // ASSERT
        verify(sut.repository).list(1, 10, "queryString");
        assertSame(actual.getEntity(), result);
    }

    @Test(expected = WebApplicationException.class)
    public void listWrapsRepoException() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.repository = mock(PublicationRepository.class);
        ListResult result = new ListResult();
        when(sut.repository.list(1, 10, "queryString"))
                .thenThrow(new PublicationRepositoryException("arg", new RuntimeException("arg")));

        // ACT
        Response actual = sut.list(1, 10, "queryString");

        // ASSERT - see exception
    }


    @Test
    public void getReturnsResultFromRepo() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        Publication publication = new Publication();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenReturn(publication);

        // ACT
        Publication actual = sut.get("id");

        // ASSERT
        assertSame(actual, publication);
    }

    @Test(expected = WebApplicationException.class)
    public void getWrapsRepoException() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        Publication publication = new Publication();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenThrow(new PublicationRepositoryException("arg", new RuntimeException("arg")));

        // ACT
        Publication actual = sut.get("id");

        // ASSERT - see exception
    }

    @Test(expected = WebApplicationException.class)
    public void getThrowsExceptionIfPubNotFound() throws Exception {
        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        Publication publication = new Publication();
        sut.repository = mock(PublicationRepository.class);
        when(sut.repository.get("id")).thenReturn(null);

        // ACT
        Publication actual = sut.get("id");

        // ASSERT - see exception
    }

    @Test
    public void canPostZipFile() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);

        UploadRequest uploadRequest = upLoadRequest("/nestedzip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(202, actual.getStatus());
        verify(sut.repository).create(argThat(pub -> pub.getState().equals("PENDING")));
        verify(sut.storage).save(any(), any());
    }

    @Test
    public void postFormData400NonNestedZip() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);

        UploadRequest uploadRequest = upLoadRequest("/examplezip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(400, actual.getStatus());
        verify(sut.repository, never()).create(any());
        verify(sut.storage, never()).save(any(), any());
    }

    @Test
    public void postFormData400IfMetadataCannotBeExtracted() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);
        sut.metadataExtractor = mock(MetadataExtractor.class);
        when(sut.metadataExtractor.extract(any(File.class))).thenThrow(new ApsZipImporterException("arg"));

        UploadRequest uploadRequest = upLoadRequest("/nestedzip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(400, actual.getStatus());
        verify(sut.repository, never()).create(any());
        verify(sut.storage, never()).save(any(), any());
    }

    @Test
    public void postFormData400IfHashCannotBeCalulated() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);
        sut.fileUtil = new ExceptionthrowingFileUtil();

        UploadRequest uploadRequest = upLoadRequest("/nestedzip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(400, actual.getStatus());
        verify(sut.repository, never()).create(any());
        verify(sut.storage, never()).save(any(), any());
    }

    @Test
    public void postFormData500IfCannotUploadTos3() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);
        doThrow(PublicationStorageException.class).when(sut.storage).save(any(), any());

        UploadRequest uploadRequest = upLoadRequest("/nestedzip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(500, actual.getStatus());
    }

    @Test
    public void postFormData500IfCannotSaveToDatabase() throws Exception {

        // ARRANGE
        PublicationsResource sut = new PublicationsResource();
        sut.storage = mock(PublicationStorage.class);
        sut.repository = mock(PublicationRepository.class);
        sut.executor = mock(ExecutorService.class);
        doThrow(new PublicationRepositoryException("", new RuntimeException())).when(sut.repository).create(any());

        UploadRequest uploadRequest = upLoadRequest("/nestedzip.zip");

        // ACT
        Response actual = sut.postFormData(uploadRequest);

        // ASSERT
        assertEquals(500, actual.getStatus());
    }

    UploadRequest upLoadRequest(String path) throws IOException {
        InputStream in = PublicationsResourceTest.class.getResourceAsStream(path);
        byte[] bytes = IOUtils.toByteArray(in);
        UploadRequest request = new UploadRequest();
        request.setFileData(bytes);
        return request;
    }

    class ExceptionthrowingFileUtil extends FileUtil {

        @Override
        public String hash(File zip) throws IOException {
            throw new IOException("");
        }
    }

}