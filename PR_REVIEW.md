# Static review — Attachment A, PR #418

Prepared with AI assistance and checked by static inspection against the supplied listing and primary sources. Recommendation: **request changes; do not merge**. The joint candidate walkthrough (Task81) has not been performed; this review does not claim candidate verification or approval.

This is a static review of the eight supplied files, using their printed line numbers. The listing was not fixed, compiled, built, run or tested. Codex read all 724 numbered lines and traced the three shown GET paths into the supplied service, mapper, repository and log writer. Unshown helpers are accepted as defect-free as the attachment instructs. The author's fourteen passing tests, 88% coverage and staging performance remain unverified claims: their source, assertions and measurements were not supplied.

All findings below originated with AI assistance. Earlier AI candidates were checked again against the actual printed listing; additional findings came from that second AI review. No candidate-origin discovery or independent human verification is claimed. The candidate walkthrough and any candidate amendments will be recorded separately without changing discovery origin.

## Merge blockers

### B01 — Proxy confidentiality uses fixed encounter IDs; the shown policy omits the age18 cutoff

**Location:** `ResultService.java` 27–33, 57–72, 80–82, 106–107, 116–122 and 142–143 (Attachment pp3–5). **Requirement:** house rules1,3,4 and R2.

`isVisibleToProxy` excludes eight encounter IDs; it never reads the encounter class or the patient's age. Proxy eligibility checks membership in `actor.proxyPatientIds()` but the shown policy never applies the age18 cutoff. A newly created MHEV, ABSL or SUBU encounter for a teenager is visible unless its ID happens to be in the list. No shown age check rejects an adult proxy who remains in that list; whether an unshown helper independently filters adult links is not established. The same ID list can also over-hide records where the stated age/class rule does not apply. **Remedy:** use the authoritative active proxy link, explicit age basis and actual encounter class in a shared visibility predicate applied before list, detail, trend, count and derived-input selection. Test ages11/12/17/18 and all three classes. Do not infer that the unseen Actor helper trusts a client-provided proxy list or mishandles revocation; that is not established here.

### B02 — Patient reads have no clinician-release or critical-path gate

**Location:** `ResultController.java` 99–101; `ResultService.java` 74–109 and125–149; `ResultRepository.java`25–40 (pp2–5,10). **Requirement:** house rule2.

Self access passes immediately; retrieval selects observations without a release condition, and the service filters only proxy encounter IDs. No shown read checks a separate clinician release or critical-path completion. A patient can therefore receive an otherwise eligible newly recorded result before the required review, including a critical result. **Remedy:** default new/corrected results to held, require an attributable clinician release action and the critical workflow before patient visibility, and enforce that predicate on every patient-facing read. A lab `final` status must not stand in for a clinician release. This finding concerns the shown read gate, not an invented claim that a notification service belongs in this PR.

### B03 — Pagination total discloses concealed observations

**Location:** `ResultController.java`51–58; `ResultService.java`80–98; `ResultRepository.java`30–34 (pp2,4,10). **Requirement:** house rule5 and R2.

The page filters some observations for proxies, while `countResults` counts every observation for the patient without the caller or visibility predicate. A proxy seeing two items and total three learns that another result exists. Separate page/count transactions can also observe different database states. **Remedy:** compute both from the same authorized, visibility-filtered snapshot and stable ordering; the total must describe the items this caller can see. Include hidden-versus-absent totals and pagination tests.

### B04 — Denial exposes the patient's name and creates unnecessary operational PHI

**Location:** `ResultController.java`104–108; also56; `AccessLogWriter.java`44–45 (pp2–3,12). **Requirement:** house rule5; privacy/minimization.

After deciding the caller is unauthorized, the controller retrieves a display name and puts it into the exception reason and warning log. The exception carries patient-identifying information; whether a particular global error renderer exposes that reason is not shown, but creating the patient-specific denial is already the wrong boundary. It also adds an identifiable lookup to the denial path. Application logs additionally contain patient identifiers and, on logging failure, arbitrary exception text. **Consequence:** an unauthorized caller may learn identity/existence through the error response, while routine diagnostic logs unnecessarily spread patient-linked information. **Remedy:** use one generic unavailable response for inaccessible and nonexistent resources; do not resolve a name on denial. Put necessary attributable access events in the controlled audit store and use PHI-free diagnostic categories/correlation IDs, without raw exception/payload text.

