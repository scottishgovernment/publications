package scot.gov.publications.metadata;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
