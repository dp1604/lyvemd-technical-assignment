package com.aldercrest.atlas.ingest;

import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Pure trusted-rule mapping test. No validator construction, payload logging or DB. */
class FhirValidationDiagnosticTest {
    @Test void knownCompiledRuleMapsToAnAsciiConstantNameAndUnknownContentIsNeverEchoed() {
        assertThat(FhirValidation.diagnosticRule(I18nConstants.ARRAY_CANNOT_BE_EMPTY)).isEqualTo("ARRAY_CANNOT_BE_EMPTY");
        for(String untrusted:new String[]{null,"","PATIENT_SECRET_123","Array_cannot_be_empty\npatient-secret",
                "Observation.valueQuantity.value=3.8","https://example.org/Patient/private","FHIRPATH_DECIMAL_ONLY patient-secret"}) {
            assertThat(FhirValidation.diagnosticRule(untrusted)).isEqualTo("UNKNOWN");
        }
        assertThat(FhirValidation.diagnosticRule(I18nConstants.ERROR_PARSING_JSON_)).matches("[A-Za-z0-9_]{1,200}")
                .doesNotContain("patient-secret","Observation.","http",":","\n");
    }
}
