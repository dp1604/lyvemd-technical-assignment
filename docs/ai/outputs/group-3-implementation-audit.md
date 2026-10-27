# Group 3 implementation audit

Status: independent AI static review of the actual own-code clinical package as read on 27 October 2026. No source mutation, executable test or Attachment A execution was performed by this lane. Root's test runs and subsequent fixes are separate evidence. Candidate has now adopted derived-only inbound BMI/eGFR handling and the ISO completed-years 1 March leap-day convention; those are no longer unresolved choices.

## Blocking correctness issue found

### G3-R1 — BMI must reject a selected measurement whose own date precedes its birth-date snapshot

Location: `ClinicalCalculations.bmiAt`, selected height/weight context checks before `computed`.

The method compares the two birth-date snapshots for equality but does not test their consistency with each selected source's effective date. It then asks `ReferenceRanges` to interpret at the later anchor. A height recorded in 1999 with DOB2000 plus a weight recorded2026 with the same DOB currently has equal birth snapshots, so it produces a numeric adult BMI using a measurement that predates birth. B4 requires explicit cannot-compute for inconsistent selected inputs; a later adult anchor must not cure that earlier inconsistency.

Recommended fix: if either selected input's `context.ageAt(input.effective()).reason()` is AGE_INCONSISTENT, return CONTEXT_INCONSISTENT (or another documented safe categorical reason). Missing DOB can remain different: BMI is numerically age-independent, with its age-dependent range unavailable. Add a regression using a pre-birth height and adult weight with identical DOB, and reverse the source types. This finding was sent to root before this report.

## Integration precondition to preserve

### G3-R2 — Status-filter-before-head selection needs an enforced correction-profile invariant

Location: `VisibleClinicalSeries.latestEligibleHeads`.

This filters final/amended/corrected rows before determining correction heads. If a visible correction-of successor could have an ineligible status (e.g. entered-in-error), its eligible original remains a derivation input even though a later visible successor exists. A3 normally creates correction-linked records as amended/corrected; if Group4 rejects correction_of on every other status and immutable storage prevents changing it later, the problematic state is not reachable through the supported profile. Enforce and test that explicit invariant during ingestion; otherwise select the latest visible chain head before checking eligibility and return an appropriate unavailable state instead of reviving an older known-invalid source. Do not claim this pure engine validates all database clinical invariants.

This is an integration requirement rather than a claim that current HTTP paths can be exploited; there are no clinical HTTP paths yet.

## Performance observation

### G3-R3 — Repeated ancestor traversal is quadratic for a long visible correction chain

Location: `VisibleClinicalSeries.filter`, loop over every visible row and walk each entire correction ancestor chain.

A chain of n visible versions performs about n(n-1)/2 ancestor lookups. BMI anchoring is already O(n logn), but this validation stage can dominate for unusually long correction histories. Consider memoizing valid/invalid ancestry with cycle detection; preserve the rule that a concealed ancestor withholds descendants and a concealed successor never suppresses a visible original. This is not a demonstrated latency failure or a current clinical blocker at the synthetic scale. Do not truncate ancestors just to cap work because that could change visibility.

## Verified by static inspection

- `ReferenceRanges` contains every supplied numerical range and applies inclusive exact BigDecimal comparisons before display formatting. Adult-only gates cover BP and BMI; creatinine has no invented adult gate. Hb ages0–13 work without known sex; Hb14+ uses clinical sex. Sodium/potassium/HbA1c do not require demographics. Height/weight/eGFR never become NORMAL merely because a numeric value exists.
- `Context.ageAt` uses effective UTC calendar dates and `Period.between(...).getYears()`, matching the adopted completed-years convention; birth after effective date yields AGE_INCONSISTENT.
- `UnitNormalizer` uses the UCUM system/code and ignores display labels for conversion, preserves source unit code, rejects comparators and unsupported representations and converts creatinine umol/L by88.4 under DECIMAL128. BigDecimal cannot represent NaN/Infinity; parser/FHIR behavior still needs direct tests in Group4.
- `ClinicalInputNormalizer` rejects inbound BMI/eGFR and scalar BP under the adopted profile. It requires exactly two distinct systolic/diastolic components for BP, supports explicit missing components through dataAbsentReason, rejects scalar observations with components and requires exactly one LOINC coding.
- `ClinicalInterpreter` independently ranges each BP component and labels the parent COMPONENT_RESULT rather than inventing one combined range.
- `egfr` uses sex-specific 2021 coefficients, min/max branches,142,0.9938^age and female1.012, with no race term. It enforces known age18+, known sex, positive valid canonical creatinine and explicit standardization. It converts the unrounded per-minute equation value by exactly60. IEEE conversion/arithmetic failure becomes an explicit unavailable reason. Independent published numerical comparisons remain root/oracle-lane evidence, not this static inspection.
- `bmiSeries` derives anchors only from visible eligible height/weight events, groups by distinct effective timestamp, updates both analytes at the same time before calculation and never uses future inputs. Same-time ambiguity returns a reason instead of iteration-order selection. BigDecimal scaleByPowerOfTen avoids unnecessary huge-exponent expansion; arithmetic exceptions become NUMERIC_UNREPRESENTABLE.
- `VisibleClinicalSeries.filter` applies the provided stored-rights visibility predicate before ancestor/head/anchor work. Missing visible ancestors and cycles withhold descendants. Hidden successors never enter supersession sets; they therefore cannot relabel or remove visible originals. The class clearly states it is a view boundary, not authorization itself.
- Source identifiers/times in derived outputs come only from selected visible inputs. Hidden events cannot add even an identical extra BMI point or timestamp because anchor construction occurs afterward. Actual routes must still fetch/filter candidates safely and produce visible-only pagination/counts.

