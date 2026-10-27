package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;
import static org.assertj.core.api.Assertions.*;

class ClinicalRulesTest {
    private final ReferenceRanges ranges = new ReferenceRanges();
    private static final LocalDate BIRTH = LocalDate.of(2000, 1, 1);

    static Stream<Arguments> boundaries() {
        Object[][] table = {
            {Analyte.SYSTOLIC,18,Sex.UNKNOWN,"90","120"},
            {Analyte.DIASTOLIC,18,Sex.UNKNOWN,"60","80"},
            {Analyte.BMI,18,Sex.UNKNOWN,"18.5","24.9"},
            {Analyte.HAEMOGLOBIN,0,Sex.UNKNOWN,"11","14"},
            {Analyte.HAEMOGLOBIN,13,Sex.FEMALE,"11","14"},
            {Analyte.HAEMOGLOBIN,14,Sex.MALE,"13.5","17.5"},
            {Analyte.HAEMOGLOBIN,14,Sex.FEMALE,"12","15.5"},
            {Analyte.SODIUM,0,Sex.UNKNOWN,"135","145"},
            {Analyte.POTASSIUM,0,Sex.UNKNOWN,"3.5","5.1"},
            {Analyte.CREATININE,0,Sex.MALE,"0.7","1.3"},
            {Analyte.CREATININE,0,Sex.FEMALE,"0.6","1.1"},
            {Analyte.HBA1C,0,Sex.UNKNOWN,"4.0","5.6"}
        };
        return Stream.of(table).flatMap(row -> {
            BigDecimal low = new BigDecimal((String)row[3]);
            BigDecimal high = new BigDecimal((String)row[4]);
            BigDecimal epsilon = new BigDecimal("0.0000000001");
            return Stream.of(Arguments.of(row[0],row[1],row[2],low.subtract(epsilon),Flag.LOW),
                    Arguments.of(row[0],row[1],row[2],low,Flag.NORMAL),
                    Arguments.of(row[0],row[1],row[2],high,Flag.NORMAL),
                    Arguments.of(row[0],row[1],row[2],high.add(epsilon),Flag.HIGH));
        });
    }

    @ParameterizedTest @MethodSource("boundaries")
    void everyRangeBranchUsesExactInclusiveEndpoints(Analyte analyte, int age, Sex sex, BigDecimal value, Flag expected) {
        Interpretation result = ranges.interpret(analyte,value,new Context(BIRTH,sex),atAge(age));
        assertThat(result.flag()).isEqualTo(expected);
        assertThat(result.reason()).isEqualTo(Reason.NONE);
    }

    @ParameterizedTest @EnumSource(value=Analyte.class,names={"HEIGHT","WEIGHT","EGFR"})
    void noRangeNeverMeansNormal(Analyte analyte) {
        Interpretation result = ranges.interpret(analyte,new BigDecimal("100"),null,null);
        assertThat(result.flag()).isEqualTo(Flag.NO_APPLICABLE_FLAG);
        assertThat(result.range()).isNull();
        assertThat(result.reason()).isEqualTo(Reason.NO_REFERENCE_RANGE);
    }

    @ParameterizedTest @EnumSource(value=Analyte.class,names={"SYSTOLIC","DIASTOLIC","BMI"})
    void adultRangesAreUnavailableJustBefore18(Analyte analyte) {
        Context context = new Context(BIRTH,Sex.MALE);
        assertThat(ranges.interpret(analyte,BigDecimal.TEN,context,atAge(18).minusNanos(1)).reason())
                .isEqualTo(Reason.AGE_NOT_APPLICABLE);
        assertThat(ranges.interpret(analyte,BigDecimal.TEN,context,atAge(18)).range()).isNotNull();
    }

