# Group 3 independent clinical/privacy rules audit

Status: independent AI-authored test-design draft. Source review only; no application test has yet been executed by this lane. Root integrates canonical source and records actual executed evidence. This audit does not certify HTTP authorization, transactional auditing or ingestion that belong to later groups.

## Sources checked

- Canonical AGENTS.md and docs/ENGINEERING_AND_SECURITY.md.
- All three extracted Technical Task pages in work/pdf-review/task.txt, particularly R3/R4, house rule 7, all §2.4 rows/bullets and §2.5 cases.
- Adopted immutable packages A v1 and B v1 (their historic proposed headers do not reverse their registered adoption).
- Checklist Tasks 52–62 and current V1__clinical_foundation.sql.

The brief's numerical table is the oracle for its ranges; external laboratory ranges must not replace it. Numerical eGFR values require a separate published independent oracle. Incoming administrative Patient.gender must never silently supply clinical sex.

## Minimum direct-measurement test matrix

For every applicable [lower, upper] interval, test lower minus a small exact decimal, lower exactly, an interior value, upper exactly and upper plus a small exact decimal. Expected flags are LOW, NORMAL, NORMAL, NORMAL, HIGH. Compare unrounded canonical values; formatting must not move an out-of-range value onto the boundary. An absent value must never become NORMAL or numeric zero. NO_APPLICABLE_FLAG must carry a safe reason and remain distinct from NORMAL.

| Measurement | Canonical code/unit | Required applicability and checks |
| --- | --- | --- |
| Systolic BP | 8480-6 / mm[Hg] | [90,120], age18+ only; exactly18 included; age17 no applicable flag. |
| Diastolic BP | 8462-4 / mm[Hg] | [60,80], age18+ only; independent of systolic flag. |
| Height | 8302-2 / cm | Always no reference range/flag, including adult positive measurements. |
| Weight | 29463-7 / kg | Always no reference range/flag, including adult positive measurements. |
| BMI | 39156-5 / kg/m2 | [18.5,24.9] at age18+; under18 may still have computable numeric BMI, with no applicable flag. |
| Haemoglobin child | 718-7 / g/dL | [11,14] for completed ages0–13, regardless of missing/unknown clinical sex. |
| Haemoglobin male | 718-7 / g/dL | [13.5,17.5] for male age14+; test instant immediately before and on UTC birthday. |
| Haemoglobin female | 718-7 / g/dL | [12,15.5] for female age14+; same patient crosses14 in two source observations. |
| Sodium | 2951-2 / mmol/L | [135,145] with no age or sex restriction; valid value still gets range if DOB/sex absent. |
| Potassium | 2947-0 / mmol/L | [3.5,5.1] with no age or sex restriction. |
| Creatinine male | 2160-0 / mg/dL | [0.7,1.3], clinical sex male; no invented age18 gate. Normalize unit before flagging. |
| Creatinine female | 2160-0 / mg/dL | [0.6,1.1], clinical sex female; no invented age18 gate. |
| HbA1c | 4548-4 / % | [4.0,5.6] with no age or sex restriction. |
| eGFR | 98979-8 / mL/s/{1.73_m2} | Derived only under equation applicability; no supplied reference interval, so never label numeric eGFR NORMAL. Display unit remains mL/s/1.73m2. |

BP is one parent 85354-9 Observation with two independently evaluated components, not two unrelated raw observations, not a single parent numeric value/range and not one flag applied indiscriminately to both. Test systolic LOW/diastolic HIGH together, systolic NORMAL/diastolic LOW together and age17/18. Later ingestion must reject missing/duplicate/wrong component codes, unsupported UCUM codes or unsupported parent representations atomically; Group3's rules engine can independently prove two-component calculation behavior.

## Unit/numeric representation checks

