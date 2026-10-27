# Requirement-to-evidence matrix

Status: baseline created in Task 8. This maps the supplied obligations to planned delivery and checks; it does not certify the unbuilt application. Maintain row IDs and update actual evidence through Task 90, final disclosure/history through Task 91, and delivery through Tasks 93-96.

## Sources, status and use

Primary sources: **T** = supplied Technical Task (three pages); **A** = Attachment A (twelve pages); **U** = candidate's actual chat directions. [Source hashes and boundaries](SOURCE_RECORD.md) identify the original inputs. Original PDFs, extracted listings and full correspondence stay local. Dana's fictional email is material to analyse; it does not override house rules or authorize disclosure.

**VERIFIED** = the linked bounded implementation/check passed; explicit remaining integrations and final acceptance still apply. **PENDING** = required work/check has no completion evidence yet. **PARTIAL** = cited preparation exists but the row's final obligation remains open. **RECORDED** = this bounded documentation obligation is recorded; later changes/final reconciliation still apply. **OPEN** = a decision is deliberately unresolved, not an implementation choice already approved. **RECORDED** on a Q row means the candidate selected an explicit assessment policy, not that software or an employer clarification was verified. An outline, directory, test name, forecast or environment probe cannot close an application row.

Delivery aliases below identify planned areas, not assertions that modules or evidence already exist:

- **N1/N2**: sections 1/2 of the [Specification and Architecture Note](../SPECIFICATION_AND_ARCHITECTURE.md), currently an outline.
- **APP/DB/UI**: application code, migrations and browser client under the [project structure](PROJECT_STRUCTURE.md); [Group2 foundation](evidence/group-2-foundation.md) exists; POST/detail are verified through [Group4](evidence/group-4-ingestion.md); remaining routes/workflow/UI are pending.
- **SCOPE**: [build scope boundaries](SCOPE_BOUNDARIES.md); initial inventory checked in Task 9, continuing/final scope inspection pending.
- **DATA/TEST**: [Group3 clinical tests/oracle](evidence/group-3-clinical.md) and foundation tests exist; POST/detail integration is verified through [Group4](evidence/group-4-ingestion.md); deterministic generator and remaining HTTP/data/client integration remain pending.
- **RUN**: [README](../README.md); Group2 build/start verified locally, dataset command and final clean-run pending.
- **PR**: [static review](../PR_REVIEW.md), currently pending. **LOG**: [AI working log](../AI_WORKING_LOG.md), maintained incrementally.
- **ENV**: [verified environment probe](evidence/environment.md); **HIST**: [history process](HISTORY.md) and snapshot-specific [Task 5 evidence](evidence/task-5-history.md).
- **DELEG/PROMPT**: [R1-R6 delegation](ai/DELEGATION.md) and [exact adopted instruction/provenance](ai/ACCESS_RULE_INSTRUCTION.md).

“Tasks” refers to the candidate's numbered working checklist. Planned verification is our method unless the source itself explicitly requires it. Replace a pending status only with a linked, actually executed result or finalized document section, naming who checked it, conditions and limits. Preserve unmet requirements and decisions rather than hiding them in a pass summary. New endpoints, migrations, clinical logic and material changes must identify affected matrix IDs.

## Part 1: fictional context and Dana's section

| ID | Source | Obligation / source fact | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| CTX-01 | T p1 §1.2 | Fictional 11-clinic group, 175,000 patients; candidate is first engineering hire | N1/N2 context; 18-19 | Note uses supplied constraints and distinguishes LyveMD facts | PENDING |
| CTX-02 | T p1 §1.2 | Careline dates from 2003, remains five more years; Atlas does not exist | N1/N2 integration/build plan; 19,24,32 | Coexistence plan does not invent an existing portal or replacement authorization | PENDING |
| CTX-03 | T p1 §1.1 | Dana seeks something approvable by Friday | N1 product plan; 20-25 | Treat Friday as fictional approval timing, distinct from candidate submission deadline | PENDING |
| P1-01 | T p1 §1.3 | Produce one note with separate Dana and engineering sections; ENTIRE Dana section uses language she can approve without a technical question | N1/N2; 18,20-25,90 | Both finalized sections consistent with build; review whole Dana section for plain-language approvability | PENDING |
| P1-02 | T p1 §1.3 | One-sentence goal in language Dana can approve without a technical question | N1 goal; 20 | Plain-language review of the actual sentence | PENDING |
| P1-03 | T p1 §1.3 | Release 1 inclusions, each with a one-line reason | N1 scope; 21 | Every inclusion has reason and acceptance coverage | PENDING |
| P1-04 | T p1 §1.1/1.3 | Release 1 exclusions, each with a one-line reason; address iOS/Android expansion | N1 scope; 21 | Explicit mobile recommendation and reasons; native apps not silently added | PENDING |
| P1-05 | T p1 §1.1/1.3 | Resolve same-day lab signoff versus clinician release and non-silent critical handling; raise with Dana | N1 conflict resolution; 16,22 | Explicit decision/request for clinical release ownership; HR-02 alignment | PENDING |
| P1-06 | T p1 §1.1/1.3 | Reject shared patient passwords; propose individual, revocable, attributable proxies | N1 conflict resolution; 22,37 | Dana-facing resolution agrees with HR-03/04 | PENDING |
| P1-07 | T p1 §1.1/1.3 | Address insurer's monthly identifiable abnormal-result list under legal-basis AND consent rule | N1 conflict resolution; 22,31 | No unauthorized identifiable export; insurer work excluded from build | PENDING |
| P1-08 | T p1 §1.1/1.3 | Resolve assumed FHIR upstream format before designing around it | N1/N2 verification gate; 22,32 | No claim that generated examples verify Careline or Northgate | PENDING |
| P1-09 | T p1 §1.1/1.3 | Recommend build versus vendor; stated price forty thousand/year and one-month claim | N1 recommendation; 23 | Reasoned recommendation and diligence questions; no invented currency, capabilities or deck content | PENDING |
| P1-10 | T p1 §1.3 | Build order with rough estimates and first cut | N1 plan; 24 | Ordered fictional product increments and estimates, distinct from 12-hour exercise | PENDING |
| P1-11 | T p1 §1.3 | Close Dana section with testable acceptance criteria | N1 ending; 25 | Concrete expected outcomes covering scope and mandatory house-rule conflicts | PENDING |

