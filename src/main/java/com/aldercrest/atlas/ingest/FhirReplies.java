package com.aldercrest.atlas.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FhirReplies {
    private static final ObjectMapper JSON = IngestionJson.mapper();
    private FhirReplies() { }
    public static IngestReply error(int status,String code) { return new IngestReply(status,encode(outcome(code,null))); }
    public static IngestReply rejected(int entries,List<EntryIssue> issues) {
        List<Object> response = new ArrayList<>();
        for (int index=0; index<entries; index++) {
            int entryIndex=index;
            List<EntryIssue> invalid=issues.stream().filter(issue -> issue.index()==entryIndex).toList();
            String code=invalid.isEmpty() ? "BUNDLE_NOT_COMMITTED" : invalid.getFirst().code();
            response.add(Map.of("response",Map.of("status",invalid.isEmpty() ? "424 Failed Dependency" : "400 Bad Request",
                    "outcome",outcome(code,"Bundle.entry["+index+"]"))));
        }
        return new IngestReply(400,encode(Map.of("resourceType","Bundle","type","transaction-response","entry",response)));
    }
    public static IngestReply success(List<String> locations) {
        List<Object> entries=locations.stream().<Object>map(location -> Map.of("response",Map.of("status","201 Created","location",location))).toList();
        return new IngestReply(200,encode(entries.isEmpty() ? Map.of("resourceType","Bundle","type","transaction-response")
                : Map.of("resourceType","Bundle","type","transaction-response","entry",entries)));
    }
    private static Map<String,Object> outcome(String code,String expression) {
        Map<String,Object> issue = expression==null ? Map.of("severity","error","code","processing","diagnostics",code)
                : Map.of("severity","error","code","processing","diagnostics",code,"expression",List.of(expression));
        return Map.of("resourceType","OperationOutcome","issue",List.of(issue));
    }
    public static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Response encoding failed."); }
    }
}
