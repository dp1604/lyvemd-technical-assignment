# Group 3 independent numeric implementation review

27 October 2026. Read-only review of the current canonical Java implementation against the primary-source oracle in `oracle-research.md` and `oracle-values.json`. No application execution, canonical edits or Git changes were performed by this reviewer. The integrator owns the separate automated comparison run.

## Findings

**No concrete coefficient, scaling, range-boundary or applicability defect identified in the reviewed implementation.**

| Reviewed area | Evidence and conclusion |
|---|---|
| `ClinicalCalculations.egfr` | Coefficient 142; female/male kappa 0.7/0.9; alpha -0.241/-0.302; upper exponent -1.200; age factor 0.9938; female factor 1.012 match NIDDK and NKF. No race multiplier or old -1.209 exponent is present. |
| Canonical input | eGFR receives a canonical creatinine value; `UnitNormalizer` divides declared `umol/L` by decimal 88.4 using DECIMAL128. `Quantity.unit` is presentation only. Both supported representations converge on `mg/dL`. |
| Output conversion | Full-precision `StrictMath` per-minute result is converted by decimal 60. No premature whole-number rounding and no approximate 0.0167 multiplier. Machine/display units and LOINC match the adopted package. |
| Formula applicability | Requires eligible source status, present positive creatinine, effective-time age 18+, known clinical sex and explicit standardization. Missing context and numeric nonrepresentation return a null value with a reason; no default age/sex/value, fabricated assay cutoff or upper-age restriction. |
| Precision | Double calculation matches the published equation's computation needs; finite and positive checks surround the formula. BigDecimal conversion preserves the calculated decimal through `/60`. Recommended independent oracle comparison tolerances remain appropriate. Application tests against all 24 JSON cases are still the integrator's evidence, not claimed here. |
| BMI arithmetic | Centimetres scale to metres by power -2, then `kg / m²` with DECIMAL128. Null/nonpositive/ambiguous inputs cannot create a numeric BMI. Two different known birth dates return inconsistent context. Missing birth-date context permits the numerical BMI but prevents an invented age-dependent range. |
| Ranges | All task-table bounds match exactly: SBP 90–120, DBP 60–80, adult BMI 18.5–24.9; Hb ages below 14 11–14, male 14+ 13.5–17.5, female 14+ 12–15.5; Na 135–145, K 3.5–5.1; creatinine male 0.7–1.3/female 0.6–1.1; HbA1c 4.0–5.6. Comparison is exact decimal `< low`/`> high`, making endpoints inclusive without display-rounding tolerance. |
| No-range interpretation | Height/weight/eGFR have no invented interval; BP parent is a component result; under-18 BP/BMI have no applicable interval. Missing values retain distinct unavailable interpretation. Na/K/HbA1c and sex-dependent creatinine do not acquire an unsupported age restriction. |
| Effective-time age | UTC observation date plus `Period.between(...).getYears()` provides completed ISO years. Future birth date is inconsistent; null context is unavailable. For a 29 February birth, this advances on 1 March in a non-leap year, matching the candidate's subsequent adoption. |
| Storage | Foundation migration uses unconstrained `numeric` rather than a fixed scale for `value_num`, so no schema rounding of values close to clinical boundaries was seen. |

## Scope and integration obligations

The clinical classes deliberately do not constitute authorization or transactional-audit enforcement. The visible-series constructor expects the service's stored-rights predicate; HTTP paths and ingestion persistence are later groups. The final integration must snapshot effective historical demographics, use the real caller visibility predicate before selecting derived inputs or event anchors, and retain the same arithmetic/provenance. The candidate's newly adopted derived-only inbound BMI/eGFR rule must be enforced by the ingestion mapper; the numerical engine itself can operate on internal derived analytes.

The visible-series and BMI code was also inspected as context: anchors are created only from the filtered latest eligible input heads, so hidden input rows do not independently add a point. Do not interpret this static observation as complete privacy-path verification; the root's absence-versus-concealment tests and later endpoint tests are necessary.

The oracle confirms faithful programming of the selected equation, not real assay traceability, individual clinical appropriateness or clinical sign-off. Actual source assay measuring intervals and clinical-owner decisions remain explicit production gates.