## Part 1: engineering section

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| P2-01 | T p1 §1.3 | Engineering decisions and their reasons, including storage/indexing at roughly 9 million observations / 175,000 patients / +3 million observations annually | N2 storage and rationale throughout section; DB; 26-30,34 | Reasons accompany engineering decisions, not just storage; schema/query/index rationale uses all scale figures | PENDING |
| P2-02 | T p1 §1.3 | What breaks first at ten times that scale and how detected | N2 scaling; 27 | Explicit forecast, measurements/alerts to detect it; no claim 90-million-row test was run | PENDING |
| P2-03 | T p1 §1.3 | Where all house rules are enforced and how auditor sees no alternative path to protected records | N2 enforcement map; APP/DB/TEST; 28,39,83-85 | Entry-point/read-path inventory, controls and adversarial evidence; honest timing limits | PENDING |
| P2-04 | T p1 §1.3 | Unit normalization and eGFR design | N2 clinical; APP/DB; 29,52,60 | Source-to-canonical-to-derived unit trace, including per-minute/per-second distinction | PENDING |
| P2-05 | T p1 §1.3 | Who owns equation correctness | N2 governance; 29 | Accountable role and clinical review responsibility; no invented actual sign-off | PENDING |
| P2-06 | T p1 §1.3 | How a newly published equation is adopted | N2 governance; 29 | Version/change/validation policy and treatment of existing results | PENDING |
| P2-07 | T p1 §1.3 | Equation test oracle | N2 / TEST; 29,62 | Independent, cited expected values and method, not only self-comparison | PENDING |
| P2-08 | T p1 §1.3 | Behavior on invalid clinical input | N2 / APP / TEST; 29,61-62 | Explicit invalid/missing/inconsistent-input cases with no fabricated derivation | PENDING |
| P2-09 | T p1 §1.3 | One operations paragraph: proof of restore | N2 operations; 30,87 | Restore-proof procedure; executed small drill identified as our supporting choice | PENDING |
| P2-10 | T p1 §1.3 | First three alerts | N2 operations; 30 | Three specific proposed alerts and why they matter | PENDING |
| P2-11 | T p1 §1.3 | Behavior when dependency unavailable | N2 / APP / TEST; 30,41,84 | Stated failure behavior and actual failure checks where implemented | PENDING |

