package com.aldercrest.atlas.clinical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** Explicit effective-dated clinical assertions only; administrative gender is not an input. */
public final class HistoricalContext {
    public record SexAssertion(Sex sex, LocalDate validFrom, LocalDate validToExclusive) { }

    public Context resolve(LocalDate birthDate, List<SexAssertion> assertions, Instant effective) {
        List<SexAssertion> ordered = validate(assertions);
        if (effective == null) { return new Context(birthDate,Sex.UNKNOWN); }
        LocalDate date = effective.atOffset(ZoneOffset.UTC).toLocalDate();
        Sex sex = ordered.stream().filter(assertion -> !date.isBefore(assertion.validFrom())
                && (assertion.validToExclusive() == null || date.isBefore(assertion.validToExclusive())))
                .map(SexAssertion::sex).findFirst().orElse(Sex.UNKNOWN);
        return new Context(birthDate,sex);
    }

    public List<SexAssertion> validate(List<SexAssertion> assertions) {
        if (assertions == null) { return List.of(); }
        for (SexAssertion assertion : assertions) {
            if (assertion == null || assertion.sex() == null || assertion.validFrom() == null
                    || (assertion.validToExclusive() != null && !assertion.validToExclusive().isAfter(assertion.validFrom()))) {
                throw new ClinicalInputException("INVALID_CLINICAL_SEX_INTERVAL");
            }
        }
        List<SexAssertion> ordered = assertions.stream().sorted(Comparator.comparing(SexAssertion::validFrom)).toList();
        for (int index = 1; index < ordered.size(); index++) {
            SexAssertion previous = ordered.get(index-1);
            if (previous.validToExclusive() == null || ordered.get(index).validFrom().isBefore(previous.validToExclusive())) {
                throw new ClinicalInputException("OVERLAPPING_CLINICAL_SEX_INTERVALS");
            }
        }
        return ordered;
    }
}
