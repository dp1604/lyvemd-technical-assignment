Current contribution status: [subsequent candidate review](../CANDIDATE_REVIEW.md). The evidence below preserves its original checkpoint; later human review does not alter that historical execution or result.

# Group3 — clinical rules and calculation verification

Tasks52–62 clinical-layer implementation and verification passed on27October2026. This is the pure calculation/input-mapping checkpoint, not completed HTTP ingestion, persisted datasets, authorization or clinical release. Those integrations remain Groups4–7/10. Actual code increments are privately pushed as c1b8d3c (units/ranges) and db823b0 (derivations/history/oracle); the workflow instruction has its own c100d2e commit. Final evidence/state publication and history recovery are separate milestone gates recorded after commit.

## Source and decisions

The exact eleven-row clinical table, R3 and §2.4 bullets govern; A/B bytes and the exact adopted access instruction are unchanged. [Group3 answers](../decisions/group-3-clarifications.md) explicitly adopt derived-only incoming BMI/eGFR and March1 leap-day age arithmetic. Codex recommended the input restriction after the candidate requested advice; only the later actual answer adopted it. No interviewer preference, law, clinical approval or real feed specification is invented.

Implementation and detailed contract: [clinical rules](../CLINICAL_RULES.md). Immutable observation context is used, including a pure effective-dated sex resolver; current patient demographics/administrative gender are not substituted. Group4 must parse/persist this context and invoke clinical normalization in its atomic action. Actual input-profile StructureDefinitions and transport/semantic validation remain that group.

## Executed checks

| Suite | Cases | Actual scope |
|---|---:|---|
| ClinicalRulesTest |73|All interval branches and inclusive/outside bounds, ages14/18, UTC/leap dates, dependent/independent missing context, no-range state, UCUM/display authority, BP components and derived-only rejection|
| BmiVisibilityTest |13|Latest as-of input/anchor types, full-output hidden/absent equality, no hidden extra points/timestamps/provenance, corrections/ancestors, same-time ambiguity, missing/nonpositive/ineligible inputs, context inconsistency, pathological decimal scale,10000-version chain|
| EgfrOracleTest |26|24separate numerical references, published whole-number comparison, unit equivalence, unsupported/missing inputs, standardization, age/sex/applicability/numeric failure and no invented upper-age cutoff|
| HistoricalContextTest |3|UTC[from,to), dated sex change, unknown/gap behavior, old snapshot retention and invalid/overlapping assertions|
| Existing FhirRuntimeTest |1|Actual required R4 parser/instance-validator runtime|
| Existing FoundationIT |8|Real PostgreSQL16 model/privilege/transaction/audit/error regression|
| **Total** |**124**|Zero failures/errors/skips|

Focused progression:73cases passed first;111passed after derivation tests/review fixes;112passed with long-chain regression. Final `pwsh -NoProfile -File ./scripts/verify.ps1` passed116unit plus8integration cases with BUILD SUCCESS,1m53s, finished10:36:16 Asia/Colombo. This duration is a build/test observation, not API latency or a future delivery estimate. No tests were skipped. The packaged JAR excludes test controllers/fixtures; source fixtures remain synthetic repository test assets. Existing nonfatal HAPI optional-schematron and JDK/Mockito warnings remain, without a schematron-validation claim.

Optional `node scripts/verify-clinical-oracle.mjs` also passed:24numerical fixture comparisons and8published NKF rounded examples. It uses a separate piecewise JavaScript expression, reads rather than overwrites fixtures, and does not import Java implementation. Node is not required for the Maven build. [Oracle research](clinical-oracle.md) and the fixture distinguish published rounded integers from our independent full-precision calculations. Absolute tolerances are1e-12canonical creatinine,1e-9per-minute eGFR and1e-11per-second eGFR; published integers are checked only after separate rounding. The same physiological equation is being transcribed independently; this is not independent clinical/assay validation.

## Review findings addressed

The [rules/test-design audit](../ai/outputs/group-3-rules-test-audit.md), [actual-code audit/follow-ups](../ai/outputs/group-3-implementation-audit.md) and [numeric audit](../ai/outputs/group-3-numeric-audit.md) are actual AI-origin contributions, not human discoveries or reviewer-executed tests. Root corrected a pre-birth source reused at a later valid BMI anchor, resolved visible correction heads before eligibility to prevent original revival, and memoized ancestor checks. Focused regressions passed; follow-up static review found no further blocker in this bounded pure layer. The historical resolver received a separate follow-up review.

No clinical test failed in these executed runs; findings were corrected before their corresponding final tests. A scratch publication-manifest guard initially rejected the legitimate curated docs/ai/outputs directory; it was narrowed to ignored root directories before staging/publication. It changed no product behavior. Earlier unpresented BMI-policy changes remain Group1 history, not silently reattributed to this implementation.

## Explanation for the candidate

1. **Normalize first.** The machine code umol/L causes creatinine to divide by88.4;97.24becomes1.1mg/dL. A misleading display label cannot change that. Compare the canonical unrounded number with the task interval.
2. **Interpret at the measurement's time.** Hb12g/dL is in the11–14 interval at age13; at age14 it is below the male13.5lower bound but at the female12lower bound. The stored historical context selects the applicable branch. Sodium/potassium/HbA1c need no age; creatinine needs sex but has no source adult gate.
3. **A missing interval is not normal.** Height, weight and eGFR have no supplied reference interval. Child BP/BMI and unknown dependent context also have explicit reasons and NO_APPLICABLE_FLAG. LOW/HIGH are range flags, not critical thresholds or release permission.
4. **BMI is view-scoped.**81kg and180cm give81/(1.8²)=25kg/m2, HIGH against the task's adult upper24.9. Use the latest authorized/visible inputs at or before the visible anchor and show source dates. Concealed measurements cannot alter old values or create extra identical points. No height means HEIGHT_UNAVAILABLE; do not invent a number or silently use a future/invalid measurement. No freshness window was supplied, so this does not claim clinical currency.
5. **eGFR includes an actual unit conversion.** For age18/male/standardized SCr0.90mg/dL, our independently checked unrounded result is126.9609629613mL/min/1.73m2; dividing by60gives2.11601604936mL/s/1.73m2. The published NKF example rounds the per-minute number to127. Our per-second result uses the unrounded calculation. Formula version is CKD-EPI2021creatinine, with no race term; machine unit mL/s/{1.73_m2} and display text preserve the agreed UCUM clarification.
6. **Do not fabricate applicability.** eGFR requires adult age, applicable clinical sex, positive canonical creatinine and explicit standardization. Missing/inconsistent inputs or unrepresentable calculations return no value plus a reason. Source standardization/assay measuring intervals, real clinical suitability/freshness and clinical-owner approval are production gates, not verified by a synthetic boolean or these tests.

Primary references: [NIDDK2021 equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults), [NKF published verification examples p2](https://www.kidney.org/sites/default/files/example_it_ticket-implement_2021_ckd-epi_equation_to_calculate_egfr_from_creatinine_1.pdf), [LOINC98979-8](https://loinc.org/98979-8), [UCUM](https://ucum.org/ucum). The supplied task overrides imported range/reporting conventions; external sources verify arithmetic/terminology, not additional employer requirements.

The explanation is delivered for review; no candidate-run walkthrough, clinical sign-off, production authentication, secure HTTP timing proof, actual feed verification, clinical deployment readiness or full standards certification is claimed. Required later integrations must use this layer without bypassing stored authorization, release and same-transaction auditing. Task92 remains the final guided defense gate.