1. Accept listed canonical UCUM machine codes under the UCUM system; display text cannot override machine code. Creatinine accepts mg/dL and umol/L (case sensitive) and normalizes umol/L / 88.4 to mg/dL. µmol/L is display text, not the supported machine code.
2. The same patient with 1 mg/dL and 88.4 umol/L produces equal canonical creatinine and eGFR, within documented calculation tolerance. Include nontrivial decimal equivalent pairs and male/female reference-boundary equivalents; test 0.7/61.88 and 1.3/114.92 mg/dL/umol/L pairs.
3. Verify every supported analyte canonical mapping and rejection of code/unit mismatches, unknown analytes, mg/L instead of mg/dL, missing UCUM system and Unicode substitutions. Structural rejection belongs to ingestion but pure normalizer outcomes can be tested now.
4. Reject malformed/non-finite numerics (including NaN and infinities); do not propagate Java arithmetic NaN/Infinity into values or JSON. Very large finite values that overflow derived double arithmetic must return a safe cannot-compute reason rather than a fabricated zero/Infinity.
5. Preserve a finite represented source value when a derived calculation is unsuitable (e.g., zero/negative height, weight or creatinine) and return an explicit derivation-unavailable reason under B4. Do not silently erase the source or fabricate replacement inputs. Distinguish missing from invalid represented numeric values without leaking concealed measurements.
6. Repeated normalization must not convert canonical mg/dL a second time. Record original quantity/unit in provenance where promised; all downstream calculations consume the canonical value.

## Effective-time demographic checks

- Compute completed age from the observation/derivation anchor's UTC calendar date, not request age, current year subtraction or system-default timezone.
- Test offset-bearing timestamps that map to the day before/on the UTC birthday (e.g. a local next-day timestamp can remain the previous UTC date). Same effective instant in two offsets yields identical age/ranges.
- Test birthday14 and birthday18 before/on/after boundaries, age0 and effective date preceding birth. Missing/partial/future-inconsistent DOB yields an unavailable age-dependent result; age-independent Na/K/HbA1c remain interpretable.
- Hb age13 with unknown sex still uses [11,14]; at14 unknown sex yields no applicable sex-dependent range. Unknown DOB cannot select the child/adult Hb branch.
- Creatinine depends on clinical sex only; missing DOB alone must not suppress an otherwise established sex-specific range. eGFR requires both known adult age and applicable sex.
- Effective-dated clinical-sex intervals use [validFrom,validToExclusive); test day before boundary, boundary day and after. Reject overlaps/inconsistency in ingestion; a pure resolver must never pick a convenient one from two conflicting assertions. Gaps/unknown remain unavailable.
- Do not use patient.sex's current value for all history, derive clinical sex from Patient.gender or infer lifetime stability. Observation snapshots must retain interpretation context/provenance and later reads must not change old interpretation because a current demographic field changes.
- Leap-day birthdays should receive an explicit reproducibility test consistent with the selected completed-calendar-age implementation; if the intended clinical anniversary convention is not established by that rule, record/ask before presenting it as a clinical policy.

## BMI temporal/visibility oracle

The pure BMI engine must receive already authorized/released/confidentiality-filtered source records or an explicitly typed visible-source collection. It cannot be used as evidence that SQL/HTTP visibility has been implemented. Its contract must make caller-visible input responsibility obvious and later routes must filter before querying candidate anchors, correction heads or totals.

1. For each visible eligible weight OR height anchor, choose latest eligible visible weight and height at or before that anchor. No future measurement. Formula is weightKg / (heightCm /100)^2. Use a concrete oracle such as 81kg/180cm =25kg/m2; result HIGH for adult and no applicable flag for child.
2. No height ever: explicit HEIGHT_UNAVAILABLE, no fabricated BMI/zero. Hidden-only height must produce byte-equivalent public state/reason and the same anchors, point count, effective times, selected-source metadata and totals as no height at all.
3. Weight unavailable: corresponding safe unavailable reason; hidden-only weight indistinguishable from absence. A visible height event may create an unavailable derived anchor because B4 explicitly permits both event types.
4. Two weights before one height: use latest visible weight at/before the height. Later weight reuses latest visible height. Later height recomputes using latest visible weight. A concealed new height/weight must neither alter an older visible calculation nor create an extra unchanged-looking point/timestamp/count.
5. A future height cannot supply an earlier weight's BMI. No unsupplied freshness cutoff: a genuinely old latest visible height remains eligible, accompanied by selected source dates and a view-scoped interpretation notice. Do not claim it is globally latest or clinically current.
6. Source eligibility is final/amended/corrected only; preliminary, registered, unknown, cancelled and entered-in-error sources cannot silently become eligible inputs even when visible to a reviewing clinician.
7. Equal effective-time competing values must not be resolved using iteration order. If true ambiguity remains after visible correction-head resolution, return an explicit safe reason. If root selects deterministic identity tie-breaking instead, this is not already a clinical policy in B4 and must be checked against its 'ambiguous visible inputs' cannot-compute requirement.
8. Test correction chains: all authorized raw versions remain retrievable; derived input selection uses latest-visible heads. A hidden successor must not suppress or relabel the visible original. A visible child with hidden ancestor must be withheld before derivation so correctionOf cannot leak it. Removing a hidden child entirely must leave the public derived series identical.
9. Correction heads must be selected within the view, and corrections are same patient/analyte/encounter/effective by A3/B2. Never use correction_of alone to join concealed metadata into public source provenance.
10. The derived output must expose only its selected visible IDs/times and inherit their intersection of restrictions; no caller may learn another actor's fuller selected inputs. Clinician and patient/proxy views can legitimately produce different values and must be labelled accordingly.
11. Contradictory selected input context, zero/negative inputs and arithmetic overflow produce explicit unavailable states; reference-range age must be evaluated at the derivation anchor, not request date or arbitrarily whichever input was iterated last.

