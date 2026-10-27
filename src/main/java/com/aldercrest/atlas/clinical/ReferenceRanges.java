package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** Assessment table/version only: no population ranges, critical thresholds or current-date input. */
public final class ReferenceRanges {
    public static final String VERSION = "atlas-assessment-table-v1";
    private record Applicable(Range range, Reason reason) { }

    public Interpretation interpret(Analyte analyte, BigDecimal value, Context context, Instant effective) {
        Applicable applicable = range(analyte, context, effective);
        if (value == null && analyte != Analyte.BLOOD_PRESSURE) {
            return new Interpretation(applicable.range(), Flag.NO_APPLICABLE_FLAG, Reason.VALUE_UNAVAILABLE);
        }
        if (applicable.range() == null) {
            return new Interpretation(null, Flag.NO_APPLICABLE_FLAG, applicable.reason());
        }
        Flag flag = value.compareTo(applicable.range().low()) < 0 ? Flag.LOW
                : value.compareTo(applicable.range().high()) > 0 ? Flag.HIGH : Flag.NORMAL;
        return new Interpretation(applicable.range(), flag, Reason.NONE);
    }

    private Applicable range(Analyte analyte, Context context, Instant effective) {
        Context actual = context == null ? new Context(null, Sex.UNKNOWN) : context;
        return switch (analyte) {
            case HEIGHT, WEIGHT, EGFR -> none(Reason.NO_REFERENCE_RANGE);
            case BLOOD_PRESSURE -> none(Reason.COMPONENT_RESULT);
            case SODIUM -> limits("135", "145");
            case POTASSIUM -> limits("3.5", "5.1");
            case HBA1C -> limits("4.0", "5.6");
            case CREATININE -> sex(actual.sex(), "0.7", "1.3", "0.6", "1.1");
            case HAEMOGLOBIN -> {
                Age age = actual.ageAt(effective);
                if (age.years() == null) { yield none(age.reason()); }
                yield age.years() < 14 ? limits("11", "14")
                        : sex(actual.sex(), "13.5", "17.5", "12", "15.5");
            }
            case SYSTOLIC, DIASTOLIC, BMI -> {
                Age age = actual.ageAt(effective);
                if (age.years() == null) { yield none(age.reason()); }
                if (age.years() < 18) { yield none(Reason.AGE_NOT_APPLICABLE); }
                yield switch (analyte) {
                    case SYSTOLIC -> limits("90", "120");
                    case DIASTOLIC -> limits("60", "80");
                    default -> limits("18.5", "24.9");
                };
            }
        };
    }

    private Applicable sex(Sex sex, String maleLow, String maleHigh, String femaleLow, String femaleHigh) {
        return switch (sex) {
            case MALE -> limits(maleLow, maleHigh);
            case FEMALE -> limits(femaleLow, femaleHigh);
            default -> none(Reason.SEX_UNAVAILABLE);
        };
    }
    private Applicable limits(String low, String high) {
        return new Applicable(new Range(new BigDecimal(low), new BigDecimal(high)), Reason.NONE);
    }
    private Applicable none(Reason reason) { return new Applicable(null, reason); }
}