## R1-R6 build requirements

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| R1-01 | T p1 R1 | POST /fhir accepts FHIR R4 Bundle type transaction | APP / TEST / RUN; 11,33,42,47,82 | Valid transaction accepted; non-Bundle/wrong type/invalid inputs checked | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R1-02 | T p1 R1 | Validate every entry; reject entire Bundle if any invalid | APP / DB / TEST; 11,42,44,82 | Invalid entry at varied positions, full rollback and unchanged state | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R1-03 | T p1 R1 | Name invalid entry in transaction-response | APP / N2; 12,44,47,82 | Response identifies offending entry; declared standard discrepancy resolved first | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R1-04 | T p1 R1 | Idempotency under X-Idempotency-Key | APP / DB / TEST / DATA; 13,45,73,82 | Actual duplicate submission; persisted effects, replay/conflict/concurrency under decided policy | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R1-05 | T p1 R1 | Resolve urn:uuid: references | APP / TEST; 11,43,82 | Bundle-local/forward references resolve; malformed/unresolved relationships rejected by explicit policy | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R1-06 | T p1 R1 | Amended result appends via correction_of and retains original | APP / DB / TEST / DATA; 13,46,70,82 | No destructive overwrite; original and correction linked and retrievable | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| R2-01 | T p1 R2 | House rules 1,3,4,5 on EVERY read, server-side | APP / DB / TEST; 35-39,48-51,83,85 | Actor/route matrix covers list, detail, trend, log policy, totals, pagination and alternate routes | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| R3-01 | T p1 R3 | Canonical creatinine unit; UCUM-governed conversion at ingest | APP / DB / TEST; 52,58,60,62 | Stored canonical values for both units; no dependence on display labels | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| R3-02 | T p1 R3 | Race-free 2021 CKD-EPI creatinine eGFR from converted value and published source | APP / TEST / N2 / RUN; 29,60-62 | Verified formula/applicability, independent oracle, unit conversion, invalid-input checks | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| R3-03 | T p1 R3; p3 Submission 2 | Equation source and chosen eGFR LOINC with verified source in run instructions | RUN; 60,88 | Exact implemented equation/version, code, source links and verification; chosen98979-8 now implemented/source-checked; final clean-run instructions still required | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| R4-01 | T p1 R4 | Ranges and flags follow every §2.4 row | APP / TEST / UI; 53-62,85 | CL-01..CL-14 table and boundary cases; effective-time demographics | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| R5-01 | T p2 R5 | Each clinical-content read inserts access_log in SAME transaction | APP / DB / TEST; 17,40-41,48-50,84 | Demonstrate read/audit transaction coupling and no clinical delivery after audit failure | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| R5-02 | T p2 R5 | Denied reads recorded | APP / DB / TEST; 17,40-41,83-84 | Actual denied event persists under documented denial/rollback policy | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| R6-01 | T p2 R6 | Minimal client results list includes range and flag | UI / TEST; 63,86 | Live list verified against API and clinical scenarios | PENDING |
| R6-02 | T p2 R6 | Trend rendered as chart | UI / TEST; 64,86 | Actual ordered API points shown in chart with accessible values | PENDING |
| R6-03 | T p2 R6 | Access log rendered | UI / TEST; 64,86 | Real permitted log request/page shown; errors/empty cases useful | PENDING |
| R6-04 | T p2 R6 | No-applicable-flag distinct from normal; flags not color-only | UI / TEST; 54,63,65,86 | Compare normal/high/low/no-applicable states with textual or other non-color cues | PENDING |
| R6-05 | T p2 R6 | Every screen keyboard-reachable | UI / TEST; 65,86 | Guided keyboard walkthrough of screens/controls/focus; actual checker attributed | PENDING |

## House rules: all eight mandatory in the design

R2's explicit build enumeration does not waive the other design obligations. Workflow representation/thresholds and transaction wording still need recorded decisions; do not invent notification or release endpoints here.

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| HR-01 | T p2 §2.2.1 | Only self, registered proxy, or clinician on one of patient's encounters; STAFF access log only | N2 / APP / DB / TEST; 14,28,35-36,51,83 | Stored entitlement checks; unrelated actors/STAFF clinical reads denied; log rights explicitly decided | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-02 | T p2 §2.2.2 | Patient visibility requires clinician release; critical path never silently releases | N1/N2 / APP / TEST; 16,22,28 | State exact demo boundary and required production workflow; unreleased/critical cases under approved policy | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-03 | T p2 §2.2.3 | Individual, revocable, logged proxy links; no shared credentials; read attributable to one person | N1/N2 / APP / DB / TEST; 22,28,37,40,83 | Registered/revoked proxy scenarios and attributable audit; no shared-password design | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-04 | T p2 §2.2.4 | At age 12+ hide ABSL/SUBU/MHEV encounters AND their observations from proxy; proxy ends at 18 | N2 / APP / TEST / DATA; 15,28,38,68,83 | All 3 classes, below/at 12 and below/at 18, chosen confidentiality time basis | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-05 | T p2 §2.2.5 | Protected omission indistinguishable from nonexistent item in status, body AND timing | N2 / APP / TEST; 28,39,83,85 | Consistent routes/counts/pagination; hidden/missing status/body comparisons and honest timing investigation; no universal-proof claim from a few timings | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-06 | T p2 §2.2.6 | Identifiable outbound data requires recorded legal basis AND patient consent; absent both, only de-identified aggregates | N1/N2 / scope / TEST; 22,28,31 | Outbound data-flow/control design; no insurer export or unsupported jurisdiction/compliance claim | PENDING |
| HR-07 | T p2 §2.2.7 | Canonical UCUM per analyte; effective-time age AND sex ranges; no flag where inapplicable; no fabricated derived values; retain amendment originals; every read in own audit transaction | N2 / APP / DB / TEST; 15,17,28-29,40,46,52-62,84-85 | Trace all six sub-obligations to CL/R1/R3/R5 checks; explain same/own transaction interpretation | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| HR-08 | T p2 §2.2.8 | Verify upstream formats before designing around them; assume nothing | N1/N2 integration gate; 22,28,32 | Actual sample/contract-verification plan; explicitly no real samples supplied | PENDING |