## eGFR oracle and applicability

- Verify equation coefficients/sex branches, min/max terms, age exponent and female multiplier against published NIDDK 2021 race-free CKD-EPI creatinine equation. Never use a race term or 2009 coefficients. Root's independent numerical lane must supply published NKF example comparisons, not simply a second copy of the same code formula.
- Include male/female examples on both sides of sex-specific kappa (0.9mg/dL male, 0.7mg/dL female), exactly at kappa, age18 and older ages. Expected output is published mL/min/1.73m2 value divided by exactly60, not a relabelled number.
- Test equivalent mg/dL/umol/L source pairs, missing/unknown/conflicting sex, missing/inconsistent DOB, age17, age18, zero/negative/missing creatinine, nonstandardized source, and huge finite source that causes underflow/overflow risk. Do not invent an upper age limit or assay measurement interval.
- Standardized creatinine must be an explicit provenance assertion; code2160-0 plus Quantity is not evidence of IDMS traceability. Unknown standardization yields cannot-compute rather than normal derivation.
- Keep equation/version, canonical input value, original unit/conversion and source ID/effective time in provenance. No normal-range flag is supplied for eGFR in the task.

## V1 integration observations

- V1 stores observation birth_date_context, sex_context and context_provenance plus standardized_creatinine; the pure engine can consume snapshots without current-demographic lookups. Group4 must populate them from explicit validated inputs and not claim the columns alone establish provenance.
- V1 allows value_num null and component JSONB arrays; engine must handle absent numeric values and BP component shape safely. Strict clinical representation validation still belongs to Group4.
- V1 checks PostgreSQL numeric finite values but component numeric finiteness requires application validation too. Do not rely on the parent value_num constraint for BP JSON.
- V1 has a unique successor index and immutable runtime privileges; these support corrections but do not implement chain visibility or source selection by themselves.
- The engine should be pure, deterministic and independent from HTTP/JDBC so tests can exercise boundaries without authorizing reads or bypassing R5. Actual service read paths must later call it only inside authorized transactional actions.

## Genuine outstanding profile questions / scope boundaries

No fundamental numeric range or supported machine-unit ambiguity remains after A/B adoption. Do not reopen those policies. The following can be recorded for later integration rather than stalling pure calculation work:

1. The exact inbound representation policy for a supplied BMI/eGFR Observation (accept as labelled source vs reject as derived-only) is not spelled out in A/B; the brief demands local derivation. Do not silently prefer a source-computed value over the required calculation. Root should document a supported profile and ask if choosing an unsettled policy becomes necessary.
2. Missing-value representations/dataAbsentReason and BP parent storage unit require an explicit input profile in Group4; the brief allows nullable value_num but does not enumerate all FHIR absence forms. Do not assume every normative representation is supported by a declared subset.
3. Leap-day anniversary convention should be documented if it affects test/clinical interpretation; UTC alone does not name a jurisdictional leap-day policy.

For Group3 completion, distinguish tested calculation/normalizer behavior from later proof that ingestion invokes normalization and GET routes use identical visibility/auditing. Tests of a pure engine do not complete Groups4/5/10.
