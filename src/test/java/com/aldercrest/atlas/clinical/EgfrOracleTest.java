package com.aldercrest.atlas.clinical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;
import static org.assertj.core.api.Assertions.*;

class EgfrOracleTest {
    static Stream<JsonNode> referenceCases() throws IOException {
        try (var source = EgfrOracleTest.class.getResourceAsStream("/clinical/egfr-oracle.json")) {
            JsonNode cases = new ObjectMapper().readTree(source).get("valid");
            return StreamSupport.stream(cases.spliterator(),false).toList().stream();
        }
    }

    @ParameterizedTest(name="{index}: independent reference") @MethodSource("referenceCases")
    void matchesIndependentPiecewiseReferenceAndPublishedRoundedExamples(JsonNode reference) {
        CanonicalQuantity normalized = new UnitNormalizer().normalize(Analyte.CREATININE,
                ClinicalRulesTest.quantity(reference.get("creatinine").asText(),reference.get("unitCode").asText()));
        assertThat(normalized.value().doubleValue()).isCloseTo(reference.get("expectedCreatinineMgDl").asDouble(),within(1e-12));
        Sex sex = Sex.valueOf(reference.get("clinicalSex").asText().toUpperCase(java.util.Locale.ROOT));
        Observation input = observation(normalized.value(),reference.get("ageYears").asInt(),sex,true,"final");
        Derived result = new ClinicalCalculations().egfr(input);
        assertThat(result.computable()).as(reference.get("id").asText()).isTrue();
        assertThat(result.value().doubleValue()).isCloseTo(reference.get("expectedPerSecond").asDouble(),within(1e-11));
        double perMinute = result.value().multiply(new BigDecimal("60")).doubleValue();
        assertThat(perMinute).isCloseTo(reference.get("expectedPerMinute").asDouble(),within(1e-9));
        if (reference.hasNonNull("publishedWholePerMinute")) {
            assertThat(Math.round(perMinute)).isEqualTo(reference.get("publishedWholePerMinute").asLong());
        }
        assertThat(result.method()).isEqualTo(ClinicalCalculations.EGFR_METHOD);
        assertThat(result.notice()).isEqualTo(ClinicalCalculations.EGFR_SOURCE);
        assertThat(result.interpretation().flag()).isEqualTo(Flag.NO_APPLICABLE_FLAG);
        assertThat(result.interpretation().reason()).isEqualTo(Reason.NO_REFERENCE_RANGE);
        assertThat(result.sources()).containsExactly(new Source(input.id(),input.effective()));
        assertThat(Analyte.EGFR.code()).isEqualTo("98979-8");
        assertThat(Analyte.EGFR.unitCode()).isEqualTo("mL/s/{1.73_m2}");
        assertThat(Analyte.EGFR.unit()).isEqualTo("mL/s/1.73m2");
    }

    @Test
    void rejectsUnsuitableInputsWithoutFabricatingADerivedNumber() {
        assertUnavailable(observation(null,18,Sex.MALE,true,"final"),Reason.VALUE_UNAVAILABLE);
        assertUnavailable(observation(BigDecimal.ZERO,18,Sex.MALE,true,"final"),Reason.CREATININE_NONPOSITIVE);
        assertUnavailable(observation(new BigDecimal("-1"),18,Sex.MALE,true,"final"),Reason.CREATININE_NONPOSITIVE);
        assertUnavailable(observation(BigDecimal.ONE,17,Sex.MALE,true,"final"),Reason.AGE_NOT_APPLICABLE);
        assertUnavailable(observation(BigDecimal.ONE,18,Sex.UNKNOWN,true,"final"),Reason.SEX_UNAVAILABLE);
        assertUnavailable(observation(BigDecimal.ONE,18,Sex.MALE,false,"final"),Reason.CREATININE_NOT_STANDARDIZED);
        assertUnavailable(observation(BigDecimal.ONE,18,Sex.MALE,true,"preliminary"),Reason.INPUT_STATUS_INELIGIBLE);
        assertUnavailable(observation(new BigDecimal("1E1000"),18,Sex.MALE,true,"final"),Reason.NUMERIC_UNREPRESENTABLE);
        assertUnavailable(observation(new BigDecimal("1E-1000"),18,Sex.MALE,true,"final"),Reason.NUMERIC_UNREPRESENTABLE);
        Observation original = observation(BigDecimal.ONE,18,Sex.MALE,true,"final");
        assertUnavailable(withContext(original,new Context(null,Sex.MALE)),Reason.AGE_UNAVAILABLE);
        assertUnavailable(withContext(original,new Context(LocalDate.of(2030,1,1),Sex.MALE)),Reason.AGE_INCONSISTENT);
    }

    @Test
    void observationTimeContextDeterminesTheFormulaWithoutAnUpperAgeCutoff() {
        Derived male = new ClinicalCalculations().egfr(observation(BigDecimal.ONE,90,Sex.MALE,true,"final"));
        Derived female = new ClinicalCalculations().egfr(observation(BigDecimal.ONE,90,Sex.FEMALE,true,"final"));
        assertThat(male.computable()).isTrue();
        assertThat(female.computable()).isTrue();
        assertThat(male.value()).isNotEqualByComparingTo(female.value());
        assertThat(new ClinicalCalculations().egfr(observation(BigDecimal.ONE,120,Sex.MALE,true,"final")).computable()).isTrue();
    }

    private void assertUnavailable(Observation row, Reason reason) {
        Derived result = new ClinicalCalculations().egfr(row);
        assertThat(result.value()).isNull();
        assertThat(result.cannotComputeReason()).isEqualTo(reason);
        assertThat(result.interpretation().flag()).isEqualTo(Flag.NO_APPLICABLE_FLAG);
    }
    private Observation withContext(Observation row,Context context) {
        return new Observation(row.id(),row.patientId(),null,row.analyte(),row.value(),row.effective(),row.status(),null,List.of(),context,row.standardizedCreatinine());
    }
    private Observation observation(BigDecimal value,int age,Sex sex,boolean standardized,String status) {
        return new Observation("creatinine","patient",null,Analyte.CREATININE,value,Instant.parse("2026-01-01T12:00:00Z"),status,null,
                List.of(),new Context(LocalDate.of(2026-age,1,1),sex),standardized);
    }
}
