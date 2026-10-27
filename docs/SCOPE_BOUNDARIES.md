# Build scope boundaries

Current human contribution: [candidate decisions, independent review and partial execution](CANDIDATE_REVIEW.md). Original AI execution evidence remains scoped to its recorded checkpoint.

Status: boundaries recorded in Task 9 and checked against the supplied brief/current tracked inventory. The implemented build and bounded verification are linked from the current requirement matrix. Preserve these boundaries through implementation, the Task 90 reconciliation and Tasks 93-94 package/access checks; this is not a final scope-compliance certificate.

The [source record](SOURCE_RECORD.md), [requirement matrix](REQUIREMENT_MATRIX.md) and supplied Technical Task govern this document. The brief's literal exclusions, our agreed delivery choices and still-required design obligations are distinct. A filename or documentation mention of an excluded subject is not itself a feature implementation.

## Explicit brief exclusions

T p2 §2.3 separately excludes real authentication and then lists the other build exclusions. EX IDs below correspond to the requirement matrix. These boundaries apply to our build; production architecture and Dana's conflict resolutions remain required where the note asks for them.

| Matrix ID | Excluded from the build | Required work preserved |
|---|---|---|
| EX-01 | Real authentication | Keep X-Test-Actor-Id and X-Test-Actor-Role as untrusted test inputs; validate them and enforce authoritative stored permissions on every read. Explain that the test interface does not prevent real-world impersonation. No authentication assurance claim. |
| EX-02 | Identity provisioning | Seed synthetic identities/relationships for the required scenarios. Preserve individual, revocable, attributable proxy access and clinician relationships; representing/revoking proxy links is not permission to omit those safeguards. No identity-enrollment service added. |
| EX-03 | Login screens | Provide the required results, trend-chart and access-log client, including keyboard access, non-color-only flags and a distinct no-applicable state. Any test-caller control is clearly an assessment interface, not a production login. |
| EX-04 | Notifications | Preserve clinician release before patient visibility and a non-silent critical-value path in the design. Representation/workflow details and unspecified thresholds remain Task 16 decisions. Do not add patient email/SMS/push delivery. The required operations paragraph still identifies the first three proposed monitoring alerts. |
| EX-05 | Billing and insurance | Resolve Dana's insurer request in the note. Identifiable outbound data requires recorded legal basis AND patient consent; otherwise only de-identified aggregates may leave. No insurer export, premium calculation or billing feature. |
| EX-06 | Scheduling | Keep the required build focused on results, trends, audit and ingestion. No appointment-booking or scheduling feature. |
| EX-07 | Write-back to Careline | Accept required FHIR transactions in our service, explain coexistence with the legacy system, and verify actual upstream formats before integration design. No Careline update/write-back adapter. No actual Careline/feed samples were supplied. |
| EX-08 | Container or cloud deployment | Deliver reproducible local build/start and create/load commands using the mandatory stack. Still explain production storage/indexing, scale, enforcement, restore proof, alerts and dependency failure. No Docker/container deployment, cloud provisioning, AWS deployment or infrastructure-as-code detour. |

All eight house rules remain mandatory in the design. R2 explicitly requires rules 1,3,4,5 on every read server-side; that enumeration does not waive the other rules. Exclusions do not remove unit/eGFR correctness, audit/denial handling, amendments, confidentiality, upstream verification or outbound-data restrictions.

## Agreed delivery choices and interview context

| Topic | Current working boundary | Source/qualification |
|---|---|---|
| Minimal client | One Spring Boot service serving plain HTML/CSS/JavaScript; no separate native iOS/Android implementation | Candidate approved this approach. Native apps are not a literal §2.3 exclusion: Dana's mobile request must still be addressed explicitly in release-1 scope/reasons during Task 21. |
| Device firmware / Rust | No medical-device firmware or Rust build | The candidate supplied this as employer/interview technology context; it is not a Part 2 deliverable. This is our scope boundary, not a fabricated brief exclusion. |
| Earlier IoT / edge / AWS scenario | No device-streaming, clinical-network edge server or always-available cloud-ingestion project added | The scenario came from the first interview, separately from this take-home. It can inform later interview preparation without replacing or expanding the supplied assignment. |
| Private GitHub workspace | Keep the authorized private repository and authentic history; final reviewer access/submission method is still a delivery check | Candidate workflow choice, not an employer requirement for a public repository or an invitation to send/share prematurely. |

## Current inspection and continuing gate

At the pre-Task-9 snapshot (`c63a7a7563b21056d957c1219a2740f8a8ca77b5`), the tracked tree contains 44 files: curated documents/configuration, reserved source/test/data directories and the history-checkpoint script. No application or excluded feature implementation is present in that inventory. Ignored tools, database files and environment-only probe remain local; they are not the deliverable application. This is a bounded inventory finding, not a future guarantee.

For every material implementation/dependency/route addition, check its matrix IDs and this scope before adding it. Preserve every mandatory obligation. If an unclear requirement or policy conflict appears, verify the source, explain it to the candidate and record the decision in the specification/register; do not silently narrow the brief or add an excluded feature. Task 10 creates the register, and Tasks 11-17 resolve the currently scheduled policies. This document decides none of them.

Final checks must inspect the actual source, client screens, dependencies, scripts and package. Keyword searches are leads for human/AI inspection, not proof that a forbidden feature exists or is absent. Keep scope documentation, the note, README, matrix and executed evidence consistent. Attachment A remains static review material: never fix, build or run it. Preserve AI authorship and actual verification; do not invent candidate-run checks or standards certification.
