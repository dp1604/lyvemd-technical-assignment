# LyveMD technical lead assignment - Aldercrest Atlas

This private repository contains the supplied technical assessment. Aldercrest, Careline and Atlas are fictional assessment context.

**Current status:** Groups2–4 passed154 automated cases. All five required API routes are now implemented, with additional focused Group5 checks for authorized lists, source/derived detail, trend, logs and transaction/audit failures. Group5 clinician workflow and complete regression are active; persisted assignment dataset and browser client remain pending. The two-section note and static PR review have parallel AI drafts awaiting integration and guided review. This is not yet a complete submission or production system.

| Required component | Location | Status |
|---|---|---|
| Specification and Architecture Note | [SPECIFICATION_AND_ARCHITECTURE.md](SPECIFICATION_AND_ARCHITECTURE.md) | Outline/adopted clarifications; parallel draft |
| Build, history, generator and instructions | src/, scripts/, this README and authentic Git history | Five API routes and clinical engine verified in focused suites; workflow/generator pending |
| Static Attachment A review | [PR_REVIEW.md](PR_REVIEW.md) | Parallel draft; supplied code never built/run/fixed |
| AI working log | [AI_WORKING_LOG.md](AI_WORKING_LOG.md), docs/ai/ | Maintained incrementally; final reconciliation pending |

One Spring Boot service will serve the API and same-origin plain HTML/CSS/JavaScript client. Required stack: Java21+, Spring Boot3.x, HAPI FHIR R4/4.0.1 and PostgreSQL16. Pinned build: Boot3.5.16 and HAPI8.12.1; dependency/plugin versions otherwise come from that exact parent. The local tools verified are Java21.0.12.1, Maven3.9.16 and PostgreSQL16.15. See [environment evidence](docs/evidence/environment.md), [foundation evidence](docs/evidence/group-2-foundation.md) and [schema choices](docs/DATABASE.md).

## Build and start

Prerequisites for the tested Windows path: PowerShell7, JDK21+, Maven3.9+ and PostgreSQL16 binaries (including initdb, pg_ctl and psql). Java and Maven must be on PATH/JAVA_HOME; PostgreSQL can be on PATH or its bin directory set in ATLAS_PG_BIN. Existing ignored workspace tools are detected automatically but are not shipped. First build needs access to Maven Central for pinned dependencies; later offline use requires the complete cache. Do not commit downloaded tools or credentials.

From the repository root, one command builds, prepares/migrates the isolated database and starts the service:

```powershell
pwsh -NoProfile -File ./scripts/start.ps1
```

Default HTTP address: http://127.0.0.1:18081 (ATLAS_HTTP_PORT can override the port). POST /fhir and the four required GET routes are implemented under the [bounded ingestion](docs/FHIR_INGESTION.md), [visibility](docs/RESULT_VISIBILITY.md), [result-read](docs/RESULT_READS.md) and [access-log](docs/ACCESS_LOG.md) contracts. The root UI remains pending. No assignment roster/data generator exists yet; verification uses separate synthetic test fixtures. Ctrl+C stops the serving process. The database remains available for subsequent work. To stop only this assessment's database after the application stops:

```powershell
pwsh -NoProfile -File ./scripts/stop-local-database.ps1
```

The local bootstrap uses a separate loopback cluster on55433, database atlas, schema atlas, random ignored credentials and separate migration/runtime roles. It preserves existing data. atlas_test is reserved for verification; tests must never point at the assessment dataset. Local PostgreSQL uses SCRAM authentication; the HTTP server binds loopback. These scripts are a Windows development path, not a deployment or authentication solution. See [script details](scripts/README.md).

## Verify

The meaningful automated foundation checks use real PostgreSQL16 in the designated test database:

```powershell
pwsh -NoProfile -File ./scripts/verify.ps1
```

This runs the normal unit tests and the integration profile. Maven package runs unit tests alone; it does not imply PostgreSQL/endpoint acceptance. Verification does not delete/reset a database. Synthetic test IDs vary to avoid clashes; deterministic assignment data is separate future work. Raw local test reports/logs stay ignored; curated outcomes belong in docs/evidence.

## Clinical engine and pending dataset

The required one-command deterministic generator/loader, dataset size/reproduction details and measured performance remain pending. No generated assignment dataset exists yet. The implemented [clinical engine](docs/CLINICAL_RULES.md) and [124-case verification](docs/evidence/group-3-clinical.md) use the [2021 adult CKD-EPI equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults) and [LOINC98979-8](https://loinc.org/98979-8). SCr umol/L divides by88.4 to canonical mg/dL; eGFR uses sex-specific κ/α,142,0.9938^age and female1.012, then converts the unrounded per-minute result by exactly60. Output display is mL/s/1.73m2, machine UCUM mL/s/{1.73_m2}. No race term/normal range is added. Only age18+ with explicit applicable clinical sex, positive standardized creatinine is computable; missing/inconsistent/unverified inputs return a reason. [NKF published examples](https://www.kidney.org/sites/default/files/example_it_ticket-implement_2021_ckd-epi_equation_to_calculate_egfr_from_creatinine_1.pdf) and a separate24-case oracle verify the calculation. Optional reproduction: node scripts/verify-clinical-oracle.mjs. Inbound BMI/eGFR values are rejected under the [adopted derived-only profile](docs/decisions/group-3-clarifications.md); ingestion storage and secured source-detail integration are verified in [Group4](docs/evidence/group-4-ingestion.md); derived list/trend/detail integration passed focused [Group5 checks](docs/RESULT_READS.md); browser UI remains pending.

## Scope and safeguards

Follow [AGENTS.md](AGENTS.md), [engineering/security rules](docs/ENGINEERING_AND_SECURITY.md), [scope](docs/SCOPE_BOUNDARIES.md), [adopted decisions](docs/DECISIONS.md) and [requirement matrix](docs/REQUIREMENT_MATRIX.md). Test actor headers are untrusted and impersonatable; real authentication is explicitly excluded. Identity provisioning/login, notifications, billing/insurance, scheduling, Careline write-back and container/cloud deployment are excluded. The required production architecture discussion remains in scope. Do not execute Attachment A.

The intentional assessment-first failed transaction-response envelope and bounded create-only FHIR profile are implemented, disclosed assessment limitations, not universal FHIR conformance. Current validation uses bundled definitions and prohibits managed HTTP resolution; runtime payload diagnostics are not logged. No legal/clinical/security certification or full assignment compliance is claimed. Local tools, database files, raw logs, original attachments/correspondence, transcripts and secrets are excluded from Git and final artifacts.

See [group plan](docs/EXECUTION_PLAN.md), [actual state](docs/EXECUTION_STATE.json), [history preservation](docs/HISTORY.md) and [project structure](docs/PROJECT_STRUCTURE.md). Final source/deliverables, history, dataset, tests, restore and recipient access must be reconciled before delivery.