## Supplied data model and test-caller contract

Preserve all 27 listed columns and their meanings. “pk”, “fk” and “nullable” below reproduce the annotations actually printed in T §2.3. The source explicitly marks only two FKs and four nullable observation columns; unspecified types/nullability must not be relabelled as source-mandated constraints. Additional constraints/columns/indexes require an implementation rationale and must preserve meanings.

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| DM-01 | T p2 §2.3 | patient: id (pk), sex, birth_date | DB / APP / TEST; 15,34 | Migration/schema inspection and demographic policy cases | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| DM-02 | T p2 §2.3 | encounter: id (pk), patient_id (fk), class, practitioner_id | DB / APP / TEST; 34,36,38 | Migration plus patient/clinician/protected-class relationship checks | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| DM-03 | T p2 §2.3 | proxy_link: patient_id, proxy_id (pk pair) | DB / APP / TEST; 34,37 | Composite-key semantics; revocation remains possible and logged | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| DM-04 | T p2 §2.3 | observation: id (pk), patient_id (fk), encounter_id (nullable), code, unit_code, value_num (nullable), effective, status, correction_of (nullable), component (jsonb, nullable) | DB / APP / TEST; 13,15-16,34,42-46,52-62 | All 10 meanings and 4 explicit nullable fields retained; corrections/components/effective-time checks | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| DM-05 | T p2 §2.3 | access_log: id (pk), actor_id, actor_role, patient_id, action, outcome, source, at | DB / APP / TEST; 17,34,40,51,84 | All 8 fields populated with actual attribution/action/outcome/source/time | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| CT-01 | T p2 §2.3 | X-Test-Actor-Id and X-Test-Actor-Role, roles PATIENT/PROXY/CLINICIAN/STAFF, untrusted input | N2 / APP / TEST; 14,35,83 | Missing/malformed/unsupported values and authoritative stored rights checked; document impersonation limitation | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |
| CT-02 | T p2 §2.3 | May add columns/indexes but cannot change listed meanings | N2 / DB / TEST; 10,34,90 | Source-to-schema review and reasons for additions, no unsupported source constraint claim | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| ST-01 | T p2 §2.3 | Java 21+ and Spring Boot 3.x | APP / RUN; 3,33,88-89 | ENV confirms probe; own build/runtime and clean instructions still required | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| ST-02 | T p2 §2.3 | HAPI FHIR R4, FHIR 4.0.1 | APP / TEST / RUN; 3,33,42,88-89 | ENV confirms probe; final dependencies/parser/validation checked on own build | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |
| ST-03 | T p2 §2.3 | PostgreSQL 16 | DB / RUN; 3,33-34,88-89 | ENV confirms isolated 16.15; final app/schema and clean database checks pending | PARTIAL — [Group2 schema/runtime foundation](evidence/group-2-foundation.md); feature/final checks pending |

## Every required endpoint

The four GET contracts need a documented, explicit role policy; being a named route is not sufficient evidence of access/audit correctness. EP-05 is the log-only STAFF path, not permission for STAFF to read clinical results.

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| EP-01 | T p2 §2.3 | POST /fhir -> Bundle(transaction-response) | APP / TEST; 42-47,73-74,82 | R1 checks and decided Q-02 response behavior, including invalid-entry response | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| EP-02 | T p2 §2.3 | GET /patients/{id}/results?offset=&limit= -> page plus total | APP / TEST / UI; 39-40,48,63,83-86 | Correct bounded paging and visible-only total; authorization/filtering/audit, empty/denied cases | PENDING |
| EP-03 | T p2 §2.3 | GET /patients/{id}/results/{obsId} -> value, unit, range, flag, correctionOf | APP / TEST; 39-40,46,49,83-85 | Exact required fields, patient ownership, original/correction retrieval, hidden/missing behavior and audit | VERIFIED — [Group4 bounded ingestion/detail and154-case evidence](evidence/group-4-ingestion.md); final dataset/cross-route/regression reconciliation remains |
| EP-04 | T p2 §2.3 | GET /patients/{id}/trend?code=&from=&to= -> points ordered by effective | APP / TEST / UI; 39-40,50,64,83-86 | Analyte and time-bound filtering, deterministic effective order, visibility and audit | PENDING |
| EP-05 | T p2 §2.3 | GET /patients/{id}/access-log?offset=&limit= -> access-log page | APP / TEST / UI; 14,51,64,83-84,86 | Validated paging, approved role entitlement, no clinical-results leakage | PENDING — Q-04 |

## Complete clinical table and prose obligations

