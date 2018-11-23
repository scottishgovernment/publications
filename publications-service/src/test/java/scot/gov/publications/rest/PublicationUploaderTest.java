package scot.gov.publications.rest;

import org.junit.Test;
import scot.gov.publications.ApsZipImporter;
import scot.gov.publications.ApsZipImporterException;
import scot.gov.publications.PublicationsConfiguration;
import scot.gov.publications.repo.Publication;
import scot.gov.publications.repo.PublicationRepository;
import scot.gov.publications.repo.PublicationRepositoryException;
import scot.gov.publications.repo.State;
import scot.gov.publications.storage.S3PublicationStorage;
import scot.gov.publications.storage.PublicationStorageException;
import scot.gov.publications.util.FileUtil;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class PublicationUploaderTest {

    @Test
    public void greenpath() throws Exception {
        // ARRANGE
        PublicationUploader sut = new PublicationUploader();
        sut.configuration = new PublicationsConfiguration();
        sut.storage = mock(S3PublicationStorage.class);
        when(sut.storage.get(any())).thenReturn(PublicationUploaderTest.class.getResourceAsStream("/nestedzip.zip"));
        sut.repository = mock(PublicationRepository.class);
        sut.apsZipImporter = mock(ApsZipImporter.class);
        Publication publication = new Publication();
        publication.setId("id");

        // ACT
        sut.importPublication(publication);

        // ASSERT
        verify(sut.apsZipImporter).importApsZip(any());
        verify(sut.repository, atLeastOnce()).update(argThat(pub -> pub.getState().equals("DONE")));
    }

    @Test
    public void repositoryExceptionPopulatedAndSaved() throws Exception {
        // ARRANGE
        PublicationUploader sut = new PublicationUploader();
        sut.configuration = new PublicationsConfiguration();
        sut.storage = mock(S3PublicationStorage.class);
        when(sut.storage.get(any())).thenReturn(PublicationUploaderTest.class.getResourceAsStream("/nestedzip.zip"));
        sut.repository = mock(PublicationRepository.class);
        doThrow(new PublicationRepositoryException("", null)).doNothing().when(sut.repository).update(any());
        sut.apsZipImporter = mock(ApsZipImporter.class);
        Publication publication = new Publication();
        publication.setId("id");

        // ACT
        sut.importPublication(publication);

        // ASSERT - the repository should be updated with details and a stacktrace
        verify(sut.repository, atLeastOnce())
                .update(
                        argThat(
                                pub -> pub.getState().equals(State.FAILED.name()) &&
                                        pub.getStatedetails().equals("Failed to save publication to database") &&
                                        !pub.getStacktrace().isEmpty()
                        ));
    }

    @Test
    public void ioExceptionPopulatedAndSaved() throws Exception {
        // ARRANGE
        PublicationUploader sut = new PublicationUploader();
        sut.configuration = new PublicationsConfiguration();
        sut.storage = mock(S3PublicationStorage.class);
        when(sut.storage.get(any())).thenReturn(PublicationUploaderTest.class.getResourceAsStream("/nestedzip.zip"));
        sut.repository = mock(PublicationRepository.class);
        sut.apsZipImporter = mock(ApsZipImporter.class);
        sut.fileUtil = mock(FileUtil.class);
        when(sut.fileUtil.createTempFile(any(), anyString(), any())).thenThrow(new IOException(""));
        Publication publication = new Publication();
        publication.setId("id");

        // ACT
        sut.importPublication(publication);

        // ASSERT - the repository should be updated with details and a stacktrace
        verify(sut.repository, atLeastOnce())
                .update(
                        argThat(
                                pub -> pub.getState().equals(State.FAILED.name()) &&
                                        pub.getStatedetails().equals("Failed to save publication as a temp file") &&
                                        !pub.getStacktrace().isEmpty()
                        ));
    }

    @Test
    public void storageExceptionPopulatedAndSaved() throws Exception {
        // ARRANGE
        PublicationUploader sut = new PublicationUploader();
        sut.configuration = new PublicationsConfiguration();
        sut.storage = mock(S3PublicationStorage.class);
        when(sut.storage.get(any())).thenThrow(new PublicationStorageException(new RuntimeException("")));
        sut.repository = mock(PublicationRepository.class);
        sut.apsZipImporter = mock(ApsZipImporter.class);
        Publication publication = new Publication();
        publication.setId("id");

        // ACT
        sut.importPublication(publication);

        // ASSERT - the repository should be updated with details and a stacktrace
        verify(sut.repository, atLeastOnce())
                .update(
                        argThat(
                                pub -> pub.getState().equals(State.FAILED.name()) &&
                                        pub.getStatedetails().equals("Failed to get publication from s3") &&
                                        !pub.getStacktrace().isEmpty()
                        ));
    }

    @Test
    public void zipIMporterExceptionPopulatedAndSaved() throws Exception {
        // ARRANGE
        PublicationUploader sut = new PublicationUploader();
        sut.configuration = new PublicationsConfiguration();
        sut.storage = mock(S3PublicationStorage.class);
        when(sut.storage.get(any())).thenReturn(PublicationUploaderTest.class.getResourceAsStream("/nestedzip.zip"));
        sut.repository = mock(PublicationRepository.class);
        sut.apsZipImporter = mock(ApsZipImporter.class);
        doThrow(new ApsZipImporterException("")).when(sut.apsZipImporter).importApsZip(any());
        Publication publication = new Publication();
        publication.setId("id");

        // ACT
        sut.importPublication(publication);

        // ASSERT - the repository should be updated with details and a stacktrace
        verify(sut.repository, atLeastOnce())
                .update(
                        argThat(
                                pub -> pub.getState().equals(State.FAILED.name()) &&
                                        pub.getStatedetails().equals("Failed to import contents of zip") &&
                                        !pub.getStacktrace().isEmpty()
                        ));
    }
}
