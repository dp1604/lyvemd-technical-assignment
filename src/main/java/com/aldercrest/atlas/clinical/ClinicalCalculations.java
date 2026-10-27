package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.ArrayList;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

public final class ClinicalCalculations {
    public static final String EGFR_METHOD = "CKD-EPI-2021-creatinine-v1";
    public static final String EGFR_SOURCE = "https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults";
    public static final String BMI_METHOD = "atlas-visible-as-of-bmi-v1";
    public static final String BMI_NOTICE = "Latest inputs available to this caller; source dates shown. No clinical freshness window has been established.";
    private final ReferenceRanges ranges = new ReferenceRanges();

    public Derived egfr(Observation creatinine) {
        if (creatinine.analyte() != Analyte.CREATININE) { throw new IllegalArgumentException("Creatinine input required."); }
        List<Source> sources = List.of(source(creatinine));
        Reason unavailable = null;
        Age age = creatinine.context().ageAt(creatinine.effective());
        if (!creatinine.eligibleForDerivation()) { unavailable = Reason.INPUT_STATUS_INELIGIBLE; }
        else if (creatinine.value() == null) { unavailable = Reason.VALUE_UNAVAILABLE; }
        else if (creatinine.value().signum() <= 0) { unavailable = Reason.CREATININE_NONPOSITIVE; }
        else if (age.years() == null) { unavailable = age.reason(); }
        else if (age.years() < 18) { unavailable = Reason.AGE_NOT_APPLICABLE; }
        else if (creatinine.context().sex() == Sex.UNKNOWN) { unavailable = Reason.SEX_UNAVAILABLE; }
        else if (!creatinine.standardizedCreatinine()) { unavailable = Reason.CREATININE_NOT_STANDARDIZED; }
        if (unavailable != null) { return unavailable(Analyte.EGFR, creatinine.effective(), sources, unavailable, EGFR_METHOD, EGFR_SOURCE); }

        double serum = creatinine.value().doubleValue();
        if (!Double.isFinite(serum) || serum <= 0) {
            return unavailable(Analyte.EGFR, creatinine.effective(), sources, Reason.NUMERIC_UNREPRESENTABLE, EGFR_METHOD, EGFR_SOURCE);
        }
        boolean female = creatinine.context().sex() == Sex.FEMALE;
        double kappa = female ? 0.7 : 0.9;
        double alpha = female ? -0.241 : -0.302;
        double ratio = serum / kappa;
        double perMinute = 142.0 * StrictMath.pow(Math.min(ratio, 1.0), alpha)
                * StrictMath.pow(Math.max(ratio, 1.0), -1.200)
                * StrictMath.pow(0.9938, age.years()) * (female ? 1.012 : 1.0);
        if (!Double.isFinite(perMinute) || perMinute <= 0) {
            return unavailable(Analyte.EGFR, creatinine.effective(), sources, Reason.NUMERIC_UNREPRESENTABLE, EGFR_METHOD, EGFR_SOURCE);
        }
        // Convert the unrounded per-minute equation result; never substitute a display label or rounded0.0167.
        BigDecimal perSecond = BigDecimal.valueOf(perMinute).divide(new BigDecimal("60"), UnitNormalizer.PRECISION);
        return computed(Analyte.EGFR, perSecond, creatinine.effective(), creatinine.context(), sources, EGFR_METHOD, EGFR_SOURCE);
    }

    public List<Derived> egfrSeries(VisibleClinicalSeries view) {
        return view.latestEligibleHeads().stream().filter(row -> row.analyte() == Analyte.CREATININE)
                .sorted(Comparator.comparing(Observation::effective).thenComparing(Observation::id))
                .map(this::egfr).toList();
    }

