package scot.gov.publications.util;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MimeTypeUtilsTest {

    @Test
    public void canDetectByNameForCommonTypes() throws IOException {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("filename.pdf", "application/pdf");
        inputs.put("filename.PDF", "application/pdf");
        inputs.put("filename.doc", "application/msword");
        inputs.put("filename.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        inputs.put("filename.xls", "application/vnd.ms-excel");
        inputs.put("filename.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        Map<String, String> actual = new HashMap<>();

        for (Map.Entry<String, String> entry : inputs.entrySet()) {
            actual.put(entry.getKey(), MimeTypeUtils.detectContentType(entry.getKey()));
        }

        assertEquals(inputs, actual);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionForUnrecognisedExtensions() {
        assertEquals("application/pdf", MimeTypeUtils.detectContentType("pdfwithwrongextention.pdf-wrong"));
    }
}
