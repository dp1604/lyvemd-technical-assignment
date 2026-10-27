package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;
import static com.aldercrest.atlas.clinical.HistoricalContext.*;
import static org.assertj.core.api.Assertions.*;

class HistoricalContextTest {
    private final HistoricalContext history = new HistoricalContext();

    @Test
    void clinicalSexIsResolvedAtTheUtcEffectiveDateUsingExclusiveEnd() {
        List<SexAssertion> assertions = List.of(new SexAssertion(Sex.FEMALE,LocalDate.of(2020,1,1),null),
                new SexAssertion(Sex.MALE,LocalDate.of(2000,1,1),LocalDate.of(2020,1,1)));
        LocalDate birth = LocalDate.of(1990,1,1);
        Context old = history.resolve(birth,assertions,Instant.parse("2019-12-31T23:59:59Z"));
        Context next = history.resolve(birth,assertions,Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(old.sex()).isEqualTo(Sex.MALE);
        assertThat(next.sex()).isEqualTo(Sex.FEMALE);
        ReferenceRanges ranges = new ReferenceRanges();
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12.5"),old,Instant.parse("2019-12-31T23:59:59Z")).flag()).isEqualTo(Flag.LOW);
        assertThat(ranges.interpret(Analyte.HAEMOGLOBIN,new BigDecimal("12.5"),next,Instant.parse("2020-01-01T00:00:00Z")).flag()).isEqualTo(Flag.NORMAL);
        assertThat(old.sex()).isEqualTo(Sex.MALE); // Later interpretation did not rewrite the old snapshot.
    }

    @Test
    void gapsUnknownOrMissingAssertionsNeverInferASex() {
        List<SexAssertion> assertions = List.of(new SexAssertion(Sex.MALE,LocalDate.of(2020,1,1),LocalDate.of(2021,1,1)));
        assertThat(history.resolve(null,assertions,Instant.parse("2021-01-01T00:00:00Z")).sex()).isEqualTo(Sex.UNKNOWN);
        assertThat(history.resolve(null,null,Instant.parse("2026-01-01T00:00:00Z")).sex()).isEqualTo(Sex.UNKNOWN);
        assertThat(history.resolve(null,List.of(new SexAssertion(Sex.UNKNOWN,LocalDate.of(2000,1,1),null)),Instant.parse("2026-01-01T00:00:00Z")).sex()).isEqualTo(Sex.UNKNOWN);
    }

    @Test
    void overlappingDuplicateOrInconsistentIntervalsAreRejectedInsteadOfChoosingAConvenientBranch() {
        SexAssertion open = new SexAssertion(Sex.MALE,LocalDate.of(2000,1,1),null);
        assertThatThrownBy(() -> history.validate(List.of(open,new SexAssertion(Sex.FEMALE,LocalDate.of(2020,1,1),null))))
                .isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> history.validate(List.of(open,open))).isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> history.validate(List.of(new SexAssertion(Sex.MALE,LocalDate.of(2020,1,1),LocalDate.of(2020,1,1)))))
                .isInstanceOf(ClinicalInputException.class);
        assertThatThrownBy(() -> history.validate(List.of(new SexAssertion(null,LocalDate.of(2020,1,1),null))))
                .isInstanceOf(ClinicalInputException.class);
    }
}
