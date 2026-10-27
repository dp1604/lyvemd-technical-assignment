# Clinical engine contract

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