    @Test
    void ageAndSexComeFromObservationContextAndUtcDate() {
        Instant effective = java.time.OffsetDateTime.parse("2014-01-01T00:30:00+01:00").toInstant();
        Context old = new Context(BIRTH,Sex.UNKNOWN);
        assertThat(old.ageAt(effective).years()).isEqualTo(13);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12"),old,effective).flag()).isEqualTo(Flag.NORMAL);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12"),new Context(BIRTH,Sex.MALE),atAge(14)).flag()).isEqualTo(Flag.LOW);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12"),new Context(BIRTH,Sex.FEMALE),atAge(14)).flag()).isEqualTo(Flag.NORMAL);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12"),old,atAge(14)).reason()).isEqualTo(Reason.SEX_UNAVAILABLE);
    }

    @Test
    void leapDayUsesTheCandidateAdoptedMarchFirstRule() {
        Context context = new Context(LocalDate.of(2008,2,29),Sex.FEMALE);
        assertThat(context.ageAt(Instant.parse("2026-02-28T23:59:59Z")).years()).isEqualTo(17);
        assertThat(context.ageAt(Instant.parse("2026-03-01T00:00:00Z")).years()).isEqualTo(18);
    }

    @Test
    void missingAndInconsistentDemographicsDoNotInvalidateIndependentRanges() {
        Context missing = new Context(null,Sex.UNKNOWN);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,BigDecimal.TEN,missing,atAge(20)).reason()).isEqualTo(Reason.AGE_UNAVAILABLE);
        Context futureBirth = new Context(LocalDate.of(2030,1,1),Sex.MALE);
        assertThat(ranges.interpret(Analyte.BMI,BigDecimal.TEN,futureBirth,atAge(20)).reason()).isEqualTo(Reason.AGE_INCONSISTENT);
        for (Analyte analyte : List.of(Analyte.SODIUM,Analyte.POTASSIUM,Analyte.HBA1C)) {
            assertThat(ranges.interpret(analyte,BigDecimal.TEN,missing,null).range()).isNotNull();
        }
        assertThat(ranges.interpret(Analyte.CREATININE,BigDecimal.ONE,new Context(null,Sex.MALE),null).flag()).isEqualTo(Flag.NORMAL);
        assertThat(ranges.interpret(Analyte.CREATININE,BigDecimal.ONE,missing,null).reason()).isEqualTo(Reason.SEX_UNAVAILABLE);
        assertThat(ranges.interpret(Analyte.SODIUM,null,missing,null).reason()).isEqualTo(Reason.VALUE_UNAVAILABLE);
    }

    @ParameterizedTest @EnumSource(Analyte.class)
    void canonicalMachineUnitsAreExplicitAndDisplayIndependent(Analyte analyte) {
        Quantity input = quantity("10",analyte.unitCode()).setUnit("deliberately misleading display");
        CanonicalQuantity canonical = new UnitNormalizer().normalize(analyte,input);
        assertThat(canonical.value()).isEqualByComparingTo("10");
        assertThat(canonical.unitCode()).isEqualTo(analyte.unitCode());
    }

    @Test
    void creatinineConversionUsesUcumCodeBeforeRangesAndPreservesPrecision() {
        UnitNormalizer units = new UnitNormalizer();
        CanonicalQuantity fromSi = units.normalize(Analyte.CREATININE,quantity("97.24","umol/L").setUnit("mg/dL"));
        assertThat(fromSi.value()).isEqualByComparingTo("1.1");
        assertThat(fromSi.unitCode()).isEqualTo("mg/dL");
        assertThat(fromSi.sourceUnitCode()).isEqualTo("umol/L");
        assertThat(ranges.interpret(Analyte.CREATININE,fromSi.value(),new Context(null,Sex.FEMALE),null).flag()).isEqualTo(Flag.NORMAL);
        assertThat(units.normalize(Analyte.CREATININE,quantity("88.4","mg/dL").setUnit("µmol/L")).value()).isEqualByComparingTo("88.4");
    }

    @Test
    void unsupportedUnitsComparatorsAndNonNumericRepresentationsAreRejected() {
        UnitNormalizer units = new UnitNormalizer();
        for (String unit : List.of("µmol/L","mmol/L","MG/DL","mL/s/1.73m2")) {
            assertThatThrownBy(() -> units.normalize(Analyte.CREATININE,quantity("1",unit))).isInstanceOf(ClinicalInputException.class);
        }
        assertThatThrownBy(() -> units.normalize(Analyte.CREATININE,quantity("1","mg/dL").setSystem("wrong"))).isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> units.normalize(Analyte.CREATININE,quantity("1","mg/dL").setComparator(Quantity.QuantityComparator.LESS_THAN))).isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> quantity("NaN","mg/dL")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> quantity("Infinity","mg/dL")).isInstanceOf(NumberFormatException.class);
        assertThat(new UnitNormalizer().normalize(Analyte.CREATININE,new Quantity().setSystem(UnitNormalizer.UCUM).setCode("mg/dL")).value()).isNull();
    }

    @Test
    void oneBloodPressureObservationKeepsItsTwoIndependentFlags() {
        org.hl7.fhir.r4.model.Observation input = fhir(Analyte.BLOOD_PRESSURE);
        input.addComponent().setCode(code(Analyte.DIASTOLIC)).setValue(quantity("81","mm[Hg]"));
        input.addComponent().setCode(code(Analyte.SYSTOLIC)).setValue(quantity("120","mm[Hg]"));
        Measurement measurement = new ClinicalInputNormalizer().normalize(input);
        Observation row = new Observation("bp","patient",null,measurement.analyte(),null,atAge(18),"final",null,
                measurement.components(),new Context(BIRTH,Sex.UNKNOWN),false);
        ResultInterpretation result = new ClinicalInterpreter().interpret(row);
        assertThat(result.interpretation().reason()).isEqualTo(Reason.COMPONENT_RESULT);
        assertThat(result.components()).hasSize(2);
        assertThat(result.components().getFirst().code()).isEqualTo("8462-4");
        assertThat(result.components().getFirst().interpretation().flag()).isEqualTo(Flag.HIGH);
        assertThat(result.components().getLast().interpretation().flag()).isEqualTo(Flag.NORMAL);
    }

    @Test
    void inputProfileRejectsScalarBpBadComponentsAndUpstreamDerivedValues() {
        ClinicalInputNormalizer normalizer = new ClinicalInputNormalizer();
        for (Analyte analyte : List.of(Analyte.SYSTOLIC,Analyte.DIASTOLIC,Analyte.BMI,Analyte.EGFR)) {
            assertThatThrownBy(() -> normalizer.normalize(fhir(analyte).setValue(quantity("1",analyte.unitCode())))).isInstanceOf(ClinicalInputException.class);
        }
        org.hl7.fhir.r4.model.Observation bp = fhir(Analyte.BLOOD_PRESSURE);
        bp.addComponent().setCode(code(Analyte.SYSTOLIC)).setValue(quantity("120","mm[Hg]"));
        assertThatThrownBy(() -> normalizer.normalize(bp)).isInstanceOf(ClinicalInputException.class);
        bp.addComponent().setCode(code(Analyte.SYSTOLIC)).setValue(quantity("80","mm[Hg]"));
        assertThatThrownBy(() -> normalizer.normalize(bp)).isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> normalizer.normalize(fhir(Analyte.SODIUM))).isInstanceOf(ClinicalInputException.class);
        org.hl7.fhir.r4.model.Observation absent = fhir(Analyte.WEIGHT).setDataAbsentReason(new CodeableConcept().setText("not measured"));
        assertThat(normalizer.normalize(absent).quantity().value()).isNull();
    }

    static Quantity quantity(String value,String unit) {
        return new Quantity().setValue(new BigDecimal(value)).setSystem(UnitNormalizer.UCUM).setCode(unit);
    }
    static Instant atAge(int age) { return BIRTH.plusYears(age).atStartOfDay().toInstant(ZoneOffset.UTC); }
    static CodeableConcept code(Analyte analyte) { return new CodeableConcept().addCoding(new org.hl7.fhir.r4.model.Coding(ClinicalInputNormalizer.LOINC,analyte.code(),null)); }
    static org.hl7.fhir.r4.model.Observation fhir(Analyte analyte) { return new org.hl7.fhir.r4.model.Observation().setCode(code(analyte)); }
}
