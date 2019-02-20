package scot.gov.publications.metadata;

import java.util.HashMap;
import java.util.Map;

/**
 * The publication types were rationalised as part of MGS-4940.
 *
 * Until the registration form is updated we still need to be able to undestand the old delted types.  This class maps
 * the old types on to the new ones.
 */
public class PublicationTypeMapper {

    static Map<String, String> typeMap = new HashMap<>();

    static final String CORRESPONDENCE = "Correspondence";

    static final String STATISTICS = "Statistics";

    static final String SPEECH_STATEMENT = "Speech/statement";

    static final String RESEARCH_AND_ANALYSIS = "Research and analysis";

    static final String PUBLICATION = "Publication";

    static {
        // how to map types that are moving...
        typeMap.put("Consultation", "Consultation paper");
        typeMap.put("Consultation responses", "Consultation analysis");
        typeMap.put("Forms", "Form");
        typeMap.put("Guidance", "Advice and guidance");
        typeMap.put("Letter/Circular", CORRESPONDENCE);
        typeMap.put("Newsletter", CORRESPONDENCE);
        typeMap.put("Research findings", RESEARCH_AND_ANALYSIS);
        typeMap.put("Research finding", RESEARCH_AND_ANALYSIS);
        typeMap.put("Research publications", RESEARCH_AND_ANALYSIS);
        typeMap.put("Research publication", RESEARCH_AND_ANALYSIS);
        typeMap.put("Speech", SPEECH_STATEMENT);
        typeMap.put("Statistics dataset", STATISTICS);
        typeMap.put("Statistics publication", STATISTICS);
        typeMap.put("Speech", SPEECH_STATEMENT);
        typeMap.put("Statistics dataset", STATISTICS);
        typeMap.put("Statistics publication", STATISTICS);
        typeMap.put("Info page", PUBLICATION);
        typeMap.put("Legislation", PUBLICATION);
        typeMap.put("Report", PUBLICATION);
    }

    public String map(String publicationType) {
        return  typeMap.getOrDefault(publicationType, publicationType);
    }
}
