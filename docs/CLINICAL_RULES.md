# Clinical engine contract

**Known terminology conflict:** the brief labels `2947-0` as potassium; official LOINC defines it as sodium in blood. The candidate adopted retaining this convention only for the synthetic assessment with prominent disclosure. Verified specimen/terminology mapping and clinical-owner approval are required before real clinical use; [source conflict and actual adoption](decisions/group-6-loinc-clarification.md).


The supplied §2.4 table and candidate-adopted policies govern. The pure Java clinical layer has no JDBC, HTTP or authorization bypass. ClinicalInputNormalizer is the shared mapping that Group4 must call inside its validated transaction adapter; ReferenceRanges and ClinicalInterpreter are shared presentation rules that Group5 must call inside authorized audited actions. A pure-engine pass is not endpoint enforcement.

## Exact table and interpretation

| Measurement | LOINC | Machine UCUM | Applicable reference interval |
|---|---|---|---|
| Systolic BP component | 8480-6 | mm[Hg] |90–120 at18+|
| Diastolic BP component |8462-4|mm[Hg]|60–80 at18+|
| Height |8302-2|cm|none|
| Weight |29463-7|kg|none|
| BMI |39156-5|kg/m2|18.5–24.9 at18+|
| Haemoglobin |718-7|g/dL|age0–13:11–14; male14+:13.5–17.5; female14+:12–15.5|
| Sodium |2951-2|mmol/L|135–145|
| Potassium |2947-0|mmol/L|3.5–5.1|
| Creatinine |2160-0|mg/dL|male0.7–1.3; female0.6–1.1|
| HbA1c |4548-4|%|4.0–5.6|
| eGFR |98979-8|mL/s/{1.73_m2}|derived; no supplied interval|

Range version: atlas-assessment-table-v1. Bounds are inclusive, compared as BigDecimal before display rounding. LOW/HIGH are not invented critical thresholds. NO_APPLICABLE_FLAG plus reason is distinct from NORMAL. Missing numeric value has VALUE_UNAVAILABLE and no normal flag. Age-independent rows do not gain an adult gate. Creatinine depends on explicit applicable clinical sex but not age; paediatric Hb needs age but not sex. Unknown/inconsistent context affects only dependent interpretation.

Context is the immutable observation snapshot, with completed ISO years at its effective UTC date; no system-date or current-patient lookup is used. Partial/missing/invalid source demographics must map to unavailable context with provenance during ingestion, never administrative Patient.gender fallback. [Actual candidate clarifications](decisions/group-3-clarifications.md) record the adopted March1 leap-day convention and derived-only inputs.

## Clinical input mapping

The supported profile requires exactly one LOINC coding for the analyte, numeric Quantity or explicit dataAbsentReason, and canonical UCUM system/code. Quantity.unit is display only. Quantity comparators and unsupported types/units are rejected. Canonical scalar units are those above; creatinine alone additionally accepts umol/L, divided by88.4 with DECIMAL128 arithmetic. No rounding to a display scale occurs before interpretation. µmol/L is permitted as presentation text, not as the accepted machine code. A value can remain absent or physiologically unsuitable without fabricating a replacement.

BP uses one chosen85354-9 parent, no scalar parent value, exactly one8480-6 and one8462-4 component, each with its own quantity/absence and interpretation. Stored parent unit is mm[Hg]; component units remain explicit. Standalone scalar BP and incoming BMI/eGFR values are rejected by this declared assessment profile. An absent value is preserved as null; BP parent absence is represented by its components rather than a contradictory parent dataAbsentReason. Full R4 structural validation, semantic supported-field checks and safe whole-Bundle response remain Group4.

73 ClinicalRulesTest cases passed in the initial focused run: every range branch's just-below/lower/upper/just-above boundaries; age14/18 and UTC dates; missing/inconsistent sex/age; leap birthday; no-range distinction; machine units/conversion/display independence; BP components and profile rejection. The pure derived calculators and independent oracle cases are a separate meaningful increment within the same Group3; full group evidence follows their verification.
## BMI and visible histories

VisibleClinicalSeries applies its supplied stored-rights/visibility predicate before correction ancestry, head selection or derived anchors. The secured detail server now supplies its SQL-filtered stored-rights/release/proxy policy inside its read/audit transaction; remaining routes must reuse that boundary. The pure wrapper does not authenticate or authorize by itself. Concealed ancestors withhold descendants; concealed successors do not remove visible originals. Visible heads are resolved before status eligibility, preventing revival of an original behind an ineligible visible successor. Raw authorized versions remain available separately. Ancestor validity is memoized; traversal is iterative and linear over the visible graph, rather than quadratic over long chains.