## Tests reviewed / remaining proof

`ClinicalRulesTest` as read covers every range branch's lower/upper inclusion and just-outside values, no-range states, age18, age14/UTC, leap day, missing/inconsistent demographic handling, canonical unit metadata, misleading display text, creatinine conversion before flags, unsupported units/comparators, two independent BP components and derived-only input rejection. It does not claim an executed assertion from the tests' presence. Root was still writing/running derived/oracle tests during review.

Required derived regressions remain those in `rules-test-audit.md`, particularly hidden-vs-absent full-output equality, hidden anchors/count/timestamps, latest visible corrections, ambiguity, before-birth context and extreme numeric inputs. Later Group4 must verify actual ingestion invokes this normalizer, stores immutable context/conversion provenance, validates correction invariants and rejects malformed HAPI numeric JSON atomically. Later Group5/10 must prove SQL/HTTP visibility, transactional auditing and body/status/timing behavior. Pure-engine results do not establish those controls.

## Follow-up disposition after root changes

Read-only follow-up checked the latest actual `ClinicalCalculations`, `VisibleClinicalSeries`, `BmiVisibilityTest`, `ClinicalRulesTest` and `EgfrOracleTest`. This section preserves the original findings and distinguishes inspected fixes from root's separately running test results.

- **G3-R1 addressed by code and focused regression.** `bmiAt` now evaluates each selected source's age at that source's own effective time and returns CONTEXT_INCONSISTENT if either is AGE_INCONSISTENT. `selectedMeasurementCannotPredateItsRecordedBirthDateEvenAtALaterValidAnchor` covers the reported pre-birth height/adult-weight case. The implementation is symmetric for weight and height; missing DOB is deliberately not treated as the same inconsistency.
- **G3-R2 addressed defensively in the engine.** `latestEligibleHeads` now computes superseded IDs from every visible complete-chain row before filtering head status eligibility. An ineligible visible successor therefore prevents revival of its original. `ineligibleVisibleSuccessorDoesNotReviveItsOriginal` checks the reported state. The later input adapter should still enforce the declared A3 correction profile rather than relying on derived filtering alone.
- **G3-R3 addressed by memoized ancestry validation.** `filter` caches true/false complete-chain results for each visited path, reuses cached results for subsequent rows and retains per-walk cycle detection. Each newly resolved node is memoized, giving amortized linear ancestor work over the visible graph rather than repeated full-chain walks. Missing ancestors and cycles remain fail closed; hidden successors remain absent from supersession and anchor logic. This is algorithm inspection, not a measured performance result.
- Reviewed derived tests now explicitly cover absent/concealed height full-output equality, concealed new anchors/counts/times/provenance, concealed corrections, hidden ancestors, two anchor types, same-time ambiguity, eligible status/patient scope, invalid latest input without fallback, child/no-age BMI flag states, extreme numeric scales and the two new regressions. Oracle tests compare against a separate reference fixture and published rounded examples; the fixture's independent derivation/source verification belongs to the oracle lane/root evidence.

**No further blocking correctness issue identified in the current pure clinical engine during this follow-up.** This is bounded static review, not a claim of exhaustive correctness, executed tests, operational latency, secure clinical HTTP routes or completed ingestion/auditing. No canonical source was edited and no executable tests were run by this audit lane.

## Historical-context resolver follow-up

Read-only review of added `HistoricalContext.java` and `HistoricalContextTest.java` against B3 found no concrete blocking issue. The resolver accepts explicit clinical-sex assertions only; administrative gender is absent from its inputs. It sorts/validates intervals before selection, rejects null sex/from, zero/negative intervals, overlapping/duplicate intervals and an open-ended interval followed by another assertion. Adjacent exclusive-end/start intervals are allowed. Selection uses the observation's UTC calendar date with inclusive start/exclusive end; gaps, absent assertions and explicit UNKNOWN retain UNKNOWN rather than infer a sex. Returned immutable Context values preserve earlier snapshots.

The three new tests show historical male/female Hb interpretation switching at the exact interval boundary, missing/gap/unknown behavior and overlap/inconsistency rejection. These are actual code/test-source observations; root's integrated test execution remains separate. Group4 still must parse the project Patient extension, call this resolver under the ingestion lock and persist snapshots/provenance. Adding a pure resolver does not itself implement or verify that database/ingestion integration.