Ranges below are source values, not invented critical thresholds. Age and sex for reference ranges are at the observation's effective time. Boundary inclusivity, missing/unsuitable demographics, historical sex representation and derivation time selection require explicit policy; this matrix does not decide them.

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| CL-01 | T p3 §2.4 row 1 | Systolic BP: 8480-6; mm[Hg]; 90-120 at age 18+ | APP / TEST / UI; 53,62,85 | Own component range/flag; adult and under-18 cases, numeric boundaries | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-02 | T p3 §2.4 row 2 | Diastolic BP: 8462-4; mm[Hg]; 60-80 at age 18+ | APP / TEST / UI; 53,62,85 | Own component range/flag, independent of systolic | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-03 | T p3 §2.4 row 3 | Height: 8302-2; cm; no range | APP / TEST / UI; 54,62-63,85 | No applicable flag is distinct from normal; canonical unit | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-04 | T p3 §2.4 row 4 | Body weight: 29463-7; kg; no range | APP / TEST / UI; 54,62-63,85 | No applicable flag; missing-height derivation case does not invent BMI | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-05 | T p3 §2.4 row 5 | BMI: 39156-5; kg/m2; 18.5-24.9 at age 18+ | APP / TEST / UI; 15,55,61-62,85 | Most recent suitable weight/height under declared policy; adult/underage/missing-input cases | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-06 | T p3 §2.4 row 6 | Haemoglobin: 718-7; g/dL; ages 0-13: 11-14; male 14+: 13.5-17.5; female 14+: 12-15.5 | APP / TEST / DATA; 15,56,62,69,85 | Same patient either side of 14; both source sex branches; effective-time age/sex and invalid demographics | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-07 | T p3 §2.4 row 7 | Sodium: 2951-2; mmol/L; 135-145 | APP / TEST; 57,62,85 | Below/within/above range; no invented age restriction | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-08 | T p3 §2.4 row 8 | Potassium: 2947-0; mmol/L; 3.5-5.1 | APP / TEST; 57,62,85 | Below/within/above range; no invented age restriction | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-09 | T p3 §2.4 row 9 | Creatinine: 2160-0; canonical mg/dL; male 0.7-1.3; female 0.6-1.1 | APP / DB / TEST / DATA; 52,58,62,67,85 | Both source units normalize before range/eGFR; both sex branches and missing demographics | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-10 | T p3 §2.4 row 10 | HbA1c: 4548-4; %; 4.0-5.6 | APP / TEST; 59,62,85 | Exact LOINC/UCUM and range/boundary flags | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-11 | T p3 §2.4 row 11 | eGFR: choose LOINC; mL/s/1.73m2; derived | APP / TEST / RUN; 29,60-62,88 | Published race-free 2021 equation, chosen verified code, actual minute-to-second conversion, no invented range | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-12 | T p3 §2.4 bullet 1 | BP is ONE Observation with two component entries, each checked by own code; Northgate creatinine in micromol/L uses same 2160-0 as mg/dL | APP / DB / TEST / DATA; 52-53,62,67,85 | Component representation; UCUM code authority despite display text; no invented upstream wire-format claim | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-13 | T p3 §2.4 bullet 2 | BMI uses most recent weight/height and cannot compute without height; eGFR derives from serum creatinine; missing/inconsistent inputs return cannot-compute WITH reason | N2 / APP / TEST / UI; 15,55,60-62,71,85 | Missing-height, inconsistent/missing inputs and equation applicability with explicit reason; never fabricated number | PARTIAL — [Group3 clinical engine](evidence/group-3-clinical.md) and [Group4 ingest/source-detail evidence](evidence/group-4-ingestion.md); derived read/UI/cross-route/final integration pending |
| CL-14 | T p3 §2.4 bullet 3 | Amended result retains original; marked as correction; BOTH retrievable | APP / DB / TEST / UI; 13,46,49,70,82 | Original and correction queryable, correctionOf exposed and no silent overwrite | PARTIAL — [Group4 ingestion/detail controls and154-case evidence](evidence/group-4-ingestion.md); remaining routes/workflow/dataset/UI and wider timing/final checks pending |