BMI uses latest eligible visible height/weight at or before each distinct visible height/weight timestamp. Events are ordered with a TreeMap (O(n logn)); a same-time height/weight pair creates one anchor. Hidden events cannot add a point, timestamp or provenance entry. Simultaneous competing readings yield an ambiguity reason rather than an arbitrary ID choice. The latest null/nonpositive reading produces an explicit reason rather than silently falling back to an old valid value. No freshness cutoff is invented.

The calculation is weight_kg / (height_cm /100)^2 using DECIMAL128, without display rounding before flags. Selected source IDs/times and a caller-view/freshness notice accompany the value. Conflicting birth snapshots or a selected source predating its recorded birth are inconsistent inputs. BMI itself can be computed below18 or without age because its physical formula does not need age; the adult range then remains unavailable. Missing age is not fabricated from another source snapshot. Numeric-scale arithmetic failure yields NUMERIC_UNREPRESENTABLE, not zero or a made-up number. Safe scale shifting avoids expanding pathological scientific notation.

## eGFR and independent numerical verification

For known historical clinical sex, completed age18+, positive standardized canonical creatinine, apply2021 race-free CKD-EPI creatinine:

`142 × min(SCr/κ,1)^α × max(SCr/κ,1)^(-1.200) × 0.9938^age × (1.012 if female)`

SCr is mg/dL; κ=0.7 female/0.9 male; α=-0.241 female/-0.302 male. Divide the unrounded mL/min/1.73m2 result by **exactly60**. Return display unit mL/s/1.73m2 and machine UCUM mL/s/{1.73_m2}, with98979-8 and equation method/version/input IDs/times/source link. There is no race argument or normal range. Under18, missing/inconsistent age/sex/value, nonpositive creatinine, unverified standardization, ineligible status or unrepresentable arithmetic gives an explicit unavailable reason. There is no invented upper-age cutoff or assay measuring interval.

Sources: [NIDDK equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults), [NKF published test ticket p2](https://www.kidney.org/sites/default/files/example_it_ticket-implement_2021_ckd-epi_equation_to_calculate_egfr_from_creatinine_1.pdf), [LOINC98979-8](https://loinc.org/98979-8), [UCUM](https://ucum.org/ucum). The [oracle record](evidence/clinical-oracle.md) distinguishes eight published rounded examples from independently computed full-precision values. The fixture includes24 numerical cases, including both creatinine units and splice boundaries. Java compares canonical SCr within1e-12, per-minute within1e-9 and per-second within1e-11; published whole numbers are compared after separate rounding. Reproducible optional Node verification: `node scripts/verify-clinical-oracle.mjs`; it reads, never rewrites, the fixture. The required Maven test suite needs no Node runtime.

Standardization is an explicit synthetic input assertion, not inferred from LOINC/Quantity. Actual assay traceability/measurement interval, clinical suitability/freshness and clinical-owner approval remain upstream production gates. The CDC source supports only the [BMI arithmetic](https://www.cdc.gov/bmi/adult-calculator/bmi-categories.html); the assessment's exact18+/18.5–24.9 range controls and is not replaced by CDC category guidance.

HistoricalContext resolves explicit nonoverlapping sex assertions at the effective UTC date with [from,to) intervals, accepts adjacent intervals and leaves gaps/unknown assertions unknown. No administrative-gender input exists. Three focused tests cover boundary selection, changed historical interpretation, preserved prior snapshots, gaps/unknowns and invalid/overlapping intervals. Group4 now parses the HTTPS extension and persists assertions/context/provenance in its create-only patient action. There is no existing-patient demographic-edit path or independent patient lock; assertions are validated together before the new patient transaction commits. The pure resolver itself performs no database work.

Final Group3 verification:115 clinical cases plus1FHIR runtime and8real-database foundation cases,124total, zero failures/errors/skips. The13BMI cases include a10000-version correction-chain regression and hidden-middle-ancestor case; this is correctness/stack-safety evidence, not production latency measurement. The optional independent JavaScript verifier reproduced all24fixture values and8published rounded examples. See the group evidence for exact commands and limits.
