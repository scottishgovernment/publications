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

//    public boolean shoudlEmbargo() {
//
//        // we never need to embargo a publications whose publication date is in the past
//        if (publicationDate.isBefore(LocalDateTime.now())) {
//            return false;
//        }
//
//        // if the sensitive flag is set or if this is an embargo type then we should embargo it.
//        return isSensitive() || typeMapper.isEmbargoType(publicationType);
//    }
}
