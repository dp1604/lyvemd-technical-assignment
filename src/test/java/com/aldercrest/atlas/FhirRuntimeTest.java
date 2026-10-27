package com.aldercrest.atlas;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FhirRuntimeTest {
    @Test
    void r4ParserAndRealInstanceValidatorArePresent() {
        FhirContext context = FhirContext.forR4();
        assertThat(context.getVersion().getVersion().getFhirVersionString()).isEqualTo("4.0.1");
        Patient patient = (Patient) context.newJsonParser().parseResource("""
                {"resourceType":"Patient","id":"synthetic","active":true}
                """);
        assertThat(patient.getIdElement().getIdPart()).isEqualTo("synthetic");
        FhirValidator validator = context.newValidator()
                .registerValidatorModule(new FhirInstanceValidator(context));
        assertThat(validator.validateWithResult(patient).isSuccessful()).isTrue();
        assertThat(validator.validateWithResult(context.newJsonParser().parseResource("""
                {"resourceType":"Observation"}
                """)).isSuccessful()).isFalse();
    }
}
