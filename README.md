# LyveMD technical lead assignment - Aldercrest Atlas

This private repository contains the supplied technical assessment. Aldercrest, Careline and Atlas are fictional assessment context.

**Current status:** Group2 service/database foundation is implemented. Clinical APIs, calculations, data generator and browser client remain pending. The two-section note and static PR review have parallel AI drafts awaiting integration and guided review. This is not yet a complete submission or production system.

| Required component | Location | Status |
|---|---|---|
| Specification and Architecture Note | [SPECIFICATION_AND_ARCHITECTURE.md](SPECIFICATION_AND_ARCHITECTURE.md) | Outline/adopted clarifications; parallel draft |
| Build, history, generator and instructions | src/, scripts/, this README and authentic Git history | Foundation runnable; clinical implementation/generator pending |
| Static Attachment A review | [PR_REVIEW.md](PR_REVIEW.md) | Parallel draft; supplied code never built/run/fixed |
| AI working log | [AI_WORKING_LOG.md](AI_WORKING_LOG.md), docs/ai/ | Maintained incrementally; final reconciliation pending |

One Spring Boot service will serve the API and same-origin plain HTML/CSS/JavaScript client. Required stack: Java21+, Spring Boot3.x, HAPI FHIR R4/4.0.1 and PostgreSQL16. Pinned build: Boot3.5.16 and HAPI8.12.1; dependency/plugin versions otherwise come from that exact parent. The local tools verified are Java21.0.12.1, Maven3.9.16 and PostgreSQL16.15. See [environment evidence](docs/evidence/environment.md), [foundation evidence](docs/evidence/group-2-foundation.md) and [schema choices](docs/DATABASE.md).

## Build and start

Prerequisites for the tested Windows path: PowerShell7, JDK21+, Maven3.9+ and PostgreSQL16 binaries (including initdb, pg_ctl and psql). Java and Maven must be on PATH/JAVA_HOME; PostgreSQL can be on PATH or its bin directory set in ATLAS_PG_BIN. Existing ignored workspace tools are detected automatically but are not shipped. First build needs access to Maven Central for pinned dependencies; later offline use requires the complete cache. Do not commit downloaded tools or credentials.

From the repository root, one command builds, prepares/migrates the isolated database and starts the service:

```powershell
pwsh -NoProfile -File ./scripts/start.ps1
```

Default HTTP address: http://127.0.0.1:18081 (ATLAS_HTTP_PORT can override the port). At this checkpoint all clinical routes and the root UI return a safe404 because they are not implemented. Ctrl+C stops the serving process. The database remains available for subsequent work. To stop only this assessment's database after the application stops:

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

## Dataset and clinical sources — pending implementation

The required one-command deterministic generator/loader, dataset size/reproduction details, clinical equation implementation and measured performance will be added and verified in their groups. No generated assignment dataset exists yet. Adopted eGFR choice98979-8 and equation/unit policies are in [the decision register](docs/DECISIONS.md); final run instructions must cite the actually implemented/verified [2021 adult CKD-EPI equation](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults) and [LOINC](https://loinc.org/98979-8).

## Scope and safeguards

Follow [AGENTS.md](AGENTS.md), [engineering/security rules](docs/ENGINEERING_AND_SECURITY.md), [scope](docs/SCOPE_BOUNDARIES.md), [adopted decisions](docs/DECISIONS.md) and [requirement matrix](docs/REQUIREMENT_MATRIX.md). Test actor headers are untrusted and impersonatable; real authentication is explicitly excluded. Identity provisioning/login, notifications, billing/insurance, scheduling, Careline write-back and container/cloud deployment are excluded. The required production architecture discussion remains in scope. Do not execute Attachment A.

The intentional assessment-first failed transaction-response envelope and bounded create-only FHIR profile are adopted limitations awaiting implementation, not universal FHIR conformance. No legal/clinical/security certification or full assignment compliance is claimed. Local tools, database files, raw logs, original attachments/correspondence, transcripts and secrets are excluded from Git and final artifacts.

See [group plan](docs/EXECUTION_PLAN.md), [actual state](docs/EXECUTION_STATE.json), [history preservation](docs/HISTORY.md) and [project structure](docs/PROJECT_STRUCTURE.md). Final source/deliverables, history, dataset, tests, restore and recipient access must be reconciled before delivery.
