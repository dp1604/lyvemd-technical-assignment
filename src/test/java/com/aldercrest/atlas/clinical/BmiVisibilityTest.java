package com.aldercrest.atlas.clinical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;
import static org.assertj.core.api.Assertions.*;

class BmiVisibilityTest {
    private final ClinicalCalculations calculations = new ClinicalCalculations();
    private static final Context ADULT = new Context(LocalDate.of(2000,1,1),Sex.UNKNOWN);

    @Test
    void bmiUsesBothAnchorTypesTheLatestAsOfInputsAndNoFutureHeight() {
        Observation w1 = row("w1",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        Observation h1 = row("h1",Analyte.HEIGHT,"180","2026-01-02T00:00:00Z");
        Observation w2 = row("w2",Analyte.WEIGHT,"72.9","2026-01-03T00:00:00Z");
        Observation h2 = row("h2",Analyte.HEIGHT,"200","2026-01-04T00:00:00Z");
        List<Derived> result = bmi(List.of(h2,w2,w1,h1),Set.of());
        assertThat(result).hasSize(4);
        assertThat(result.getFirst().value()).isNull();
        assertThat(result.getFirst().cannotComputeReason()).isEqualTo(Reason.HEIGHT_UNAVAILABLE);
        assertThat(result.get(1).value()).isEqualByComparingTo("25");
        assertThat(result.get(2).value()).isEqualByComparingTo("22.5");
        assertThat(result.getLast().value()).isEqualByComparingTo("18.225");
        assertThat(result.getLast().sources()).containsExactly(new Source("h2",h2.effective()),new Source("w2",w2.effective()));
        assertThat(result.get(1).interpretation().flag()).isEqualTo(Flag.HIGH);
        assertThat(result.get(2).interpretation().flag()).isEqualTo(Flag.NORMAL);
        assertThat(result.getLast().notice()).contains("this caller", "No clinical freshness");
    }

    @Test
    void concealedNewMeasurementsCannotChangeValuesAnchorsCountsTimesOrProvenance() {
        Observation height = row("h",Analyte.HEIGHT,"180","2020-01-01T00:00:00Z");
        Observation weight = row("w",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        List<Observation> baseline = List.of(height,weight);
        List<Observation> extended = new ArrayList<>(baseline);
        extended.add(row("hidden-h",Analyte.HEIGHT,"200","2026-01-02T00:00:00Z"));
        extended.add(row("hidden-w",Analyte.WEIGHT,"81","2026-01-03T00:00:00Z"));
        assertThat(bmi(extended,Set.of("hidden-h","hidden-w"))).isEqualTo(bmi(baseline,Set.of()));
        assertThat(bmi(baseline,Set.of()).getLast().value()).isEqualByComparingTo("25");
    }

    @Test
    void concealedAndAbsentHeightHaveIdenticalPublicUnavailableResults() {
        Observation weight = row("w",Analyte.WEIGHT,"70","2026-01-02T00:00:00Z");
        Observation height = row("hidden",Analyte.HEIGHT,"170","2026-01-01T00:00:00Z");
        assertThat(bmi(List.of(weight,height),Set.of("hidden"))).isEqualTo(bmi(List.of(weight),Set.of()));
        assertThat(bmi(List.of(height),Set.of("hidden"))).isEmpty();
    }

    @Test
    void concealedSuccessorCannotRelabelOrRemoveItsVisibleOriginalInput() {
        Observation original = row("h1",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation correction = copy(original,"h2", "200","corrected","h1",ADULT);
        Observation weight = row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z");
        List<Derived> baseline = bmi(List.of(original,weight),Set.of());
        assertThat(bmi(List.of(original,correction,weight),Set.of("h2"))).isEqualTo(baseline);
        List<Derived> corrected = bmi(List.of(original,correction,weight),Set.of());
        assertThat(corrected.getLast().value()).isEqualByComparingTo("20.25");
        assertThat(corrected.getLast().sources()).contains(new Source("h2",original.effective())).doesNotContain(new Source("h1",original.effective()));
        assertThat(VisibleClinicalSeries.filter("p",List.of(original,correction,weight),row->true).observations()).hasSize(3);
    }

    @Test
    void correctionWithConcealedAncestorIsAlsoWithheld() {
        Observation original = row("hidden-original",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation correction = copy(original,"h2","200","corrected",original.id(),ADULT);
        Observation weight = row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z");
        assertThat(bmi(List.of(original,correction,weight),Set.of(original.id()))).isEqualTo(bmi(List.of(weight),Set.of()));
    }

    @Test
    void simultaneousConflictingInputsAreAmbiguousAndDuplicateAnchorTimesAreCollapsed() {
        Observation h1 = row("h1",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation h2 = row("h2",Analyte.HEIGHT,"200","2026-01-01T00:00:00Z");
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        List<Derived> result = bmi(List.of(w,h2,h1),Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().cannotComputeReason()).isEqualTo(Reason.HEIGHT_AMBIGUOUS);
        assertThat(bmi(List.of(h1,w),Set.of())).hasSize(1);
    }

    @Test
    void invalidLatestInputsDoNotSilentlyFallBackToOlderOnes() {
        Observation h = row("h",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        for (String value : new String[]{"0","-1"}) {
            assertThat(bmi(List.of(h,w,row("bad",Analyte.HEIGHT,value,"2026-01-02T00:00:00Z")),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.HEIGHT_NONPOSITIVE);
            assertThat(bmi(List.of(h,w,row("bad",Analyte.WEIGHT,value,"2026-01-02T00:00:00Z")),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.WEIGHT_NONPOSITIVE);
        }
        assertThat(bmi(List.of(h,w,row("missing",Analyte.HEIGHT,null,"2026-01-02T00:00:00Z")),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.HEIGHT_UNAVAILABLE);
    }

    @Test
    void onlyEligibleStatusesAndMatchingPatientCanCreateDerivedAnchors() {
        Observation h = row("h",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z");
        for (String status : List.of("preliminary","registered","cancelled","entered-in-error","unknown")) {
            Observation ineligible = copy(w,"pending","100",status,null,ADULT);
            assertThat(bmi(List.of(h,ineligible),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.WEIGHT_UNAVAILABLE);
        }
        Observation other = new Observation("other","other-patient",null,Analyte.HEIGHT,new BigDecimal("200"),w.effective(),"final",null,List.of(),ADULT,false);
        assertThat(bmi(List.of(h,w,other),Set.of())).isEqualTo(bmi(List.of(h,w),Set.of()));
    }

    @Test
    void bmiCanBeComputedUnder18ButHasNoAdultFlagAndDoesNotInventMissingAge() {
        Context child = new Context(LocalDate.of(2010,1,1),Sex.FEMALE);
        Observation h = row("h",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        Derived minor = bmi(List.of(copy(h,"h","180","final",null,child),copy(w,"w","81","final",null,child)),Set.of()).getFirst();
        assertThat(minor.value()).isEqualByComparingTo("25");
        assertThat(minor.interpretation().reason()).isEqualTo(Reason.AGE_NOT_APPLICABLE);
        Context missing = new Context(null,Sex.UNKNOWN);
        Derived unknown = bmi(List.of(copy(h,"h","180","final",null,missing),w),Set.of()).getFirst();
        assertThat(unknown.value()).isEqualByComparingTo("25");
        assertThat(unknown.interpretation().reason()).isEqualTo(Reason.AGE_UNAVAILABLE);
        Context conflicting = new Context(LocalDate.of(2001,1,1),Sex.UNKNOWN);
        assertThat(bmi(List.of(copy(h,"h","180","final",null,conflicting),w),Set.of()).getFirst().cannotComputeReason()).isEqualTo(Reason.CONTEXT_INCONSISTENT);
    }

    @Test
    void malformedCorrectionCycleDoesNotCreateAnInputAndExtremeScaleFailsSafely() {
        Observation h = row("h",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z");
        assertThat(bmi(List.of(copy(h,"h","180","corrected","h",ADULT),w),Set.of()).getFirst().cannotComputeReason()).isEqualTo(Reason.HEIGHT_UNAVAILABLE);
        Observation extreme = row("h",Analyte.HEIGHT,"1E2147483647","2026-01-01T00:00:00Z");
        assertThat(bmi(List.of(extreme,w),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.NUMERIC_UNREPRESENTABLE);
    }

    @Test
    void selectedMeasurementCannotPredateItsRecordedBirthDateEvenAtALaterValidAnchor() {
        Observation impossibleHeight = row("h",Analyte.HEIGHT,"180","1999-01-01T00:00:00Z");
        Observation weight = row("w",Analyte.WEIGHT,"81","2026-01-01T00:00:00Z");
        assertThat(bmi(List.of(impossibleHeight,weight),Set.of()).getLast().cannotComputeReason()).isEqualTo(Reason.CONTEXT_INCONSISTENT);
    }

    @Test
    void ineligibleVisibleSuccessorDoesNotReviveItsOriginal() {
        Observation h = row("h",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        Observation ineligible = copy(h,"h2","200","preliminary","h",ADULT);
        Observation w = row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z");
        assertThat(bmi(List.of(h,ineligible,w),Set.of()).getFirst().cannotComputeReason()).isEqualTo(Reason.HEIGHT_UNAVAILABLE);
    }

    @Test
    void longCorrectionHistoriesRemainIterativeAndKeepOnlyTheLatestVisibleHead() {
        List<Observation> rows = new ArrayList<>();
        Observation original = row("h0",Analyte.HEIGHT,"180","2026-01-01T00:00:00Z");
        rows.add(original);
        for (int index = 1; index < 10000; index++) {
            rows.add(copy(original,"h"+index,index == 9999 ? "200" : "180","corrected","h"+(index-1),ADULT));
        }
        rows.add(row("w",Analyte.WEIGHT,"81","2026-01-02T00:00:00Z"));
        assertThat(bmi(rows,Set.of()).getLast().value()).isEqualByComparingTo("20.25");
        Derived concealedMiddle = bmi(rows,Set.of("h5000")).getLast();
        assertThat(concealedMiddle.value()).isEqualByComparingTo("25");
        assertThat(concealedMiddle.sources()).contains(new Source("h4999",original.effective()));
    }

    private List<Derived> bmi(List<Observation> rows,Set<String> hiddenIds) {
        return calculations.bmiSeries(VisibleClinicalSeries.filter("p",rows,row -> !hiddenIds.contains(row.id())));
    }
    private Observation row(String id,Analyte analyte,String value,String time) {
        return new Observation(id,"p",null,analyte,value == null ? null : new BigDecimal(value),Instant.parse(time),"final",null,List.of(),ADULT,false);
    }
    private Observation copy(Observation row,String id,String value,String status,String correctionOf,Context context) {
        return new Observation(id,row.patientId(),row.encounterId(),row.analyte(),new BigDecimal(value),row.effective(),status,correctionOf,List.of(),context,false);
    }
}
