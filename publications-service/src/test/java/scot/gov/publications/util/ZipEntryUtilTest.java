package scot.gov.publications.util;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.zip.ZipEntry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZipEntryUtilTest {


    @Test
    public void isHtmBehavesAsExcepted() {
        assertTrue(ZipEntryUtil.isHtml(entryWithFilename("blah.htm")));
        assertFalse(ZipEntryUtil.isHtml(entryWithFilename("__MACOSX/blah.htm")));
        assertFalse(ZipEntryUtil.isHtml(entryWithFilename("__MACOSX/blah.gif")));
    }

    ZipEntry entryWithFilename(String filename) {
        ZipEntry entry = Mockito.mock(ZipEntry.class);
        Mockito.when(entry.getName()).thenReturn(filename);
        return entry;
    }

}