### B05 — Clinical content and its audit are not one atomic transaction

**Location:** `ResultService.java`74,95,100,125; `ResultController.java`51–54,70–74,89–91; `AccessLogWriter.java`27–45 (pp2,4–5,11–12). **Requirement:** R5 and house rule7.

The service's read-only transaction ends when it returns. The controller then schedules `record`, whose exception handler deliberately swallows persistence failure. With async enabled, the audit runs later on another thread; even without interception, the shown code still provides no common read/audit transaction and still suppresses failure. A response can be delivered without the required durable access row. **Remedy:** place authorization, content reads and synchronous audit insertion in one read-write service transaction; materialize the response and complete the commit before sending content. Audit/commit failure must withhold content. A normal diagnostic log is not the required `access_log`. Spring documents thread-bound transactions and asynchronous execution separately; see [transaction semantics](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/annotation/Transactional.html) and [Async](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Async.html).

### B06 — Denied clinical attempts are never sent to the audit writer

**Location:** `ResultController.java`67–75 and99–108; `AccessLogWriter.java`49–52 (pp2–3,12). **Requirement:** R5.

All three controller audit calls are after successful service returns. Authorization rejection and the detail path's empty result throw before those calls; `recordDenied` has no caller in the listing. An unauthorized probe or a proxy requesting a concealed result leaves no required denied row. **Remedy:** model an expected denial as an outcome whose audit can commit, then return the generic denial; avoid throwing inside a transaction in a way that rolls back the denied event. Verify authorization denials, concealed detail probes and audit-write failures separately.

### B07 — Unit conversion follows display text and the shown eGFR path bypasses it

**Location:** `UnitNormalizer.java`24–54 and61–62; `ResultMapper.java`34–50 and73–74; `EgfrCalculator.java`38 and70–75 (pp7–10). **Requirement:** R3 and house rule7.

The normalizer reads `Quantity.unit`, lowercases it and searches substrings, rather than matching the coded UCUM unit. A valid creatinine Quantity with `code=umol/L` and human display `µmol/L`, or without optional display text, is not converted. `isCanonical`61–62 also dereferences the optional display without a null check and calls haemoglobin in `g/L` canonical even though `normalise`45–50 would convert it; no caller of `isCanonical` is shown, so these are helper defects rather than an observed GET failure. The mapper's `calculateFromFeed` route passes the numeric feed value directly to the calculator; none of the eight files calls `normalise`. Thus no supplied path establishes canonical numeric input before range checking or eGFR calculation, and an SI value can be treated as mg/dL. **Remedy:** validate the analyte plus `Quantity.system`/`code`, normalize once before persistence/calculation, retain input provenance and reject unsupported/ambiguous units. For example, 88.4umol/L must normalize to1mg/dL independently of display spelling. FHIR distinguishes the [human-readable unit from the computable code](https://hl7.org/fhir/R4/datatypes-definitions.html#Quantity.code). This is not a claim that an unseen ingest service is defective; the supplied conversion and read/calculation paths do not provide the required guarantee.

### B08 — eGFR is a hybrid with a race multiplier, not the required 2021 equation

**Location:** `EgfrCalculator.java`13–14,44–53 and62–67 (p7). **Requirement:** R3.

