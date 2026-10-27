package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

public final class ClinicalModels {
    private ClinicalModels() { }

    public enum Sex { MALE, FEMALE, UNKNOWN }
    public enum Flag { LOW, NORMAL, HIGH, NO_APPLICABLE_FLAG }
    public enum Reason {
        NONE, NO_REFERENCE_RANGE, COMPONENT_RESULT, VALUE_UNAVAILABLE,
        AGE_UNAVAILABLE, AGE_INCONSISTENT, AGE_NOT_APPLICABLE, SEX_UNAVAILABLE,
        HEIGHT_UNAVAILABLE, WEIGHT_UNAVAILABLE, HEIGHT_AMBIGUOUS, WEIGHT_AMBIGUOUS,
        HEIGHT_NONPOSITIVE, WEIGHT_NONPOSITIVE, CONTEXT_INCONSISTENT,
        CREATININE_NONPOSITIVE, CREATININE_NOT_STANDARDIZED, NUMERIC_UNREPRESENTABLE,
        INPUT_STATUS_INELIGIBLE
    }
    public record Age(Integer years, Reason reason) { }
    public record Context(LocalDate birthDate, Sex sex) {
        public Context { if (sex == null) { sex = Sex.UNKNOWN; } }
        public Age ageAt(Instant effective) {
            if (birthDate == null || effective == null) { return new Age(null, Reason.AGE_UNAVAILABLE); }
            LocalDate date = effective.atOffset(ZoneOffset.UTC).toLocalDate();
            if (birthDate.isAfter(date)) { return new Age(null, Reason.AGE_INCONSISTENT); }
            // Candidate adopted ISO completed years: Feb29 advances on Mar1 in non-leap years.
            return new Age(Period.between(birthDate, date).getYears(), Reason.NONE);
        }
    }
    public record Range(BigDecimal low, BigDecimal high) { }
    public record Interpretation(Range range, Flag flag, Reason reason) { }
    public record ComponentInterpretation(String code, BigDecimal value, String unitCode, String unit,
                                          Interpretation interpretation) { }
    public record ResultInterpretation(Interpretation interpretation, List<ComponentInterpretation> components) {
        public ResultInterpretation { components = List.copyOf(components); }
    }
    public record CanonicalQuantity(BigDecimal value, String unitCode, String sourceUnitCode) { }
    public record Component(Analyte analyte, CanonicalQuantity quantity) { }
    public record Measurement(Analyte analyte, CanonicalQuantity quantity, List<Component> components) {
        public Measurement { components = List.copyOf(components); }
    }
    public record Observation(String id, String patientId, String encounterId, Analyte analyte,
                              BigDecimal value, Instant effective, String status, String correctionOf,
                              List<Component> components, Context context, boolean standardizedCreatinine) {
        public Observation {
            components = components == null ? List.of() : List.copyOf(components);
            context = context == null ? new Context(null, Sex.UNKNOWN) : context;
        }
        public boolean eligibleForDerivation() {
            return "final".equals(status) || "amended".equals(status) || "corrected".equals(status);
        }
    }
    public record Source(String observationId, Instant effective) { }
    public record Derived(Analyte analyte, BigDecimal value, Instant effective,
                          Interpretation interpretation, Reason cannotComputeReason,
                          List<Source> sources, String method, String notice) {
        public Derived { sources = List.copyOf(sources); }
        public boolean computable() { return value != null; }
    }
}
