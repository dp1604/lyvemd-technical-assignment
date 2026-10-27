# Review of Attachment A — PR #418

**Request changes; do not merge.** The review identifies 24 blockers, three preferences and five follow-up checks. The main risks are confidential results reaching the wrong reader, clinical values being misinterpreted, loss of correction history and reads succeeding without a durable audit. Two separate type errors also contradict the claim that this exact listing builds.

All findings originated with AI assistance. The eight supplied files—724 numbered lines—were inspected against the task and the primary sources linked below. The attachment was not changed, compiled, built or run. Its examples describe possible consequences, not observed patient events. As instructed, unshown helpers are treated as defect-free. The author's 14 passing tests, 88% coverage and staging performance remain unverified because the tests and measurements were not supplied.

The [written walkthrough](PR_REVIEW_WALKTHROUGH.md) explains each blocker through an example. The candidate requested the review and discussed key findings; that does not make AI-origin findings human discoveries or establish a completed line-by-line verification. Actual discussion, source checks and review history are in the [Task 81 record](docs/evidence/task-81-review.md).

## Files reviewed

| File | Printed lines | Main findings |
|---|---:|---|
| ResultController.java | 110 | Contract, authorization, denials, auditing and pagination — B02–B06, B17, B19, B22, B24 |
| ResultService.java | 157 | Proxy policy, visibility, totals, ordering and read cost — B01–B03, B05, B14, B17, B21, B24 |
| ReferenceRangeResolver.java | 101 | Age/sex rules, historical context, flags and model types — B11–B13, B20–B21 |
| EgfrCalculator.java | 77 | Equation, units, applicability, types and qualified input — B07–B11, B18, B20, B23 |
| UnitNormalizer.java | 64 | Coded units, optional fields and integration — B07, B21 |
| ResultMapper.java | 77 | Values, components, flags, derivations and corrections — B07, B09–B11, B13–B15, B21, B23 |
| ResultRepository.java | 84 | Queries, identity, retention, ordering and read cost — B02–B03, B15–B17, B22, B24 |
| AccessLogWriter.java | 54 | Transaction boundaries, denials and failures — B04–B06 |
| **Total** | **724** | All eight files |

## Merge blockers

### B01 — Proxy confidentiality depends on a fixed list of encounter IDs

**Location:** `ResultService.java` 27–33, 57–72, 80–82, 106–107, 116–122 and 142–143 (pp3–5). **Requirement:** house rules 1, 3, 4; R2.

`isVisibleToProxy` excludes eight IDs instead of checking encounter class and patient age. A new MHEV, ABSL or SUBU encounter for a teenager passes this filter. The list can also hide records where the age/class rule does not apply. Proxy membership has no explicit age-18 cutoff here; the unshown Actor helper might independently restrict adult links, so that part needs confirmation.

Use the authoritative individual proxy link, an explicit age basis and the actual encounter class. Apply the same visibility rule before list, detail, trend, count and derived-input selection. Verify all three protected classes and ages 11/12/17/18. This finding does not assume that Actor trusts client-provided proxy lists or mishandles revocation.

### B02 — Patient reads do not check clinician release or critical handling

**Location:** `ResultController.java` 99–101; `ResultService.java` 74–109 and 125–149; `ResultRepository.java` 25–40 (pp2–5,10). **Requirement:** house rule 2.

Self-access passes immediately. The queries then retrieve observations without a release condition, and the service only filters proxy encounter IDs. A newly recorded result, including a critical one, can reach the patient before the required review.

Keep new and corrected results held until an attributable clinician release, with the critical workflow completed where required. Enforce that condition on every patient-facing read. Lab `final` status is not a substitute for release. The missing read gate must be repaired; notification transport is outside this PR's scope.

### B03 — The total reveals observations removed from the page

**Location:** `ResultController.java` 51–58; `ResultService.java` 80–98; `ResultRepository.java` 30–34 (pp2,4,10). **Requirement:** house rule 5; R2.

The list filters observations for proxies, but `countResults` counts every observation for the patient. Two visible rows with a total of three disclose a concealed result. The separate list/count transactions can also observe different states.

