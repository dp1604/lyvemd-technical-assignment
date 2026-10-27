# Project structure

Status: Groups 1–6 complete: foundation, clinical rules, all five API routes, local workflow and deterministic dataset with actual scenario/recovery checks. UI and final assignment acceptance remain pending. Directory names are delivery choices; the supplied assignment remains authoritative.

```text
README.md                              Run instructions and submission entry point
SPECIFICATION_AND_ARCHITECTURE.md       Required two-section note
PR_REVIEW.md                           Required static review
AI_WORKING_LOG.md                      Required AI assistance disclosure
AGENTS.md                              Persistent working rules
src/main/java/com/aldercrest/atlas/     Single service application source
src/main/resources/db/migration/       Versioned PostgreSQL migrations
src/main/resources/static/             Same-origin HTML/CSS/JavaScript client
src/test/java/com/aldercrest/atlas/     Meaningful automated tests
src/test/resources/fhir/               Deliberate synthetic test fixtures
scripts/                               Reproducible run/data/verification commands
src/main/java/com/aldercrest/atlas/dataset/  Java deterministic recipe, HTTP loader and fixture guard
docs/                                  Decisions, traceability and supporting notes
docs/evidence/                         Sanitized, reproducible verification evidence
docs/ai/prompts/                       Exact substantial instruction records
docs/ai/outputs/                       Relevant returned AI material
```

Root pom.xml, service/configuration/transaction/error classes, V1–V3 migrations, clinical/ingestion/all-read/workflow code, tests and local build/start/verification commands now exist. See docs/evidence/group-5-reads-and-workflow.md for the204-case full checkpoint, final affected recheck and CLI/timing evidence. The Java dataset package and scripts/load-dataset.ps1 implement generation/loading; [Group 6 evidence](evidence/group-6-dataset.md) records actual effects. Remaining .gitkeep files reserve still-empty locations; they are not implementations or tests. See docs/DATABASE.md and docs/evidence/group-2-foundation.md for actual scope and checks.

The candidate approved one Spring Boot service and a plain browser client. Serving the client from the service keeps one application build and a shared origin. A framework or native mobile build is not needed for the minimal client requirement. The note must still address Dana's iOS/Android request explicitly.

Local work/ and outputs/ remain outside version control. work/ includes downloaded dependencies, a synthetic database and its credentials, environment probes and source-text review material. A curated environment summary is deliberately included under docs/evidence; the local inventory and raw logs are not uploaded.

The final delivery must include all four completed components, source, tests, migrations, dataset generator, verified run instructions and recoverable history. GitHub access for employer reviewers remains a later delivery check.
