# Decision register — Group 1

Status: Tasks 10-17 decisions are recorded from actual candidate adoptions; document/source checks passed. Clinical implementation and verification remain pending; the Group2 service/schema foundation is separately documented. This register does not finalize the two-section note.

## Record rules

Separate source facts, candidate-adopted assessment policies, limitations and unresolved questions. An adopted policy is not an employer clarification, clinical sign-off, verified implementation or universal conformance claim. Source facts below are readings of the supplied brief or linked primary standards; recommendations become adopted only through the actual candidate answer.

The immutable reviewed [Package A v1](decisions/package-a-v1.md) and [Package B v1](decisions/package-b-v1.md) preserve exactly what was presented. Their historical PROPOSED header describes presentation time; current status is in this register and the [actual adoption records](decisions/adoptions.json). Do not edit a reviewed package after adoption; material changes require a new version and candidate decision. The earlier exact access-rule instruction remains unchanged.

## Source facts

| ID | Verified source fact | Source |
|---|---|---|
| F-01 | Required stack is Java21+, Spring Boot3.x, HAPI FHIR R4/4.0.1 and PostgreSQL16; five endpoints and the supplied model meanings remain required | Technical Task p2 §2.3; [source hashes](SOURCE_RECORD.md) |
| F-02 | All eight house rules govern the design; R2 specifically enumerates 1,3,4,5 for every server-side read | Technical Task pp1-2 R2/§2.2 |
| F-03 | The task requests invalid-entry reporting in a transaction-response, while R4 prescribes one OperationOutcome for a failed transaction | Technical Task p1 R1; [R4 response rule](https://hl7.org/fhir/R4/http.html#transaction-response) |
| F-04 | Patient.gender is administrative gender; it is not automatically the historical clinical-sex input needed by the supplied calculation/range rules | [R4 Patient.gender](https://hl7.org/fhir/R4/patient-definitions.html#Patient.gender) |
| F-05 | Published 2021 creatinine eGFR is an adult equation with mg/dL creatinine and per-minute result; NIDDK supplies the SI-creatinine conversion | [NIDDK equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults) |
| F-06 | UCUM uses u for micro and dot for multiplication; LOINC's eGFR example uses a normalized-area annotation | [UCUM](https://ucum.org/ucum); [LOINC98979-8](https://loinc.org/98979-8) |
| F-07 | PostgreSQL transactions support atomic work; isolation determines visibility of concurrent changes | [PG16 transactions](https://www.postgresql.org/docs/16/tutorial-transactions.html); [isolation](https://www.postgresql.org/docs/16/transaction-iso.html) |
| F-08 | No actual upstream sample/vendor deck, critical thresholds, historical-sex feed representation, complete log-role policy or real authentication was supplied | Technical Task and candidate context; [scope](SCOPE_BOUNDARIES.md) |

## Candidate-adopted assessment policies

| ID | Tasks / matrix | Adopted policy and reason | Authoritative detail |
|---|---|---|---|
| D-01 | 11; Q-01, R1, CT, DM | Scoped JSON transaction ingestion: POST Patient/Encounter/Observation; every entry structurally/application-validated, unsupported operations/semantics rejected atomically. Resolve complete local UUID map; no remote fetch or accidental upsert. Explicitly bounded adapter rather than general FHIR server. | A1; B1/B2/B3/B4 refinements |
| D-02 | 12; Q-02, R1-03, EP-01 | Assessment-first failed transaction-response with safe invalid-entry outcomes and uncommitted siblings; prominently disclose deliberate R4 failure-envelope deviation. Malformed transport input cannot promise entry indexes. | A2 |
| D-03 | 13; Q-03, R1-04/06 | Required actor-scoped exact-body idempotency with durable completed outcomes/concurrency control; fresh append-only correction with defined extension, head-only chain and original retention. No overwrite or false replay success. | A3 plus B2 visibility/context limits |
| D-04 | 14; Q-04, HR-01, CT-01, EP-05 | Bounded untrusted test headers/known roster plus stored entitlements; only known CLINICIAN ingestion; patient/STAFF log access only. No production identity assurance. | B1/B2 |
| D-05 | 15; Q-05, HR-04/07, CL | UTC request-date proxy confidentiality/expiry; observation-time age/sex interpretation from explicit effective-dated assertions, with provenance. Caller-visible as-of BMI, no hidden-input or hidden-successor oracle, no invented freshness/critical thresholds. | B2/B3/B4 |
| D-06 | 16; Q-06, HR-02 | Held-by-default results; entitled local clinician review, classification and separate release with audit. Critical completion attestation required; no automatic lab-signoff release or notification service. | B5 |
| D-07 | 17; Q-07, R5, HR-07 | One action/request transaction includes content reads and audit; materialize/commit before content; expected denials commit; audit failure withholds content. Consistent snapshot with explicit revocation/outage boundary. | B6 |
| D-08 | 11/15; Q-01/Q-05, CL-11/12 | Explicit BP parent85354-9 and eGFR98979-8; canonical conversion; task eGFR display label retained with correct machine UCUM annotation code. Source notation clarified rather than silently parsed as a decimal UCUM expression. | B4 |

All rows above are adopted as assessment policies. A was adopted in source message `01a17b79-8015-7d12-a414-e311a7cb218d`: "Adopt Package A v1 as written (recommended)". B was adopted in `01a17b82-32b9-7673-b54d-42acaaab6ef8`: "Adopt Package B v1 as written (recommended)". Codex drafted both; no earlier user wording or approval was reconstructed.

## Limitations and unresolved external facts

1. The create-only resource/profile subset, unsupported context-changing corrections and failure envelope are declared assessment limitations; candidate adoption does not guarantee employer acceptance or full FHIR conformance.
2. A correction with a concealed ancestor remains withheld. Never force release of a known-wrong original to enable its correction. Retained histories and latest-visible-to-caller summaries must not disclose hidden successors.
3. BMI is explicitly view-scoped with source dates and may differ from a clinician's fuller view. Hidden and absent inputs have the same public unavailable shape/reason. Clinical freshness and production suitability require clinical-owner validation.
4. The synthetic input contract asserts standardized creatinine; neither an ordinary Quantity nor LOINC alone verifies a real assay. Actual feed format, assay traceability/measuring interval and historical-demographic evidence remain production gates.
5. Critical-path completion is a recorded demonstration attestation, not proof of real contact or a complete production workflow. No clinical thresholds, legal jurisdiction or vendor facts are invented.
6. Test actors remain impersonatable. PostgreSQL unavailability prevents writing to that same database; fail closed without claiming impossible outage audit durability. Snapshot authorization cannot retract an already-authorized in-flight response.
7. The Group2 service/schema foundation and focused tests exist; official FHIR extensions/profile examples, clinical/endpoint code, feature migrations, dataset commands and acceptance tests remain their implementation groups. A decided policy is not a passed behavior.

Future unclear facts/policies are added here with source, options, candidate decision, reason, affected matrix IDs, implementation/evidence links and limitations. Preserve adopted versions and explain changes. The production-design duties and scope exclusions remain intact.

## Group3 clarifications

D-09 derived-only incoming BMI/eGFR restriction and D-10 March1 leap-day completed-age convention were explicitly adopted in actual candidate replies. See [source answers, reasons and limits](decisions/group-3-clarifications.md). A/B presentation files and exact access instruction remain immutable. Clinical core checks do not yet complete ingestion/read-route enforcement.