Count and page the same authorized, filtered collection in one consistent snapshot and stable order. Add hidden-versus-absent count and pagination cases.

### B04 — The denial path creates and logs patient-identifying information

**Location:** `ResultController.java` 104–108 and 56; `AccessLogWriter.java` 44–45 (pp2–3,12). **Requirement:** house rule 5; privacy/minimization.

After rejecting access, the controller looks up the patient's display name and embeds it in an exception and warning log. Whether the exception reason reaches the caller depends on the unshown renderer; the identifying lookup and log already occur. Routine logs also contain patient IDs/counts and arbitrary audit-failure messages.

Return one generic unavailable response for inaccessible and nonexistent resources, without resolving a name. Keep necessary attribution in the controlled audit store. Operational diagnostics should use safe categories/correlation IDs, without names, raw payloads or exception text that may contain clinical data.

### B05 — A clinical response can succeed without its audit

**Location:** `ResultService.java` 74, 95, 100, 125; `ResultController.java` 51–54, 70–74, 89–91; `AccessLogWriter.java` 27–45 (pp2,4–5,11–12). **Requirement:** R5; house rule 7.

The service's transaction boundaries do not include the controller's later `record` call. With async enabled, the audit runs on another thread; the writer also catches and suppresses persistence failures. Disabling async would still leave the missing shared transaction and swallowed failure.

Put authorization, content reads and synchronous audit insertion in one read-write service transaction. Materialize the response and commit before sending it. If audit insertion or commit fails, withhold the content. A warning log does not satisfy `access_log`. Spring documents the [thread-bound transaction boundary](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/annotation/Transactional.html) and [async execution](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Async.html).

### B06 — Denied attempts never reach the audit writer

**Location:** `ResultController.java` 48–58, 67–75, 86–92 and 99–108; `AccessLogWriter.java` 49–52 (pp2–3,12). **Requirement:** R5.

Every controller audit call follows a successful service return. Authorization failures and empty detail results throw before it. `recordDenied` has no caller, so those attempts leave no required denied row.

Represent an expected denial as an outcome whose audit commits before the generic error is returned. Avoid throwing inside that transaction in a way that rolls back the denial event. Check unauthorized access, concealed details and audit-write failure separately.

### B07 — Conversion uses display text, and the eGFR path bypasses it

**Location:** `UnitNormalizer.java` 24–54 and 61–62; `ResultMapper.java` 34–50 and 73–74; `EgfrCalculator.java` 38 and 70–75 (pp7–10). **Requirement:** R3; house rule 7.

The normalizer checks substrings in `Quantity.unit` rather than coded UCUM identity. A quantity coded `umol/L` but displayed as `µmol/L`, or without optional display text, is not converted. `isCanonical` also dereferences an absent unit and treats haemoglobin in `g/L` as canonical despite the conversion in `normalise`.

