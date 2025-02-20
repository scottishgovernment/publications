package scot.gov.publications.metadata;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static scot.gov.publications.metadata.PublicationTypeMapper.STATISTICS;

public class MetadataTest {

    @Test
    public void emptyIsbnNormalisedToEmptyString() {
        Metadata sut = new Metadata();
        sut.setIsbn("");
        assertEquals(sut.normalisedIsbn(), "");
    }

    @Test
    public void nullIsbnNormalisedToEmptyString() {
        Metadata sut = new Metadata();
        sut.setIsbn(null);
        assertEquals(sut.normalisedIsbn(), "");
    }

    @Test
    public void isbnNormalised() {
        Metadata sut = new Metadata();
        sut.setIsbn(" 9999-899990-  \t");
        assertEquals(sut.normalisedIsbn(), "9999899990");
    }

    @Test
    public void shouldEmbargoReturnsFalseIfDateInPast() {
        Metadata sut = new Metadata();
        sut.setPublicationDate(LocalDateTime.now().minusDays(1));
        assertFalse(sut.shoudlEmbargo());
    }

    @Test
    public void shouldEmbargoReturnsTrueIfSensetive() {
        Metadata sut = new Metadata();
        sut.setPublicationDate(LocalDateTime.now().plusDays(1));
        sut.setSensitive(true);
        sut.setPublicationType("type");
        assertTrue(sut.shoudlEmbargo());
    }

    @Test
    public void shouldEmbargoReturnsTrueIfEmbargoType() {
        Metadata sut = new Metadata();
        sut.setPublicationDate(LocalDateTime.now().plusDays(1));
        sut.setSensitive(false);
        sut.setPublicationType(STATISTICS);
        assertTrue(sut.shoudlEmbargo());
    }

    @Test
    public void isConsultationRecognisesConsultation() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation-paper");
        sut.consultation = new Consultation();
        assertTrue(sut.isConsultation());
    }

    @Test
    public void isConsultationFalseIfNoConsultation() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation-paper");
        assertFalse(sut.isConsultation());
    }

    @Test
    public void isConsultationFalseIfWrongTypeConsultation() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation-analysis");
        sut.consultation = new Consultation();
        assertFalse(sut.isConsultation());
    }

    @Test
    public void isConsultationAnalysisRecognisesAnalysis() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation-analysis");
        sut.consultation = new Consultation();
        assertTrue(sut.isConsultationAnalysis());
    }

    @Test
    public void isConsultationAnalysisFalseIfNoConsultation() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation-analysis");
        assertFalse(sut.isConsultationAnalysis());
    }

    @Test
    public void isConsultationAnalysisFalseIfWrongTypeConsultation() {
        Metadata sut = new Metadata();
        sut.setPublicationType("consultation");
        sut.consultation = new Consultation();
        assertFalse(sut.isConsultationAnalysis());
    }

//
//    public boolean isConsultation() {
//        return "consultation-paper".equals(getPublicationType()) && getConsultation() != null;
//    }
//
//    public boolean isConsultationAnalysis() {
//        return "consultation-analysis".equals(getPublicationType()) && getConsultation() != null;
//    }
}