## Deterministic dataset and performance

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| DS-00 | T p3 §2.5 | Generate yourself deterministically by script, not manual entry; size exercises house rules | DATA / RUN; 66,88-89 | Reproducible seed/time inputs and generated counts/scenarios, clean-load procedure | PENDING |
| DS-01 | T p3 §2.5 case 1 | Same patient has creatinine in BOTH units | DATA / TEST; 67,85 | Generated scenario IDs, actual ingest and canonical equivalence checks | PENDING |
| DS-02 | T p3 §2.5 case 2 | Parent-and-teen proxy pairs; teen has protected encounter | DATA / TEST; 68,83 | Pair/encounter/observation identities and actual protected read checks | PENDING |
| DS-03 | T p3 §2.5 case 3 | Same patient's haemoglobin on BOTH sides of age 14 | DATA / TEST; 69,85 | Effective dates/birth date prove boundary; respective ranges checked | PENDING |
| DS-04 | T p3 §2.5 case 4 | Amended observations with original and amended versions | DATA / TEST; 70,82 | Linked retained rows and both API retrievals | PENDING |
| DS-05 | T p3 §2.5 case 5 | Patient with weight and NO height EVER recorded | DATA / TEST; 71,85 | Dataset-wide absence verified and explicit BMI cannot-compute reason | PENDING |
| DS-06 | T p3 §2.5 case 6 | Multi-year series for at least one analyte | DATA / TEST / UI; 72,85-86 | Actual date span and ordered bounded trend/chart | PENDING |
| DS-07 | T p3 §2.5 case 7 | Bundle actually submitted twice | DATA / TEST; 73,82 | Two requests and stable persisted effects, not duplicate-looking direct SQL inserts | PENDING |
| DS-08 | T p3 §2.5 case 8 | Bundle actually containing one invalid entry | DATA / TEST; 74,82 | Invalid entry named; entire transaction rejected with no partial writes | PENDING |
| PF-01 | T p3 §2.5 | State actual size used and reproduction | DATA / RUN / evidence; 75,88-89 | Exact resulting entity/observation counts, seed/time and reproduction command | PENDING |
| PF-02 | T p3 §2.5 | State read-endpoint response-time target and measured dataset size | RUN / evidence; 75-76 | Chosen numerical target (none supplied), actual measurements per applicable GET endpoint and dataset counts | PENDING |
| PF-03 | U process/checklist | Reproducible performance evidence, no confusion with production-scale forecast | TEST / evidence / N2; 76,90 | Measurement conditions, request count/concurrency/latency and relevant query evidence; limitation stated | PENDING |

## Static review: scope and every supplied file

A's author's test/coverage/staging-performance statements are claims in review material, not our executed evidence. Do not expand defects into unseen helpers, which A explicitly stipulates are defect-free. Review file-local numbered lines, not PDF line numbers; a file can continue across pages.

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| PR-01 | T p3 Part 3.1 | Each finding identifies file and precise location | PR; 77-79 | Printed location verified against actual listing | PENDING |
| PR-02 | T p3 Part 3.2 | For each: defect, patient/clinician consequence, proposed remedy | PR; 78-81 | Supported code path/concrete case and consequence; no invented execution | PENDING |
| PR-03 | T p3 Part 3.3 | Separate blockers from preferences | PR; 78-81 | Severity justified against requirements/patient consequences | PENDING |
| PR-04 | T p3 Part 3.4; A p1 | Do NOT fix, build or run Attachment A; no source repository to check out | PR / process; 77-81 | Static-only method and truthful claims; tests run only on own separate build | PARTIAL — AGENTS boundary recorded; final review pending |
| PR-05 | T p3 Part 3.4 | State candidate versus AI origin and how each finding was checked | PR / LOG; 79-81,91 | True origin per finding and verification method; earlier AI candidates stay AI-origin | PENDING |
| PR-06 | A p12 listing boundary | Eight files/724 printed lines; unshown helpers stipulated defect-free; no inference from claimed tests | PR; 77,79 | Scope/method statement and no unsupported hidden-helper/timing findings | PENDING |
| A-01 | A §A.1 pp1-3, lines 1-110 | ResultController.java | PR; 77-81 | Static read of complete file, numbered findings or recorded no supported finding | PENDING |
| A-02 | A §A.2 pp3-5, lines 1-157 | ResultService.java | PR; 77-81 | Same complete-file static review and cross-file trace | PENDING |
| A-03 | A §A.3 pp5-6, lines 1-101 | ReferenceRangeResolver.java | PR; 77-81 | Compare shown range/demographic paths with whole clinical table | PENDING |
| A-04 | A §A.4 pp6-7, lines 1-77 | EgfrCalculator.java | PR; 77-81 | Compare shown formula/inputs/units with verified primary sources | PENDING |
| A-05 | A §A.5 p8, lines 1-64 | UnitNormalizer.java | PR; 77-81 | Trace shown UCUM/conversion behavior and patient consequence | PENDING |
| A-06 | A §A.6 pp9-10, lines 1-77 | ResultMapper.java | PR; 77-81 | Trace actual mapping/derivation/flag/correction behavior | PENDING |
| A-07 | A §A.7 pp10-11, lines 1-84 | ResultRepository.java | PR; 77-81 | Trace shown queries/filter/order/paging/parameters without inventing helper defects | PENDING |
| A-08 | A §A.8 pp11-12, lines 1-54 | AccessLogWriter.java | PR; 77-81 | Trace shown audit/transaction/error behavior; no execution | PENDING |

## Submission, exclusions and candidate process requirements

