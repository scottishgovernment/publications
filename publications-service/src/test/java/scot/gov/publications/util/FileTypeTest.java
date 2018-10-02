package scot.gov.publications.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FileTypeTest {

    @Test
    public void forMimeTypeReturnsExceptedTypes() {
        Map<String, FileType> input = new HashMap<>();
        input.put("image/jpeg", FileType.JPG);
        input.put("application/pdf", FileType.PDF);
        input.put("blah", null);
        input.put("", null);
        input.put(null, null);

        Map<String, FileType> actual = new HashMap<>();
        input.entrySet().stream().forEach(entry -> actual.put(entry.getKey(), FileType.forMimeType(entry.getKey())));
        assertEquals(input, actual);
    }

    @Test
    public void returnsExceptedTypes() {
        Map<String, FileType> input = new HashMap<>();
        input.put("filename.jpg", FileType.JPG);
        input.put("filename.pdf", FileType.PDF);
        input.put("blah", null);
        input.put("", null);
        input.put(null, null);

        Map<String, FileType> actual = new HashMap<>();
        input.entrySet().stream().forEach(entry -> actual.put(entry.getKey(), FileType.forFilename(entry.getKey())));
        assertEquals(input, actual);
    }
}