More importantly, the mapper passes the feed value straight into the calculator; none of these files calls `normalise`. An SI creatinine value can therefore be interpreted as mg/dL. Validate analyte and `Quantity.system`/`code`, normalize before persistence/calculation and retain provenance: 88.4 umol/L must become 1 mg/dL regardless of display spelling. FHIR distinguishes [display units from computable codes](https://hl7.org/fhir/R4/datatypes-definitions.html#Quantity.code). No caller of `isCanonical` or separate ingestion implementation is supplied.

### B08 — The eGFR formula mixes versions and includes a race multiplier

**Location:** `EgfrCalculator.java` 13–14, 44–53 and 62–67 (p7). **Requirement:** R3.

The α values match the newer equation, but the coefficient 141, exponent −1.209, age base 0.993, female factor 1.018 and race factor 1.159 do not. The required 2021 values are 142, −1.200, 0.9938 and 1.012, without a race term. This is a hybrid, rather than a faithful implementation of either the required equation or its cited 2009 source.

These differences change kidney estimates and any trend built from them. Implement the published race-free 2021 expression, record its version/source and check both creatinine branches and clinical-sex inputs against independent reference cases. [NIDDK adult equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults).

### B09 — The per-minute result is not converted to the advertised per-second unit

**Location:** `EgfrCalculator.java` 20 and 44–55; `ResultMapper.java` 48–65 (pp7,9). **Requirement:** R3; §2.4.

The formula produces a per-minute estimate; no division by 60 occurs. `EGFR_UNIT` says `mL/s/1.73m2`, while the mapper returns a derived number without that unit. Consuming it as the advertised per-second quantity creates a sixtyfold scale error, separate from B08. The unused constant does not establish what an unshown client actually displays.

Divide the correct per-minute result by 60 and return its explicit unit, chosen LOINC and provenance. Check that conversion independently. [NIDDK units and conversion](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults).

### B10 — The calculator accepts unsuitable inputs and hides failure reasons

**Location:** `EgfrCalculator.java` 29–42 and 47–55; `ResultMapper.java` 48–50 (pp7,9). **Requirement:** R3; house rule 7; §2.4.

Presence is checked, but positive creatinine and adult eligibility are not. Every present non-female gender is treated as male. Zero/negative values enter fractional negative powers; missing inputs become an unexplained `null`. This can produce an inapplicable estimate or leave the reader unable to understand its absence.

Validate suitable positive standardized creatinine, adult applicability and explicit clinical context. Return a typed cannot-compute result with a reason when those conditions fail. The [published adult equation applies at 18+](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults); do not invent defaults for missing age or sex.

### B11 — Historical results are interpreted using today's demographics

**Location:** `ReferenceRangeResolver.java` 64 and 75–99; `EgfrCalculator.java` 34–42; `ResultMapper.java` 29 (pp6–7,9). **Requirement:** house rule 7; R4.

Ranges use `ageNow`; eGFR passes today's date to the age helper. Missing birth date becomes age 99. Both use current Patient gender without effective-time clinical-sex provenance. A haemoglobin measured at 13 can acquire a different range after the patient's 14th birthday, while unknown age can acquire an adult interpretation.

Resolve age and applicable clinical sex at measurement time, retain the context/version and explain unavailable interpretation when necessary. The age helper is not the defect: it receives the wrong temporal input. [Patient.gender is administrative](https://hl7.org/fhir/R4/patient-definitions.html#Patient.gender), not proof of historical clinical sex.

### B12 — One age-14 switch applies to every analyte

**Location:** `ReferenceRangeResolver.java` 38–50 and 66–85 (pp5–6). **Requirement:** R4; §2.4.

Below 14, the map contains only haemoglobin; at 14+, every analyte uses the adult map. A 15-year-old therefore receives BP/BMI ranges that start at 18, while a 10-year-old sodium, potassium or HbA1c result has no range. Sex-specific creatinine is also incorrectly suppressed below 14.

Use each table row's own applicability conditions. Verify 13/14 and 17/18 boundaries, plus rows with no specified age cutoff. Otherwise abnormalities can go unflagged and adolescents can receive inapplicable flags. The supplied numeric intervals are not a general adult/paediatric rule.

### B13 — A missing range is returned as NORMAL

**Location:** `ResultMapper.java` 37–46 (p9). **Requirement:** R4, R6; house rule 7.

`flag` starts as NORMAL and changes only when a range exists. Height, weight and other results without an applicable interval can therefore imply normality without any valid comparison, preventing the client from showing the required distinction.

Return a separate no-applicable state and reason where appropriate. Use NORMAL only after comparison with an applicable range, and test those states separately.

### B14 — The mapper assumes every result has one scalar value

**Location:** `ResultMapper.java` 31–45 and 69–75; `ResultService.java` 135–145 (pp5,9–10). **Requirement:** R4; nullable value in §2.3; BP rule in §2.4.

Both mappings dereference a top-level numeric Quantity. The required BP Observation has two components and need not have a parent scalar, so a supported record can fail the page or lose its component abnormalities. Trend selection also checks only the parent coding and cannot select a systolic/diastolic component by its code.

Handle scalar, component and absent-value results explicitly. Range-check each BP component independently and project its trend from the authorized parent. Do not manufacture a parent number. [FHIR Observation components](https://hl7.org/fhir/R4/observation-definitions.html#Observation.component).

### B15 — Correction writes delete the original

**Location:** `ResultRepository.java` 57–66 and 79–82; `ResultMapper.java` 63–65 (pp9–11). **Requirement:** R1; detail contract; §2.4.

`upsert` deletes rows matching patient/code/effective before insertion. A correction sharing that tuple destroys its original, and the broad match can remove another same-time observation too. The mapper separately hardcodes `correctionOf` to null. Clinicians lose both the retained record and its explicit correction relationship.

Append an immutable version linked to one validated predecessor, retain both and return that link. Neither `upsert` nor `insert` is called by these GET paths; the problem is in the supplied persistence method, not a read-side deletion.

### B16 — Dynamic search interpolates values into SQL

**Location:** `ResultRepository.java` 44–54 (p10). **Requirement:** safe access to protected clinical records.

`String.format` puts patientId/code inside SQL quotes and sortDir into ORDER BY. Untrusted values become SQL syntax and can change the patient boundary or create expensive/erroring work.

Bind patient/code as data, choose direction from a server-owned allowlist and validate/bound limit. Apply the same authorization and visibility rules as other reads. The controller does not call `search`, and its ignored `sort` argument does not flow here; external reachability is unproven.

### B17 — A small page still loads and decodes the entire history

**Location:** `ResultController.java` 43–45; `ResultService.java` 78–89 and 129–148; `ResultRepository.java` 25–27 (pp2,4–5,10). **Requirement:** scale and reliable clinical access.

Every page/trend fetches all observations for the patient before filtering, sorting and slicing. The SQL does not use trend code/time bounds. Offset/limit have no bounds, and negative values reach Stream operations that reject them. A small requested page therefore does not bound database, heap or CPU work.

Validate parameters early and move appropriate visibility, code/time, ordering and paging into parameterized indexed queries. Keep counts consistent and profile long histories before claiming a response budget. The work-growth problem is evident; no latency failure or capacity limit was measured.

### B18 — The declared return type cannot accept the result of round

**Location:** `EgfrCalculator.java` 27 and 55 (p7). **Requirement:** build correctness; the author's test claim.

`Math.round(double)` returns `long`. `Optional.of` boxes that as Long, which cannot satisfy `Optional<Double>`; Java does not insert a long-to-Double widening-then-boxing conversion. The expression is not type-correct.

Use a return type and precision policy that agree, round only at the display boundary, and request the author's actual verified revision. This follows from [Math.round](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#round(double)), [Optional.of](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html#of(T)) and [Java conversion rules](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.2).

### B19 — The routes do not match the required URLs

**Location:** `ResultController.java` 23 and 41/62/79 (pp1–2). **Requirement:** §2.3 endpoint contract.

The class adds `/api/v1` to `/patients/{patientId}`, while the contract requires `/patients/...`. Clients using the contract cannot reach these methods without a compatibility mapping that has not been supplied.

Expose the required paths, or resolve an explicit contract change and verify the mapping. A deployment rewrite cannot be assumed. Other absent Part 2 features are not automatically defects in this partial results PR.

### B20 — Both clinical classes name the wrong R4 gender enum

**Location:** `ReferenceRangeResolver.java` 98–99; `EgfrCalculator.java` 39–40 (pp6–7). **Requirement:** HAPI FHIR R4; build correctness.

The code names `org.hl7.fhir.r4.model.AdministrativeGender.FEMALE`. R4's type is `org.hl7.fhir.r4.model.Enumerations.AdministrativeGender`, also returned by `Patient.getGender()`. These references are another build blocker, separate from B18.

Use the actual model type and verify the exact reviewed revision. This fixes type resolution, not the historical/clinical-sex errors. The [Patient API](https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-structures-r4/org/hl7/fhir/r4/model/Patient.html) and [nested enum API](https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-structures-r4/org/hl7/fhir/r4/model/Enumerations.AdministrativeGender.html) confirm it. Read-only archive inspection of official `org.hl7.fhir.r4:6.9.12` found the nested class and no top-level class; that reference library is not a claimed junior dependency version. No classes were loaded.

### B21 — Clinical meaning changes with coding order

**Location:** `ReferenceRangeResolver.java` 59; `ResultMapper.java` 31–32 and 49–50; `ResultService.java` 135–137; `UnitNormalizer.java` 29 and 36–45; `ResultRepository.java` 64 and 74 (pp5–6,8–11). **Requirement:** R3–R4; house rules 7–8.

Every path takes the first coding's bare code without checking its system. With local `HB` first and LOINC `718-7` second, haemoglobin loses its range, B13 calls it normal and the LOINC trend skips it. Reordering equivalent codings changes the result. Conversely, a local code named `2160-0` can select creatinine logic without establishing that meaning.

Resolve one supported, unambiguous `(system, code)` identity consistently across interpretation and storage. Test permutations/system collisions and prove any narrower internal profile. No such profile is supplied. FHIR [assigns no meaning to coding order](https://hl7.org/fhir/R4/datatypes.html#CodeableConcept) and defines [Coding.system](https://hl7.org/fhir/R4/datatypes-definitions.html#Coding.system); an unseen ingestion defect is not being presumed.

### B22 — Writes store a reference while reads use a patient key

**Location:** `ResultRepository.java` 25–40 and 69–76, especially 73; `ResultController.java` 42/51, 63/70 and 80/89 (pp2,10–11). **Requirement:** patient-key and endpoint contracts.

`insert` stores `obs.getSubject().getReference()` directly as patient_id. Readers instead bind the route's patientId. For `Patient/p1`, the write value differs from `/patients/p1/results`'s `p1`. If the column accepts that string, retrieval misses the result; if its type/constraints reject it, insertion fails. DDL is unprovided, so the precise symptom is unknown.

Resolve supported typed references to the authoritative local key, validate locality/existence, and use that key consistently. Retain the original reference separately; do not fetch arbitrary remote URLs. Verify a persistence-to-read case using a [normal relative FHIR reference](https://hl7.org/fhir/R4/references.html#literal). The GETs do not call this writer, so no unseen ingestion failure is assumed.

### B23 — A qualified value is turned into an exact measurement

**Location:** `ResultMapper.java` 34–45, 53–65 and 69–75; `EgfrCalculator.java` 29–38 and 47–55 (pp7,9–10). **Requirement:** house rule 7; §2.4.

The mapper copies the number/unit and the calculator treats the number as exact. Neither reads `Quantity.comparator`. If admitted, creatinine `<1 mg/dL` becomes exact 1 and can produce a fabricated estimate; task-potassium `>5.1 mmol/L` can become the normal endpoint 5.1. The result is more certain, or clinically different, than the supplied measurement.

Preserve and deliberately interpret supported qualifiers, or reject/mark the input unavailable before exact calculation. A profile excluding qualifiers must be demonstrated. FHIR defines [comparator as a modifier](https://hl7.org/fhir/R4/datatypes-definitions.html#Quantity.comparator) whose [meaning must be honored](https://hl7.org/fhir/R4/datatypes.html#Quantity). This finding is conditional on such input reaching the mapper; no unseen validator is declared defective.

### B24 — Equal-time results can move between pages

**Location:** `ResultRepository.java` 24–27; `ResultService.java` 80–88; `ResultController.java` 42–58 (pp2,4,10). **Requirement:** correct pagination under §2.3.

SQL returns rows without ORDER BY. Java sorts only by effective time, then applies skip/limit. Ties retain the database's unspecified order. If A and B share a timestamp, fetches ordered A,B then B,A can make page 1/limit 1 and page 2/offset 1 return A twice and omit B, even without a logical record change.

Define a total order—effective time plus immutable ID—and use it consistently before paging. Test tied rows at boundaries. This is separate from optional custom sort, and a tie-breaker alone does not freeze independent pages against concurrent inserts. [PostgreSQL ordering](https://www.postgresql.org/docs/16/queries-order.html), [pagination guidance](https://www.postgresql.org/docs/16/queries-limit.html) and [Java's stable sort](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#sorted(java.util.Comparator)) explain why preserving an input tie order does not make it repeatable.

## Preferences

- **P01 — Remove unused wiring and correct the comments.** Service lines 35–53 inject ranges, calculator and FhirContext without using them. Controller lines 19–20 promise authorization before clinical reads, yet denial looks up a name. Simplify the dependencies and make the comments describe the repaired path; unused fields alone are not a patient-safety defect.
- **P02 — Remove or deliberately support optional sort.** Controller line 45 accepts sort, but Service 75–82 always uses descending effective time. It is not a required task parameter. Keep a small documented allowlist if useful, otherwise remove the option. B24 addresses mandatory stable paging; B16's unrelated SQL method is not reached by this argument.
- **P03 — Agree display precision.** Calculator lines 25/55 promise whole-number rounding. Preserve calculation precision and have the clinical owner agree display rounding after equation/unit fixes. The brief supplies no additional tolerance, assay precision or formatting rule.

## Follow-up checks

- **V01 — Investigate hidden/missing timing.** Service 102–107 checks visibility only when an observation exists. Both paths return empty to the same controller 404, which is a useful body/status pattern. It proves neither timing equivalence nor a practical timing attack. Verify realistic noisy cases through a common authorized query/error path and document residual limits.
- **V02 — Put revocation and reads under a declared snapshot.** Controller 48–51, 67–70 and 86–89 authorizes before the service transactions at 74–75, 100–102 and 125–129. A revocation between those operations may be missed. Define the boundary, bring policy/content/audit together and verify concurrency. An already-authorized response cannot be promised retractable; no race was executed here.
- **V03 — Specify accepted effective-time and request forms.** Service 131–139/152–155, Mapper 62/72 and Repository 65/75 assume populated effectiveDateTime. The fallback checks a typed getter that can create an empty child or reject another type. Establish allowed effective[x]/precision/date-window inputs and validate them; an edit timestamp is not clinical measurement time. No admitted profile is supplied, so a supported-input failure is not established. Controller from/to are required as written; the brief does not promise open windows. DateUtil/AgeUtil remain defect-free. [FHIR effective time](https://hl7.org/fhir/R4/observation-definitions.html#Observation.effective_x_), [R4 accessors](https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-structures-r4/org/hl7/fhir/r4/model/Observation.html#getEffectiveDateTimeType()).
- **V04 — Verify the write transaction.** Repository 61–66 deletes then inserts without a transaction annotation or supplied caller. Without an outer transaction, insert failure can leave the deletion committed; an outer transaction could prevent that additional failure. Identify the owner and verify rollback/concurrency. B15 still applies even if replacement is atomic.
- **V05 — Define status eligibility and derived provenance.** Mapper 34–50/62–65 calculates before copying status; trend mapping 69–75 omits status. If entered-in-error/ineligible sources are admitted, distinguish retained history from a current estimate. [Observation.status is a modifier](https://hl7.org/fhir/R4/observation-definitions.html#Observation.status). Supply the accepted-status/amendment-trend policy and where BMI/eGFR links, versions and unavailable reasons originate. No particular allowlist, latest-head convention or unseen implementation defect is assumed.

## Scope and verification

The review covers this partial results PR, not the entire Part 2 application. It assigns no extra defects to Actor, DateUtil, AgeUtil, FhirCodec or unshown repositories/DTOs, and does not demand production authentication, notifications or other excluded features. Where a finding depends on input admission, external callers, schema or transaction configuration, the qualification remains with it.

Checks consisted of reading the listing, tracing all three success/denial paths, comparing the supplied table and clinical equation, and consulting FHIR, Spring and Java sources. Relevant printed pages were visually checked and official-library entry names inspected without loading classes. Exact source checks, provenance and discussion are retained in the [Task 81 evidence](docs/evidence/task-81-review.md). No payload attack, timing/load result or successful build is claimed. The focused joint walkthrough is recorded separately; it does not establish independent human verification of every finding.

## Conflict in the supplied terminology

The task labels `2947-0` as potassium; official LOINC defines sodium in blood. This is a task conflict, not another junior defect for following the table. The candidate adopted retaining it only for synthetic assessment with prominent disclosure. Real clinical use requires verified specimen/mapping and clinical-owner approval; no replacement code or specimen is inferred. [D-17 decision](docs/decisions/group-6-loinc-clarification.md), [official LOINC definition](https://loinc.org/2947-0).
