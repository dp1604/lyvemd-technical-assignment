Current contribution status: [subsequent candidate review](../CANDIDATE_REVIEW.md). The evidence below preserves its original checkpoint; later human review does not alter that historical execution or result.

# the implementation independent clinical oracle

Research/calculation lane completed 27 October 2026, 10:17 Sri Lanka time. Scratch evidence for the integrator; no canonical project files, Git state, database or supplied PR listing were changed. The task text and adopted Package B govern requirements; external sources verify the equation and terminology, not replacement policies.

## Primary sources checked

1. [NIDDK adult equations](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults): race-free 2021 CKD-EPI creatinine equation, adult applicability, standardized assay dependency and creatinine conversion. Its equation is `142 × min(SCr/kappa,1)^alpha × max(SCr/kappa,1)^(-1.200) × 0.9938^Age × femaleFactor`; female kappa/alpha/factor are 0.7/-0.241/1.012; male values are 0.9/-0.302/1. Standardized SCr is mg/dL; divide umol/L by 88.4. The source output is per minute. The source prints an approximate per-second multiplier 0.0167; adopted exact division by 60 avoids introducing that approximation. These are estimates of function, not measured GFR. No race term belongs in this equation.
2. [NKF implementation ticket, PDF p2](https://www.kidney.org/sites/default/files/example_it_ticket-implement_2021_ckd-epi_equation_to_calculate_egfr_from_creatinine_1.pdf): actual published test combinations and rounded expected results are below. The PDF explicitly includes both branch breakpoints, age 18 and age 90. It says not to calculate below 18, with unavailable age/sex, or beyond a verified assay measuring interval. No assay interval was supplied for this assignment; do not invent one. The example ticket's reporting interval, whole-number display and reference interpretation must not silently replace the task's per-second output/ranges. Its ambiguity discussion requires a clinical/laboratory reporting decision where sex fields disagree; the adopted explicit clinical-context policy avoids inferring a branch from administrative gender.
3. [LOINC 98979-8](https://loinc.org/98979-8): active CKD-EPI 2021 creatinine-based estimated GFR term for serum/plasma/blood, normalized to 1.73 square metres; example machine unit `mL/min/{1.73_m2}`. The adopted per-second equivalent is `mL/s/{1.73_m2}`. The code alone does not prove assay traceability.
4. [UCUM specification 2.2](https://ucum.org/ucum), prefix table and sections 6–8: case-sensitive micro prefix is `u`; a period is multiplication. Braces carry a unit annotation, not decimal arithmetic. Thus accept declared machine code `umol/L`, and preserve the task's `mL/s/1.73m2` as display text rather than treating its dot as a decimal in a machine UCUM expression.

No direct quotation is needed; the facts above are paraphrased and mathematical notation is preserved for auditability.

## Published oracle versus independent computation

Published NKF results are whole numbers in **mL/min/1.73m2**, not exact full-precision results and not the task's per-second output. The following whole-number column is transcribed from the published table. The decimal columns are **our independent JavaScript evaluations** of the published equation, using explicit piecewise branches; they are not claimed to be published worked-example decimals.

| Completed age | Clinical sex | SCr mg/dL | Published NKF whole result | Independent per-minute | Independent per-second |
|---:|---|---:|---:|---:|---:|
| 18 | male | 0.90 | 127 | 126.96096296130331 | 2.1160160493550553 |
| 18 | male | 0.91 | 125 | 125.28859767063041 | 2.0881432945105067 |
| 18 | female | 0.70 | 128 | 128.48449451683896 | 2.141408241947316 |
| 18 | female | 0.71 | 126 | 126.31599570409516 | 2.1052665950682528 |
| 90 | male | 0.50 | 97 | 96.89245452145748 | 1.6148742420242914 |
| 90 | male | 1.50 | 44 | 43.9520525875727 | 0.7325342097928783 |
| 90 | female | 0.50 | 89 | 89.04202544017481 | 1.4840337573362468 |
| 90 | female | 1.50 | 33 | 32.89927374006926 | 0.5483212290011543 |

The source script `compute-oracle.mjs` writes `oracle-values.json`. It does not import or call application code. It has run successfully: 8/8 independent computations round to the NKF published integers. The generated JSON has 24 valid calculations: both accepted units for each published example, four near-breakpoint cases, and four additional age/sex combinations. Eight unavailable-input scenarios and four invalid-representation scenarios record semantic expectations. JSON uses null for missing values; nonfinite invalid examples are described as strings because JSON cannot encode NaN/Infinity as numbers.

Suggested computational comparison tolerances for these finite cases: canonical creatinine absolute error ≤1e-12 mg/dL; unrounded eGFR per-minute ≤1e-9; per-second ≤1e-11. Separately round unrounded per-minute results to whole numbers and require exact agreement with the eight published integers. Do not round per-minute output before converting to the task's per-second value.

For a concrete unit pair, `79.56 umol/L / 88.4 = 0.9 mg/dL`; age-18 male output must be 2.1160160493550553 per second in either representation. `132.6 / 88.4` may have binary floating-point noise; the corresponding expected decimal differs in the last few bits, which is why comparison uses explicit tolerances. Numerical tolerance does not permit flagging a value outside a stated clinical boundary as normal; source table boundaries should be compared without display rounding.

## Task-table and adopted-policy verification obligations

- Effective-time completed age, not current age: test immediately before and on birthdays 14 and 18; UTC date interpretation follows adopted B2/B3. An unknown/partial birth date must not invent an age. Under-14 Hb range does not require a known sex branch; age-independent Na/K/HbA1c ranges do not require an invented age.
- Check inclusive lower/upper endpoints, just below and just above: SBP 90/120; DBP 60/80; adult BMI 18.5/24.9; child Hb 11/14, male 14+ Hb 13.5/17.5, female 14+ Hb 12/15.5; Na 135/145; K 3.5/5.1; male creatinine 0.7/1.3, female 0.6/1.1; HbA1c 4/5.6. These are the assignment's table, not sourced clinical cutoffs from the external references.
- Height and weight never receive a normal flag where the task provides no range; eGFR has no supplied reference interval. Under-18 BP/BMI have no applicable task-table flag. Missing context must produce a distinct reason rather than NORMAL.
- BP retains one parent with independent component interpretations. Do not infer systolic/diastolic by component array position.
- BMI 60 kg and 150 cm independently gives `60 / 1.5^2 = 26.666666666666668`. No height gives unavailable, not zero/default-height BMI. No future input, no hidden event anchor, no extra point from a concealed event, and visible-first input/correction selection are adopted policy requirements. Application and authorization integration remain separate checks; the oracle alone does not establish them.
- eGFR unavailable for under-18, missing/unknown applicable clinical sex, absent age, missing/nonpositive creatinine, or unverified assay standardization. Malformed/nonfinite source numbers and unsupported codes are ingestion rejections under adopted B4. Positive but unsupported values must not be silently clamped into a range; any nonfinite/underflowed derived numeric output must remain explicit unavailable rather than fabricated.

## Limits and candidate decisions

No new candidate policy decision identified: the existing adopted package already resolves the unit notation, sex context, adult applicability and no-invented-assay-boundary issues. Actual standardized source assay/measurement interval, clinical freshness interpretation and production clinical sign-off remain explicit upstream gates, not verified facts. Independent transcription checks catch programming mistakes but cannot certify clinical suitability for a real patient. The candidate must still review the evidence and explain the shipped implementation; this lane does not claim their verification occurred.
