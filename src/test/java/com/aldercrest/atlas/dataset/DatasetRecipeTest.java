package com.aldercrest.atlas.dataset;

import ca.uhn.fhir.context.FhirContext;
import com.aldercrest.atlas.clinical.Analyte;
import com.aldercrest.atlas.clinical.ClinicalModels.Context;
import com.aldercrest.atlas.clinical.ClinicalModels.Flag;
import com.aldercrest.atlas.clinical.ClinicalModels.Sex;
import com.aldercrest.atlas.clinical.ReferenceRanges;
import com.aldercrest.atlas.ingest.FhirDefinitions;
import com.aldercrest.atlas.ingest.FhirValidation;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.aldercrest.atlas.ingest.RawProfile;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.aldercrest.atlas.dataset.DatasetRecipe.*;

/** Pure recipe/profile tests. They do not claim HTTP ingestion, workflow or dataset loading. */
class DatasetRecipeTest {
    @Test void approvedCohortCountsIncludeDeferredAndReplaySourcesExactlyOnce() {
        var recipe=create(100,10_000);
        assertThat(recipe.patients()).hasSize(100);assertThat(recipe.encounters()).hasSize(106);assertThat(recipe.observations()).hasSize(10_000);
        assertThat(recipe.observations().stream().filter(row->INITIAL.equals(row.phase())).count()).isEqualTo(9998);
        assertThat(recipe.observations().stream().filter(row->CORRECTION.equals(row.phase())).count()).isEqualTo(1);
        assertThat(recipe.observations().stream().filter(row->REPLAY.equals(row.phase())).count()).isEqualTo(1);
        Set<String> keys=new HashSet<>();
        recipe.patients().forEach(row->assertThat(keys.add(row.key())).isTrue());
        recipe.encounters().forEach(row->assertThat(keys.add(row.key())).isTrue());
        recipe.observations().forEach(row->assertThat(keys.add(row.key())).isTrue());
        assertThat(keys).hasSize(10_206);
    }

    @Test void semanticInputsAndEncodedInitialBytesAreDeterministicWithoutWallClockOrServerIds() {
        var first=create(100,10_000);var second=create(100,10_000);
        assertThat(first.patients()).isEqualTo(second.patients());assertThat(first.encounters()).isEqualTo(second.encounters());
        assertThat(first.observations()).isEqualTo(second.observations());assertThat(first.scenarios()).isEqualTo(second.scenarios());
        for(var patient:first.patients()) {assertThat(first.initialBundle(patient.key())).isEqualTo(second.initialBundle(patient.key()));}
        assertThat(urn("p000")).isEqualTo(urn("p000")).startsWith("urn:uuid:");
        assertThat(urn("p000")).isNotEqualTo(urn("p001"));
    }