    public List<Derived> bmiSeries(VisibleClinicalSeries view) {
        List<Observation> inputs = view.latestEligibleHeads().stream()
                .filter(row -> row.analyte() == Analyte.HEIGHT || row.analyte() == Analyte.WEIGHT).toList();
        // One anchor per distinct visible input time: hidden events cannot add even identical points.
        TreeMap<Instant, List<Observation>> events = new TreeMap<>();
        inputs.forEach(row -> events.computeIfAbsent(row.effective(), time -> new ArrayList<>()).add(row));
        List<Observation> heights = List.of();
        List<Observation> weights = List.of();
        List<Derived> result = new ArrayList<>();
        for (var event : events.entrySet()) {
            List<Observation> nextHeights = event.getValue().stream().filter(row -> row.analyte() == Analyte.HEIGHT).toList();
            List<Observation> nextWeights = event.getValue().stream().filter(row -> row.analyte() == Analyte.WEIGHT).toList();
            if (!nextHeights.isEmpty()) { heights = nextHeights; }
            if (!nextWeights.isEmpty()) { weights = nextWeights; }
            result.add(bmiAt(heights, weights, event.getKey()));
        }
        return List.copyOf(result);
    }

    private Derived bmiAt(List<Observation> heights, List<Observation> weights, Instant anchor) {
        List<Source> knownSources = java.util.stream.Stream.concat(heights.stream(), weights.stream())
                .sorted(Comparator.comparing(Observation::id)).map(this::source).toList();
        Reason reason = heights.isEmpty() ? Reason.HEIGHT_UNAVAILABLE : weights.isEmpty() ? Reason.WEIGHT_UNAVAILABLE
                : heights.size() != 1 ? Reason.HEIGHT_AMBIGUOUS : weights.size() != 1 ? Reason.WEIGHT_AMBIGUOUS : null;
        if (reason != null) { return unavailable(Analyte.BMI, anchor, knownSources, reason, BMI_METHOD, BMI_NOTICE); }
        Observation height = heights.getFirst();
        Observation weight = weights.getFirst();
        if (height.value() == null) { reason = Reason.HEIGHT_UNAVAILABLE; }
        else if (weight.value() == null) { reason = Reason.WEIGHT_UNAVAILABLE; }
        else if (height.value().signum() <= 0) { reason = Reason.HEIGHT_NONPOSITIVE; }
        else if (weight.value().signum() <= 0) { reason = Reason.WEIGHT_NONPOSITIVE; }
        LocalDate heightBirth = height.context().birthDate();
        LocalDate weightBirth = weight.context().birthDate();
        if (reason == null && heightBirth != null && weightBirth != null && !heightBirth.equals(weightBirth)) {
            reason = Reason.CONTEXT_INCONSISTENT;
        }
        if (reason == null && (height.context().ageAt(height.effective()).reason() == Reason.AGE_INCONSISTENT
                || weight.context().ageAt(weight.effective()).reason() == Reason.AGE_INCONSISTENT)) {
            reason = Reason.CONTEXT_INCONSISTENT;
        }
        if (reason != null) { return unavailable(Analyte.BMI, anchor, knownSources, reason, BMI_METHOD, BMI_NOTICE); }
        Context context = new Context(Objects.equals(heightBirth, weightBirth) ? heightBirth : null, Sex.UNKNOWN);
        try {
            BigDecimal meters = height.value().scaleByPowerOfTen(-2);
            BigDecimal bmi = weight.value().divide(meters.multiply(meters, UnitNormalizer.PRECISION), UnitNormalizer.PRECISION);
            return computed(Analyte.BMI, bmi, anchor, context, knownSources, BMI_METHOD, BMI_NOTICE);
        } catch (ArithmeticException error) {
            return unavailable(Analyte.BMI, anchor, knownSources, Reason.NUMERIC_UNREPRESENTABLE, BMI_METHOD, BMI_NOTICE);
        }
    }

    private Source source(Observation row) { return new Source(row.id(), row.effective()); }
    private Derived computed(Analyte analyte, BigDecimal value, Instant effective, Context context,
                             List<Source> sources, String method, String notice) {
        return new Derived(analyte, value, effective, ranges.interpret(analyte, value, context, effective),
                Reason.NONE, sources, method, notice);
    }
    private Derived unavailable(Analyte analyte, Instant effective, List<Source> sources, Reason reason, String method, String notice) {
        return new Derived(analyte, null, effective, new Interpretation(null, Flag.NO_APPLICABLE_FLAG, reason),
                reason, sources, method, notice);
    }
}
