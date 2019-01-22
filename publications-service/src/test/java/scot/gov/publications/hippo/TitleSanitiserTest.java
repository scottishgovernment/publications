package scot.gov.publications.hippo;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TitleSanitiserTest {

    @Test
    public void titlesSanitisedAsExpected() {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("word’‘“”", "word''\"\"");
        inputs.put("word", "word");
        inputs.put("", "");

        inputs.entrySet().forEach(entry -> assertEquals(TitleSanitiser.sanitise(entry.getKey()), entry.getValue()));
    }

}