    @Test void allRelationshipsAreCompleteAndInitialBundlesResolveForwardReferencesWithinBounds() throws Exception {
        var recipe=create(100,10_000);Map<String,PatientSpec> patients=new HashMap<>();Map<String,EncounterSpec> encounters=new HashMap<>();
        recipe.patients().forEach(row->patients.put(row.key(),row));recipe.encounters().forEach(row->encounters.put(row.key(),row));
        for(var observation:recipe.observations()) {
            assertThat(patients).containsKey(observation.patientKey());
            assertThat(encounters.get(observation.encounterKey()).patientKey()).isEqualTo(observation.patientKey());
            assertThat(observation.effective()).isBefore(AS_OF.atStartOfDay().toInstant(ZoneOffset.UTC));
            assertThat(observation.effective().atOffset(ZoneOffset.UTC).toLocalDate()).isAfterOrEqualTo(patients.get(observation.patientKey()).birthDate());
            assertThat(observation.code()).isNotIn("39156-5","98979-8");
        }
        RawProfile profile=new RawProfile();
        for(var patient:recipe.patients()) {
            String bytes=recipe.initialBundle(patient.key());assertThat(bytes.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(2*1024*1024);
            JsonNode bundle=IngestionJson.mapper().readTree(bytes);assertThat(bundle.path("type").asText()).isEqualTo("transaction");
            JsonNode entries=bundle.path("entry");assertThat(entries.size()).isLessThanOrEqualTo(1000);
            assertThat(entries.get(0).at("/resource/resourceType").asText()).isEqualTo("Observation");
            assertThat(entries.get(entries.size()-1).at("/resource/resourceType").asText()).isEqualTo("Patient");
            Set<String> fullUrls=new HashSet<>();for(JsonNode entry:entries) {assertThat(fullUrls.add(entry.path("fullUrl").asText())).isTrue();}
            for(JsonNode entry:entries) {
                JsonNode resource=entry.path("resource");assertThatCode(()->profile.resource(resource)).doesNotThrowAnyException();
                assertThat(entry.at("/request/method").asText()).isEqualTo("POST");
                assertThat(entry.at("/request/url").asText()).isEqualTo(resource.path("resourceType").asText());
                if(resource.has("subject")) {assertThat(fullUrls).contains(resource.at("/subject/reference").asText());}
                if(resource.has("encounter")) {assertThat(fullUrls).contains(resource.at("/encounter/reference").asText());}
            }
        }
    }

    @Test void representativeClinicalResourcesValidateWithActualLocalHapiR4Definitions() throws Exception {
        var recipe=create(100,10_000);var validator=new FhirValidation(FhirContext.forR4Cached());
        // Covers source units, protected local class, sex extension, held/critical signals,
        // and one whole background patient's BP components/ordinary clinical formats.
        for(String patient:List.of("p000","p001","p003","p009","p010")) {
            for(JsonNode entry:IngestionJson.mapper().readTree(recipe.initialBundle(patient)).path("entry")) {
                assertThat(validator.valid(entry.path("resource").toString())).as(entry.at("/resource/id").asText()).isTrue();
            }
        }
    }

    @Test void bothCreatinineUnitsAndTeenProtectedPersonasAreExplicitSyntheticInputs() {
        var recipe=create(100,10_000);var rows=observations(recipe);
        var mg=rows.get("p000-creatinine-mgdl");var si=rows.get("p000-creatinine-umol");
        assertThat(mg.patientKey()).isEqualTo(si.patientKey());assertThat(mg.code()).isEqualTo("2160-0");assertThat(si.code()).isEqualTo("2160-0");
        assertThat(mg.unitCode()).isEqualTo("mg/dL");assertThat(si.unitCode()).isEqualTo("umol/L");
        assertThat(si.value().divide(new BigDecimal("88.4"))).isEqualByComparingTo(mg.value());
        assertThat(mg.standardized()).isTrue();assertThat(si.standardized()).isTrue();
        assertThat(recipe.scenarios().get("teenProtected")).hasSize(6);
        for(String patient:List.of("p001","p002")) {
            PatientSpec spec=recipe.patients().stream().filter(row->row.key().equals(patient)).findFirst().orElseThrow();
            assertThat(Period.between(spec.birthDate(),AS_OF).getYears()).isBetween(12,17);
            assertThat(recipe.encounters().stream().filter(row->row.patientKey().equals(patient)).map(EncounterSpec::classCode).toList())
                    .containsExactly("AMB","ABSL","SUBU","MHEV");
        }
        assertThat(Period.between(recipe.patients().get(7).birthDate(),AS_OF).getYears()).isEqualTo(18);
    }

    @Test void haemoglobinBoundaryUsesOnePatientsHistoricalEffectiveAgeAndSex() {
        var recipe=create(100,10_000);var rows=observations(recipe);var patient=recipe.patients().get(3);
        var before=rows.get("p003-hb-before14");var after=rows.get("p003-hb-after14");
        Context context=new Context(patient.birthDate(),Sex.FEMALE);ReferenceRanges ranges=new ReferenceRanges();
        assertThat(before.patientKey()).isEqualTo(after.patientKey());assertThat(patient.sex()).isEqualTo("female");
        assertThat(context.ageAt(before.effective()).years()).isEqualTo(13);assertThat(context.ageAt(after.effective()).years()).isEqualTo(14);
        var childhood=ranges.interpret(Analyte.HAEMOGLOBIN,before.value(),context,before.effective());
        var adolescent=ranges.interpret(Analyte.HAEMOGLOBIN,after.value(),context,after.effective());
        assertThat(childhood.flag()).isEqualTo(Flag.NORMAL);assertThat(childhood.range().low()).isEqualByComparingTo("11");
        assertThat(adolescent.flag()).isEqualTo(Flag.LOW);assertThat(adolescent.range().low()).isEqualByComparingTo("12");
    }

    @Test void amendmentIsDeferredAndKeepsContextWhileMissingHeightCannotBeFilledByBackground() {
        var recipe=create(100,10_000);var rows=observations(recipe);var original=rows.get("p004-k-original");var amendment=rows.get("p004-k-amended");
        assertThat(original.phase()).isEqualTo(INITIAL);assertThat(amendment.phase()).isEqualTo(CORRECTION);
        assertThat(amendment.correctionOf()).isEqualTo(original.key());assertThat(amendment.patientKey()).isEqualTo(original.patientKey());
        assertThat(amendment.encounterKey()).isEqualTo(original.encounterKey());assertThat(amendment.effective()).isEqualTo(original.effective());
        assertThat(amendment.code()).isEqualTo(original.code());assertThat(original.value()).isEqualByComparingTo("4");assertThat(amendment.value()).isEqualByComparingTo("5.8");
        assertThat(recipe.observations().stream().filter(row->row.patientKey().equals("p005")).map(ObservationSpec::code).toList()).containsExactly("29463-7");
        assertThat(recipe.observations().stream().filter(row->row.patientKey().equals("p000") && REPLAY.equals(row.phase())).count()).isEqualTo(1);
        assertThatCode(()->observationResource(amendment,"Patient/returned-patient","Encounter/returned-encounter",null))
                .isInstanceOf(IllegalArgumentException.class);
        var resource=observationResource(amendment,"Patient/returned-patient","Encounter/returned-encounter","Observation/returned-original");
        assertThat(resource.get("status")).isEqualTo("amended");
        JsonNode json=IngestionJson.mapper().valueToTree(resource);
        assertThat(json.at("/extension/0/url").asText()).isEqualTo(FhirDefinitions.CORRECTION);
        assertThat(json.at("/extension/0/valueReference/reference").asText()).isEqualTo("Observation/returned-original");
    }

    @Test void multiyearSeriesAndCriticalReleaseModesAreExplicitWithoutInventedThresholds() {
        var recipe=create(100,10_000);var rows=observations(recipe);var series=recipe.scenarios().get("multiYear").stream().map(rows::get).toList();
        assertThat(series).hasSize(94);assertThat(series.getFirst().effective()).isEqualTo(Instant.parse("2019-01-01T08:00:00Z"));
        assertThat(series.getLast().effective()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
        assertThat(series.stream().map(ObservationSpec::code).distinct().toList()).containsExactly("2951-2");
        assertThat(rows.get("p009-held-na").releaseMode()).isEqualTo(HOLD);assertThat(rows.get("p009-held-na").sourceCritical()).isFalse();
        assertThat(rows.get("p009-held-critical-k").releaseMode()).isEqualTo(HOLD_CRITICAL);
        assertThat(rows.get("p009-released-critical-k").releaseMode()).isEqualTo(CRITICAL_FULL);
        for(String key:List.of("p009-held-critical-k","p009-released-critical-k")) {
            var spec=rows.get(key);assertThat(spec.sourceCritical()).isTrue();
            JsonNode resource=IngestionJson.mapper().valueToTree(observationResource(spec,urn(spec.patientKey()),urn(spec.encounterKey()),null));
            assertThat(resource.at("/interpretation/0/coding/0/code").asText()).isEqualTo("HH");
        }
    }

    @Test void backgroundCoversTheSourceTableComponentsUnitsAndExplicitUnknownContexts() {
        var recipe=create(100,10_000);var background=recipe.observations().stream().filter(row->Integer.parseInt(row.patientKey().substring(1))>=10).toList();
        assertThat(background).hasSize(9885);
        assertThat(background.stream().map(ObservationSpec::code).distinct().toList()).containsExactlyInAnyOrder(
                "85354-9","8302-2","29463-7","718-7","2951-2","2947-0","2160-0","4548-4");
        assertThat(background.stream().filter(row->row.code().equals("2160-0")).map(ObservationSpec::unitCode).distinct().toList())
                .containsExactlyInAnyOrder("mg/dL","umol/L");
        assertThat(recipe.patients().stream().filter(row->row.sex().equals("unknown")).count()).isPositive();
        var bp=background.stream().filter(row->row.code().equals("85354-9")).findFirst().orElseThrow();
        assertThat(bp.value()).isNull();assertThat(bp.components()).hasSize(2);
        JsonNode resource=IngestionJson.mapper().valueToTree(observationResource(bp,urn(bp.patientKey()),urn(bp.encounterKey()),null));
        assertThat(resource.has("valueQuantity")).isFalse();
        assertThat(resource.at("/component/0/code/coding/0/code").asText()).isEqualTo("8480-6");
        assertThat(resource.at("/component/1/code/coding/0/code").asText()).isEqualTo("8462-4");
        assertThatThrownBy(()->bp.components().get(0).put("extra",true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void impossibleCohortSizesFailClearlyInsteadOfOverfillingAnIngestBundle() {
        for(int[] size:List.of(new int[]{9,10_000},new int[]{1001,10_000},new int[]{100,114},new int[]{10,116},new int[]{11,1114})) {
            assertThatThrownBy(()->create(size[0],size[1])).isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_DATASET_SIZE");
        }
        assertThat(create(10,115).observations()).hasSize(115);
        assertThatThrownBy(()->create(100,10_000).initialBundle("unavailable")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String,ObservationSpec> observations(DatasetRecipe recipe) {
        Map<String,ObservationSpec> rows=new HashMap<>();recipe.observations().forEach(row->rows.put(row.key(),row));return rows;
    }
}
