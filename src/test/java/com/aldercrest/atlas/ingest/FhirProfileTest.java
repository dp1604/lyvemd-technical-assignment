package com.aldercrest.atlas.ingest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FhirProfileTest {
    private static FhirValidation validation;
    private static final ObjectMapper JSON=IngestionJson.mapper();
    @BeforeAll static void setup() throws Exception {validation=new FhirValidation(FhirContext.forR4Cached());}

    @Test void locallyDefinedComplexExtensionAndOrdinaryR4ResourcesValidate() throws Exception {
        String patient="""
                {"resourceType":"Patient","birthDate":"2000-01-01","extension":[{"url":"%s","extension":[
                {"url":"code","valueCode":"male"},{"url":"validFrom","valueDate":"2000-01-01"}]}]}
                """.formatted(FhirDefinitions.SEX_CONTEXT);
        assertThat(validation.valid(patient)).isTrue();
        JsonNode node=JSON.readTree(patient);
        new RawProfile().resource(node);
        assertThat(new ExtensionContent().sexAssertions(node)).hasSize(1);
        assertThat(validation.valid("{\"resourceType\":\"Observation\"}")).isFalse();
    }

    @Test void shippedDefinitionsHaveMatchingCanonicalAndLocalConstraintMetadata() throws Exception {
        for(String name:new String[]{"clinical-sex-context","correction-of","standardized-creatinine"}) {
            try(var stream=getClass().getResourceAsStream("/fhir/StructureDefinition-"+name+".json")) {
                String json=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var definition=(org.hl7.fhir.r4.model.StructureDefinition)validation.parse(json);
                assertThat(definition.getUrl()).isEqualTo(FhirDefinitions.BASE+name);
                assertThat(definition.getFhirVersion().toCode()).isEqualTo("4.0.1");
                assertThat(definition.getBaseDefinition()).isEqualTo("http://hl7.org/fhir/StructureDefinition/Extension");
                assertThat(definition.getDifferential().getElement()).isNotEmpty();
                assertThat(definition.getDifferential().getElement().stream().filter(e -> "Extension.url".equals(e.getPath()))
                        .findFirst().orElseThrow().getFixed().primitiveValue()).isEqualTo(definition.getUrl());
            }
        }
    }

    @Test void localDefinitionsRejectIncorrectExtensionTypesWithoutRemoteResolution() {
        String template="""
                {"resourceType":"Observation","status":"final","code":{"coding":[{"system":"http://loinc.org","code":"2160-0"}]},
                "subject":{"reference":"Patient/p1"},"effectiveDateTime":"2026-01-01T00:00:00Z",
                "valueQuantity":{"value":1,"system":"http://unitsofmeasure.org","code":"mg/dL"},
                "extension":[%s]}
                """;
        assertThat(validation.valid(template.formatted("{\"url\":\""+FhirDefinitions.STANDARDIZED+"\",\"valueBoolean\":true}"))).isTrue();
        assertThat(validation.valid(template.formatted("{\"url\":\""+FhirDefinitions.STANDARDIZED+"\",\"valueString\":\"true\"}"))).isFalse();
        assertThat(validation.valid(template.formatted("{\"url\":\""+FhirDefinitions.CORRECTION+"\",\"valueReference\":{\"reference\":\"Observation/o1\"}}"))).isTrue();
        assertThat(validation.valid(template.formatted("{\"url\":\"https://example.org/unknown-extension\",\"valueString\":\"never-fetch\"}"))).isFalse();
    }

    @Test void strictJsonRejectsDuplicatePropertiesTrailingDocumentsAndRepresentationAbuse() {
        assertThatThrownBy(() -> JSON.readTree("{\"type\":1,\"type\":2}")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> JSON.readTree("{} {}" )).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> JSON.readTree("{\"number\":NaN}")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> JSON.readTree("[".repeat(65)+"0"+"]".repeat(65))).isInstanceOf(Exception.class);
    }

    @Test void unhandledSecurityAndClinicalFieldsCannotDisappearDuringParsing() throws Exception {
        for(String resource:new String[]{
                "{\"resourceType\":\"Patient\",\"modifierExtension\":[]}",
                "{\"resourceType\":\"Patient\",\"_birthDate\":{\"extension\":[]}}",
                "{\"resourceType\":\"Patient\",\"meta\":{\"security\":[]}}",
                "{\"resourceType\":\"Patient\",\"active\":false}",
                "{\"resourceType\":\"Patient\",\"identifier\":[{\"type\":{\"extension\":[]}}]}"}) {
            JsonNode parsed=JSON.readTree(resource);
            assertThatThrownBy(() -> new RawProfile().resource(parsed)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
