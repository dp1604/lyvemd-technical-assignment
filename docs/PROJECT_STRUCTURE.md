# Project structure

Status: Groups2–4 foundation, clinical rules, ingestion and secured detail implemented; remaining three GET routes, workflow, dataset and UI remain pending. Directory names are our delivery choices; the mandatory contract comes from the supplied assignment.

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
scripts/data/                          Deterministic dataset generator source
docs/                                  Decisions, traceability and supporting notes
docs/evidence/                         Sanitized, reproducible verification evidence
docs/ai/prompts/                       Exact substantial instruction records
docs/ai/outputs/                       Relevant returned AI material
```

Root pom.xml, service/configuration/transaction/error classes, V1/V2 migrations, clinical/ingestion/detail code, tests and local build/start/verification commands now exist. See docs/evidence/group-4-ingestion.md for the154-test checkpoint. Remaining .gitkeep files reserve future feature/fixture/client locations; they are not implementations or tests. See docs/DATABASE.md and docs/evidence/group-2-foundation.md for actual scope and checks.

The candidate approved one Spring Boot service and a plain browser client. Serving the client from the service keeps one application build and a shared origin. A framework or native mobile build is not needed for the minimal client requirement. The note must still address Dana's iOS/Android request explicitly.

Local work/ and outputs/ remain outside version control. work/ includes downloaded dependencies, a synthetic database and its credentials, environment probes and source-text review material. A curated environment summary is deliberately included under docs/evidence; the local inventory and raw logs are not uploaded.

The final delivery must include all four completed components, source, tests, migrations, dataset generator, verified run instructions and recoverable history. GitHub access for employer reviewers remains a later delivery check.