| ID | Source | Obligation | Planned delivery; tasks | Evidence required | Current status |
|---|---|---|---|---|---|
| SUB-01 | T p3 Submission | ONE repository or archive containing all four completed components | Final repo/package; 2,90,93-94 | Actual package inventory and integrity/access inspection | PARTIAL — private structure exists; final components pending |
| SUB-02 | T p3 Submission 1 | Include completed Specification and Architecture Note | N1/N2; 18-32,90,93 | Final two-section document, consistent with build/limits | PENDING |
| SUB-03 | T p3 Submission 2 | Include build WITH actual history and generator script | APP / DB / DATA / HIST; 4-5,33-76,91,93 | Actual source/generator and recoverable genuine final Git history | PARTIAL — HIST setup/checkpoints only |
| SUB-04 | T p3 Submission 2 | ONE command builds and starts service | RUN / scripts; 88-89 | Follow command from fresh checkout with documented prerequisites | PENDING |
| SUB-05 | T p3 Submission 2 | ONE command creates AND loads dataset | RUN / DATA / scripts; 66,88-89 | Empty-DB clean run creates schema/data and proves deterministic scenarios/counts | PENDING |
| SUB-06 | T p3 Submission 2 | Run instructions include eGFR LOINC and source verified against | RUN; 60,88-89 | Implemented code/source citations and exact unit behavior; also R3-03 | PENDING |
| SUB-07 | T p3 Submission 3 | Include complete static Attachment A review | PR; 77-81,90,93 | Findings meet PR-01..06 and scope all eight files | PENDING |
| SUB-08 | T p3 Submission 4 | Each substantial AI item: asked, returned, shipped, WHY differences | LOG; 6,90-91 | Final artifact/log reconciliation, meaningful changes/rejected alternatives and actual checks | PARTIAL — maintained through current checkpoint; update continues |
| SUB-09 | T p3 Submission 4 | Which R1-R6 delegated/retained and WHY | DELEG / LOG; 7,91 | Actual agreement and six-row reasons with honest authorship; final shipped split reconciled | RECORDED — DELEG; final Task 91 reconciliation remains |
| SUB-10 | T p3 Submission 4 | Access-rule AI instruction EXACTLY as given | PROMPT / LOG; 7,91 | Immutable adopted v1, actual adoption source/hash; preserve subsequent actual versions | RECORDED — PROMPT; final Task 91 reconciliation remains |
| SUB-11 | T p3 final paragraph | Unclear or believed-wrong requirements stated in specification, never guessed | N2 decisions; 10-17,90 | Q rows resolved/documented with candidate decisions before implementation | PENDING |
| SUB-12 | T p1/p3; U deadline | Submit by end of 27 October; candidate's hard cutoff 22:00 Asia/Colombo, target 21:00 | Delivery; 1,94-96 | Correct actual recipient/method/version/access and confirmed delivery; Friday is not submission deadline | PARTIAL — clock confirmed; actual delivery pending |
| EX-01 | T p2 §2.3 | Real authentication excluded; test headers are not production authentication | N2 / APP / RUN; 9,14,35,90,93 | Explain trust boundary; no authentication assurance claim | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-02 | T p2 §2.3 | Identity provisioning excluded | Scope / final inventory; 9,90,93 | No unnecessary provisioning feature; design needs distinguished from build | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-03 | T p2 §2.3 | Login screens excluded | Scope / UI / final inventory; 9,90,93 | No login UI added as assignment work | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-04 | T p2 §2.3 | Notifications excluded | N1/N2 / final inventory; 9,16,21,90,93 | Critical design duty preserved without adding notification build | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-05 | T p2 §2.3 | Billing and insurance excluded | N1/N2 / final inventory; 9,21-22,31,90,93 | No insurer-export/billing build; HR-06 still addressed | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-06 | T p2 §2.3 | Scheduling excluded | Scope / final inventory; 9,21,90,93 | No scheduling feature | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-07 | T p2 §2.3 | Careline write-back excluded | N1/N2 / final inventory; 9,21,32,90,93 | Coexistence/inbound design does not create write-back | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| EX-08 | T p2 §2.3 | Container OR cloud deployment excluded | N2 / RUN / final inventory; 9,21,88-90,93 | Reproducible local build; no deployment detour; operations design still required | PARTIAL — SCOPE recorded and initial inventory checked; final inspection pending |
| U-01 | U persistent directions | Follow assignment strictly and defensible engineering/privacy/security practices THROUGH delivery | AGENTS / engineering rules / TEST; 9-10,33,35,41,62,65,76,79,82-94 | Trace applicable controls, synthetic-only data, safe inputs/errors/logs, secrets/package inspection; no unsupported certification | PARTIAL — working rules recorded; product verification pending |
| U-02 | U persistent directions | Ask about uncertain facts/decisions; help candidate understand and defend whole submission | Decisions / guided walkthrough; 10-17,62,81,86,90,92 | Actual decisions and guided checks, no invented human verification | PARTIAL — agreement recorded; walkthrough pending |
| U-03 | U workflow choice | Private dp1604/lyvemd-technical-assignment; approved same-service plain HTML/CSS/JS client; isolated tools | Repo / APP / UI / RUN; 3-5,33,63-65,88-89,93-94 | Keep repository private, final access deliberately checked; approved architecture actually implemented | PARTIAL — repo/tools prepared; application and final access pending |
| U-04 | U checklist communication | Maintain ordered checklist; announce each actually completed task at end of response | Process / LOG; 1-8,90-96 | Only completed boxes checked; no automatic claim that future task passed | PARTIAL — continuing process |
| V-01 | U chosen verification plan | Clean-start, failure, backup/restore and guided checks support required claims | TEST / evidence / RUN; 41,62,76,81-89,92 | Actual reproducible evidence and clear separation of our supporting checks from explicit source deliverables | PENDING |
| V-02 | U chosen delivery plan | Freeze/reconcile/package, inspect destination/access, draft reply, candidate submits | Final package / reply; 90-96 | Correct recipient/method verified at delivery; no messages/invitations/submission without actual authorization | PENDING |