The alpha values match the newer equation, but coefficient141, upper exponent−1.209, age base0.993, female factor1.018 and race factor1.159 do not. The required 2021 expression uses142, upper exponent−1.200, age base0.9938, female factor1.012 and no race term. This is a hybrid, not simply a faithful implementation of the cited2009 equation. **Consequence:** kidney estimates and trends differ by incorrect factors, including race. **Remedy:** implement the published race-free2021 equation with version/source provenance and independent reference cases covering both creatinine branches and clinical-sex inputs; remove race from calculation. Checked against [NIDDK's adult equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults).

### B09 — Per-minute calculation is labelled as per-second output

**Location:** `EgfrCalculator.java`20 and44–55; `ResultMapper.java`48–65 (pp7,9). **Requirement:** R3 and §2.4.

The equation computes a per-minute quantity but no conversion to seconds occurs. `EGFR_UNIT` claims `mL/s/1.73m2`; that constant is not referenced by the mapper, which returns an unaccompanied derived number. If consumed as the claimed per-second quantity, the scale error is a factor of60, apart from the equation defects. **Remedy:** convert the correct per-minute value by dividing by60, return its explicit unit and chosen LOINC/provenance, and test the conversion independently. NIDDK identifies the [equation's units and SI conversion](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults). Do not claim the unused constant itself proves how an unshown client labels the response.

### B10 — eGFR applicability and failure reasons are missing

**Location:** `EgfrCalculator.java`29–42 and47–55; `ResultMapper.java`48–50 (pp7,9). **Requirement:** R3, house rule7 and §2.4 derived-input rule.

The calculator accepts zero/negative creatinine and any present administrative gender, treating every non-female value as male; it does not restrict the adult equation to adults. Zero/negative inputs enter fractional negative powers rather than producing a declared invalid-input result. Missing inputs become an undifferentiated `null` in the DTO, without the required cannot-compute reason. **Consequence:** an inapplicable or nonsensical estimate can be presented as a number, or a patient/clinician cannot tell why it is absent. **Remedy:** validate positive suitable standardized creatinine, explicit applicable clinical context and adult eligibility; return a typed unavailable result with a safe reason instead of fabricated estimates. The [NIDDK adult applicability statement](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults) supports the18+ boundary. No claim is made that specific bad inputs were executed.

### B11 — Historical interpretation uses today's age, current demographics and an invented age99

**Location:** `ReferenceRangeResolver.java`64 and75–99; `EgfrCalculator.java`34–42; `ResultMapper.java`29 (pp6–7,9). **Requirement:** house rule7 and R4.

Ranges call `ageNow` and the equation passes today's date to the otherwise defect-free age helper. The resolver substitutes99 when birth date is absent. Both use the current Patient gender without effective-time clinical-sex provenance. A haemoglobin taken at13 can change interpretation after the patient's14th birthday; unknown age can falsely acquire an adult range. Current administrative gender is also not proof of historical clinical sex: [FHIR defines it as administrative](https://hl7.org/fhir/R4/patient-definitions.html#Patient.gender). **Remedy:** resolve completed age and the relevant clinical-sex assertion at observation effective time, preserve provenance/versioning, and return an explicit unavailable interpretation when a required historical input is unknown. Do not invent age or silently treat unknown/other gender as male.

### B12 — A universal age14 switch applies the wrong table rows

**Location:** `ReferenceRangeResolver.java`38–50 and66–85 (pp5–6). **Requirement:** R4 and §2.4.

All analytes below14 use a map containing only haemoglobin, while all14+ use the adult map. Consequently a15-year-old receives adult BP/BMI ranges although those start at18; a10-year-old sodium/potassium/HbA1c result has no range even though those rows specify no age restriction. Creatinine's sex-specific row is also incorrectly suppressed below14. **Consequence:** abnormal values can lose their flag, and adolescent values can receive an inapplicable flag. **Remedy:** encode each supplied analyte's own applicability criteria; test exact boundaries13/14 and17/18 plus analytes with no specified age cutoff. The table, rather than an imported general adult/paediatric convention, governs this exercise.

### B13 — No applicable range is falsely called NORMAL

**Location:** `ResultMapper.java`37–46 (p9). **Requirement:** R4, R6 and house rule7.

The flag starts as `NORMAL` and changes only when a range exists. Height, weight and other legitimate no-range results therefore assert clinical normality without an applicable comparison. **Consequence:** patients and clinicians receive false reassurance; the client cannot distinguish the required no-applicable state. **Remedy:** return an explicit `NO_APPLICABLE`/equivalent state and reason when appropriate; use normal only after comparison with an applicable range. Test no-range and insufficient-context results independently from normal.

### B14 — Scalar-only mapping cannot represent the required component BP or absent values

**Location:** `ResultMapper.java`31–45 and69–75; `ResultService.java`135–145 (pp5,9–10). **Requirement:** R4, §2.3 nullable value and §2.4 BP rule.

Both mappings dereference a top-level numeric Quantity unconditionally. The required blood-pressure Observation contains two components, whose values/ranges must be handled individually; it need not have a parent numeric value. The mapper therefore cannot produce the required BP result and can dereference a missing numeric value. Trend filtering also looks only at the parent's first coding, so a component analyte query is not resolved into its component value. **Consequence:** a supported BP record can fail the results page or fail to expose a systolic/diastolic abnormality; missing measurements become errors rather than an explicit unavailable state. **Remedy:** model scalar, component and absent-value results explicitly, check each BP component against its own code/range and build component trends deliberately. [FHIR describes Observation components separately](https://hl7.org/fhir/R4/observation-definitions.html#Observation.component). Do not describe the exact exception response or a measured outage without execution evidence.

### B15 — Amendments delete originals and the response discards their link

**Location:** `ResultRepository.java`57–66 and79–82; `ResultMapper.java`63–65 (pp9–11). **Requirement:** R1, detail contract and §2.4 amendment rule.

`upsert` deletes rows sharing patient/code/effective time before insertion. A correction normally preserves that clinical context, so this method destroys the previous result instead of retaining both. The mapper independently hardcodes `correctionOf` to null even if the resource carries a correction link. **Consequence:** clinicians lose the original record and cannot distinguish a correction from an unrelated result. **Remedy:** append a new immutable observation and explicit validated predecessor link; preserve both retrievable versions and map that link into the DTO. `upsert` is not called by these three GET paths; the destructive behavior is a defect in the supplied persistence method, not an observed GET side effect.

### B16 — Dynamic search interpolates untrusted SQL values and sort syntax

**Location:** `ResultRepository.java`44–54 (p10). **Requirement:** protected clinical records; safe database access.

`String.format` embeds patientId and code inside SQL quotes and inserts sortDir directly into ORDER BY. When called with untrusted values, they become SQL syntax rather than bound data; a patient boundary can be changed. **Consequence:** a caller of this search method may expose other patients' clinical rows or trigger expensive/erroring database work. **Remedy:** bind patient/code values, select direction from a server-owned enum/allowlist, and validate/bound the limit. Check authorization and filtered counts with the same policy as other clinical paths. The shown controller never calls `search`; its `sort` argument does not flow into this SQL method, and no exploit or external reachability was tested.

### B17 — Read cost is unbounded by the requested page or trend window

**Location:** `ResultController.java`43–45; `ResultService.java`78–89 and129–148; `ResultRepository.java`25–27 (pp2,4–5,10). **Requirement:** the production-scale architecture/read behavior; reliable patient access.

Every page and trend first loads and decodes all observations for that patient; visibility, ordering, range filtering and pagination occur in memory. `offset`/`limit` have no shown bounds; negative values reach Stream skip/limit and extreme values do not bound database work. The query ignores the requested trend code/time range. **Consequence:** a long record or repeated requests can consume large heap/CPU and delay results for other users; a small requested page does not protect availability. **Remedy:** validate bounded parameters, push caller visibility plus code/time/order/page into parameterized indexed SQL, keep page/count consistent, and profile representative long histories before claiming a budget. This establishes the work-growth mechanism, not a measured latency failure or a numerical capacity limit.

### B18 — Declared Optional<Double> cannot return the boxed long from round

**Location:** `EgfrCalculator.java`27 and55 (p7). **Requirement:** deliverable correctness; the author's build/test claim.

`Math.round(double)` returns primitive long. `Optional.of` boxes the supplied value as Long; it cannot satisfy the declared `Optional<Double>` return type. Assignment context does not provide a long-to-Double widening-then-boxing conversion. **Consequence:** the listed source is not type-correct, so it cannot provide these features as written. **Remedy:** use a return type/precision policy consistent with the actual numeric result and round only at the agreed display boundary, then have the author provide the actual successful checks. Verified from [Math.round](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#round(double)), [Optional.of](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html#of(T)) and [Java conversion rules](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.2), not by compiling the supplied code.

### B19 — The shown routes add an undocumented prefix to the required paths

**Location:** `ResultController.java`23 and41/62/79 (pp1–2). **Requirement:** §2.3 endpoint contract.

The class maps `/api/v1/patients/{patientId}`, producing `/api/v1/patients/...`; the task requires `/patients/...`. No supplied rewrite or compatibility mapping establishes the required URLs. **Consequence:** a client using the documented contract cannot reach these methods as shown. **Remedy:** expose the specified paths, or explicitly resolve the contract change with the owner and provide a verified mapping. This is a shown mapping mismatch; no claim is made about unseen deployment routing. Missing ingestion/access-log/client implementation is not automatically assigned as a defect in this explicitly partial results PR.

## Nonblocking preferences and follow-up checks

- **P01 — Remove unused wiring and misleading comments.** `ResultService.java`35–53 holds/injects ranges, egfrCalculator and FhirContext without using them; `ResultController.java`19–20 claims authorization before clinical reads while its denial branch reads a name. Clean up after the policy fixes so the dependency graph and comments match behavior. The resulting clarity helps reviewers find real enforcement; unused fields alone do not prove a patient safety defect.
- **P02 — Either remove or implement the advertised sort option.** Controller45 passes sort, but `ResultService.java`75–82 always sorts by descending effective time. Sort is not a mandatory task parameter. Choose a small documented allowlist only if needed; add an ID tie-breaker for deterministic pagination. Do not connect this ignored argument to the unrelated repository SQL injection.
- **P03 — Agree display precision separately from calculation precision.** `EgfrCalculator.java`25/55 promises whole-number rounding. After fixing equation and unit conversion, retain internal precision and have the clinical owner approve display precision. No extra numeric tolerance or clinical threshold was supplied, so this is a design follow-up rather than an invented grading rule.
- **V01 — Timing equivalence needs evidence.** `ResultService.java`102–107 does a visibility check only for existing observations. Both hidden and missing details return empty to the same controller404, which is a positive body/status pattern; the work paths differ. Static inspection does not establish a measurable timing oracle or prove equivalence. Require hidden/missing timing tests under realistic noisy conditions after a fix, use a common authorization-aware query/error path, and state residual limits honestly.
- **V02 — Authorization/read consistency needs concurrency tests.** `ResultController.java`48–51,67–70,86–89 authorizes before the service read transactions at `ResultService.java`74–75,100–102,125–129. A proxy revocation between the checks and the content read could be missed. Bring policy/content/audit into one declared snapshot and test the documented revocation boundary; this is a concurrency follow-up, not an executed race or a defect assigned to an unshown helper. Do not promise that already-authorized in-flight responses can be retracted.

## Scope and verification record

The first AI candidates were retained with corrections: the equation is described as hybrid; the details path's hidden/missing body/status similarity is acknowledged; the unused repository search is not attributed to GET endpoints; no defects are assigned to unshown Actor, AgeUtil, DateUtil, codec or repository helpers. The review does not demand real authentication, notification delivery or the entire Part2 implementation from a PR explicitly covering part of the feature. Header input validation cannot be pronounced broken without inspecting Actor, which the attachment stipulates is defect-free.

AI checks actually completed: read all supplied numbered lines; trace each shown GET success/denial path; compare flags/range boundaries to the supplied table; compare the eGFR constants/applicability/units against NIDDK; check Quantity/component/demographic semantics against FHIR R4; check async/transaction semantics against Spring6.2 documentation and return-type semantics against Java21 documentation. No executed tests, payload attacks, timing measurements, load results or successful builds of Attachment A are claimed. The candidate walkthrough has not been performed and remains incomplete.


## Supplied terminology conflict

The task itself assigns potassium code `2947-0`, although official LOINC identifies it as sodium in blood. The candidate adopted retaining this convention only for the synthetic assessment, with verified specimen/mapping and clinical-owner approval required before real clinical use. This is a specification conflict, not an additional PR defect assigned to the junior for following the supplied table. No silent replacement code or specimen is inferred. See the [D-17 source conflict and actual adoption](docs/decisions/group-6-loinc-clarification.md) and [official LOINC 2947-0 definition](https://loinc.org/2947-0).
