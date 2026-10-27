package com.aldercrest.atlas.http;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class SafeErrors {
    public static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");
    private SafeErrors() { }

    public static ResponseEntity<Map<String, Object>> response(int status) {
        String code = status == 404 ? "not-found" : status >= 500 ? "transient" : "invalid";
        String message = status == 404 ? "Not available." : status >= 500
                ? "Service unavailable." : "Request cannot be processed.";
        return ResponseEntity.status(status).contentType(FHIR_JSON).body(Map.of(
                "resourceType", "OperationOutcome", "issue", List.of(Map.of(
                        "severity", "error", "code", code, "diagnostics", message))));
    }
}