## Open decisions tracked for later tasks

These are links to scheduled decision work, not decisions taken by this matrix. The [decision register](DECISIONS.md) records adopted Group 1 policies; the final specification and implementation evidence remain pending. Further ambiguity discovered later must be added rather than forced into these examples.

| ID | Source / issue | Decision needed | Affected rows / tasks | Evidence before implementation | Current status |
|---|---|---|---|---|---|
| Q-01 | T R1/§2.3 unspecified supported FHIR surface/API details | Supported interactions/resources, validation and unsupported-entry behavior; pagination defaults/limits, time bounds and tie ordering | R1-01/02/05, DM, EP; 10-11,14-15 | Primary-source verification where needed; candidate decisions, explicit API policies and declared limitations | RECORDED — Group1 adopted bounded surface/time/paging policies; Group4 ingestion verified, remaining read policies Group5 |
| Q-02 | T R1 transaction-response versus normative failed-transaction response | Resolve task/standard failure-response discrepancy while preserving atomicity and invalid-entry identification | R1-03, EP-01, DS-08; 12 | Verify official R4 source, decide with candidate and document task-specific behavior honestly | RECORDED — adopted task-first deviation, implemented/disclosed and verified in Group4 |
| Q-03 | T R1 unspecified replay/conflict/amendment matching | Missing/reused key, changed body, concurrent replay; how amended input identifies original | R1-04/06, CL-14, DS-04/07; 13 | Explicit candidate-approved rules and meaningful tests | RECORDED — adopted exact-byte replay/head-linked correction policies, backend verified in Group4; assignment dataset/final reconciliation pending |
| Q-04 | T §2.3/HR1 unspecified header details and non-STAFF log rights | Actor validation and authoritative stored entitlement; permitted access-log roles beyond STAFF | HR-01, CT-01, EP-05; 14 | Explicit policy; test interface cannot establish production identity | RECORDED — adopted Group1 role/identity policy; POST/detail implementation verified in Group4, remaining routes/workflow pending |
| Q-05 | T HR4/HR7/§2.4 leaves demographic/time/clinical edge policies unspecified | Confidentiality time basis; historical age/sex; BMI most-recent selection; missing/inconsistent inputs and numeric boundaries; supported UCUM inputs and eGFR applicability/code selection | HR-04/07, DM-01/04, CL; 15,52,60-62 | Separate source facts from chosen policies, verify clinical sources; record candidate decisions before code | RECORDED — adopted time/context/BMI policy; Group3 engine and Group4 historical storage/detail verified, derived read/UI integration pending |
| Q-06 | T HR2 unspecified release/critical representation and thresholds | Demonstration state/workflow and non-silent critical path without invented clinical thresholds or excluded notifications | HR-02, P1-05, EX-04; 16 | Candidate decision; distinguish required production process from implemented scope | RECORDED — Group 1 adoption; implementation pending |
| Q-07 | T R5 same transaction versus HR7 own transaction | Reconcile wording; denial persistence and audit-failure behavior | R5-01/02, HR-07, DM-05; 17 | Document chosen interpretation with candidate, then verify transaction/failure paths | RECORDED — adopted per-action read/audit policy; Group4 ingestion/detail/fault checks verified, remaining read routes and final outage/timing regression pending |

## Maintenance and completion gate

Task 8 completes baseline traceability only. Before final delivery, every required row must point to the actual finalized artifact and supporting result, or explicitly disclose an unsatisfied obligation; an OPEN question cannot silently become an assumed policy. Check clinical rows and dataset scenarios individually. Keep architecture projections, source facts, chosen policies, implemented behavior and measured evidence distinct. The final matrix reconciliation is Task 90, not a pass implied by this baseline.
