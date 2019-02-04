package scot.gov.publications.util;

import org.junit.Test;
import org.mockito.Mockito;

import javax.jcr.Binary;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;

public class ExifProcessImplTest {

    @Test
    public void pageCountIsZeroForNonPDF() throws Exception {
        InputStream in = ExifProcessImplTest.class.getResourceAsStream("/examplepdf.xls");
        Binary binary = Mockito.mock(Binary.class);
        Mockito.when(binary.getStream()).thenReturn(in);
        long count = new ExifProcessImpl().pageCount(binary, "application/msexcel");
        assertEquals(0, count);
    }

    @Test
    public void pageCountIsZeroIfExceptionIsThrown() throws Exception {
        InputStream in = Mockito.mock(InputStream.class);
        Mockito.when(in.read(any())).thenThrow(new IOException("arg"));
        Binary binary = Mockito.mock(Binary.class);
        Mockito.when(binary.getStream()).thenReturn(in);
        long count = new ExifProcessImpl().pageCount(binary, "application/pdf");
        assertEquals(0, count);
    }

    @Test
    public void extractPageCountBehavesAsExpceted() {
        Map<List<String>, Long> fixtures = new HashMap<>();
        fixtures.put(Collections.singletonList("PageCount: 1"), 1L);
        fixtures.put(Collections.singletonList("PageCount: invalid"), 0L);
        fixtures.put(Collections.singletonList("PageCount: 1: too many colons"), 0L);
        fixtures.put(Collections.emptyList(), 0L);

        Map<List<String>, Long> results = new HashMap<>();
        for (Map.Entry<List<String>, Long> fixture : fixtures.entrySet()) {
            results.put(fixture.getKey(), ExifProcessImpl.extractPageCount(fixture.getKey()));
        }
        assertEquals(fixtures, results);
    }

}

