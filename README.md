# LyveMD technical assignment — Aldercrest Atlas

**Terminology conflict:** the brief labels `2947-0` as potassium; official LOINC identifies sodium in blood. This synthetic assessment retains the supplied convention with a prominent notice, following the [recorded candidate decision](docs/decisions/group-6-loinc-clarification.md). Real clinical use requires verified specimen/terminology mapping and clinical-owner approval.

Aldercrest, Careline and Atlas are fictional assessment context. The application is local and synthetic. The four required components are:

| Component | Start here |
|---|---|
| Specification and Architecture Note | [Two sections: Dana and engineering](SPECIFICATION_AND_ARCHITECTURE.md) |
| Build, generator, run instructions and authentic history | This README, `pom.xml`, `src/`, `scripts/` and [history/recovery process](docs/HISTORY.md) |
| Static review of Attachment A | [PR review](PR_REVIEW.md): 24 blockers, 3 preferences, 5 qualified follow-up checks; supplied code never built, run or fixed; [written walkthrough](PR_REVIEW_WALKTHROUGH.md) |
| AI working log | [Actual contributions, changes and evidence](AI_WORKING_LOG.md), [R1–R6 delegation](docs/ai/DELEGATION.md), [exact adopted instruction and provenance](docs/ai/ACCESS_RULE_INSTRUCTION.md) |

## Prerequisites

The verified Windows path uses PowerShell 7, JDK 21+, Maven 3.9+ and PostgreSQL 16 binaries, including `initdb`, `pg_ctl`, `psql`, `pg_dump` and `pg_restore`. Put Java/Maven on PATH with JAVA_HOME set; set ATLAS_PG_BIN to PostgreSQL's bin directory or make those tools available on PATH. Existing ignored workspace tools are detected, but are not shipped. First dependency resolution needs Maven Central; verification used an already populated external Maven cache, not a clean machine or cold download.

Pinned application: Java 21, Spring Boot 3.5.16, HAPI FHIR 8.12.1 using R4/FHIR 4.0.1, PostgreSQL 16. Locally verified tools: JDK 21.0.12.1+1, Maven 3.9.16, PostgreSQL 16.15. Optional client-model/performance tools use Node 24 without downloaded packages. The browser must support standard JSON.parse reviver source context; unsupported clients receive an explicit unavailable-capability message. [Environment](docs/evidence/environment.md), [client contract](docs/CLIENT.md).

## The two required commands

From the repository root, build, prepare/migrate the isolated database and start the service:

```powershell
pwsh -NoProfile -File ./scripts/start.ps1
```

After readiness, use a second terminal in the same repository to create and load the deterministic dataset:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1
```

Open http://127.0.0.1:18081/. The same Spring Boot service serves the API and plain HTML/CSS/JavaScript client; there is no client installation/build step or remote chart/font dependency. Use the synthetic IDs and relationships in the generated `work/dataset/v1/load-manifest.json`, as explained in [DATASET](docs/DATASET.md). Test actor controls declare X-Test-Actor-Id/Role; they are impersonatable assessment input, **not authentication**.

The local bootstrap binds PostgreSQL to 127.0.0.1:55433, uses random ignored SCRAM credentials, separates migration/runtime roles, and preserves existing data. The dataset command uses its own bounded loopback helper on 18091, performs real HTTP ingestion and local clinician/proxy workflow actions, verifies the scenarios and stops its helper. Ctrl+C stops the foreground service; the database persists. After stopping the app, `pwsh -NoProfile -File ./scripts/stop-local-database.ps1` stops only this assessment cluster. [Script behavior](scripts/README.md).

The two public commands were exercised from a fresh full-history checkout and new empty cluster, using external installed tools and a warm dependency cache. The actual result, counts, scenarios, asset checks, additional-wrapper failure and source restoration are in [clean-start evidence](docs/evidence/group-12-clean-start.md). This is a fresh-source/database check, not a fresh-machine test.

## Dataset and clinical interpretation

The fixed recipe `atlas-g6-v1`, seed 20261027, semantic date 2026-10-27 generates **100 patients, 106 encounters and 10,000 stored source Observations**. Clinician lists additionally contained 3,693 calculated/unavailable view rows; these are not stored source Observations. The eight required cases include both creatinine units, age-13/16 proxy confidentiality, same-patient haemoglobin across age 14, retained amendments, weight with no height ever, a 94-point multi-year series, actual exact-Bundle replay and actual invalid-Bundle rollback. [Recipe/reproduction](docs/DATASET.md), [actual scenario and recovery proof](docs/evidence/group-6-dataset.md).

Replays preserve committed IDs. A genuinely fresh database assigns fresh server IDs while reproducing the recipe's clinical inputs. Existing historical failed receipts and narrowly guarded explicit recovery are documented; ordinary runs never silently rotate a completed failed key. Proxy access uses the current UTC request date, while clinical ranges use the observation's effective date; fixed dated fixtures are not a promise of unchanged ages forever.

Creatinine UCUM `umol/L` divides by **88.4** into canonical `mg/dL` before calculation. Adult eGFR uses race-free **2021 CKD-EPI creatinine**, with the [NIDDK equation/source and conversion](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults), and chosen [LOINC 98979-8](https://loinc.org/98979-8). The per-minute estimate is divided by exactly 60 for display `mL/s/1.73m2`, machine UCUM `mL/s/{1.73_m2}`. Explicit suitable clinical sex, completed effective-time age 18+, positive standardized serum creatinine and valid context are required; otherwise a reason is returned. No race factor or invented normal eGFR range is added. [Independent oracle and published NKF examples](docs/evidence/clinical-oracle.md), [rules](docs/CLINICAL_RULES.md).

## Verification and measurements

- **Backend:** one complete 237-case run (163 unit + 74 integration), followed by a separately executed new connection-provider failure/recovery IT. Its overlapping runtime unit is not added as a distinct case or called a full 238-case rerun. [Exact checkpoints, original failure/correction and scope](docs/evidence/group-10-regression.md).
- **Client:** eight focused model cases plus actual Codex browser/keyboard/role/privacy checks. The candidate subsequently reported independent review and partial personal execution; individual client checks remain partial. [Attributed evidence](docs/evidence/group-7-client.md).
- **Performance:** prior declared p95 ≤500 ms per case, five cases spanning all four GET routes, 10 warmups +100 measurements each, one discovery call: **551 valid HTTP 200 responses and exactly 551 audits**, unchanged source inventory. Measured p95 ranged 27.0271–57.7036 ms; list/sodium maxima 631.5012/761.7399 ms remain disclosed. [Conditions, every percentile/maximum and query-plan limits](docs/PERFORMANCE.md).
- **Restore:** all ten tables/content/schema/grants/sequences matched in a fresh restored database before four attributable allowed/denied HTTP checks. Total drill 103.386 s; pg_restore phase 5.786 s. Existing roles were prerequisites; this is not cluster-global/key recovery or approved production RPO/RTO proof. [Executed restore](docs/evidence/group-12-restore.md).
- **Clean startup:** [fresh checkout, empty cluster and both public commands](docs/evidence/group-12-clean-start.md), with explicit warm prerequisites and preservation checks.

To run the backend suite against the designated separate `atlas_test` database:

```powershell
pwsh -NoProfile -File ./scripts/verify.ps1
```

Optional client checks: `node scripts/client/client-model.test.mjs`. Optional clinical oracle: `node scripts/verify-clinical-oracle.mjs`. Performance and restore reproduction procedures are linked above. Do not run loads, tests, restore or benchmarks concurrently on the same database. Generated data, credentials and raw reports stay ignored.

## Scope and material limits

The adapter supports bounded JSON R4 transaction Bundles with POST creates of Patient, Encounter and Observation; it is not a general FHIR server. Incoming BMI/eGFR is rejected under the adopted derived-only profile. The invalid-Bundle transaction-response follows the brief and intentionally differs from normative R4's failed-transaction OperationOutcome envelope. [Adopted decisions](docs/DECISIONS.md), [ingestion contract](docs/FHIR_INGESTION.md).

Stored authorization, release/confidentiality, visible-only derivation/counts and synchronous same-transaction auditing apply to the application paths. [Auditor path inventory and trusted-operator boundary](docs/READ_PATH_INVENTORY.md), [requirement matrix](docs/REQUIREMENT_MATRIX.md). Hidden/missing status/body equality and metadata controls are tested; finite unequal timing samples do **not** establish timing indistinguishability or absence of a classifier. That literal limb of HR5/R2 remains unestablished and is disclosed.

Full eligible patient history is materialized before Java derivation/window/paging; neither an index nor a transaction timeout hard-bounds arbitrary Java work. The 10k serial-loopback measurements do not establish 9M/90M, concurrent, cold, remote-network or production capacity. Structural validation has a cooperative budget, not interruption of a single validator call. Clinician critical-process attestation records an action, not proof of real patient contact. Real feed/specimen/assay verification, clinical approval, production identity and operator/key/recovery governance remain production gates.

Identity provisioning/login, notifications, billing/insurance, scheduling, Careline write-back, native apps and container/cloud deployment are outside this assessment build. No real clinical data, vendor integration or external disclosure is implemented. [Scope boundaries](docs/SCOPE_BOUNDARIES.md). The candidate reports a subsequent independent submission walkthrough; remaining partial client checks are not replaced by AI checks, and no legal/clinical/security certification or guaranteed interview outcome is claimed.

## Development history and AI disclosure

The private Git repository retains authentic incremental commits. The submission archive additionally includes the verified `history.bundle`, checksum, `HISTORY_RECOVERY.md` and exact-file `SUBMISSION_MANIFEST.json`; clone the bundle to inspect earlier revisions without private GitHub access. No local Git configuration, tools, database, original PDFs/correspondence/transcripts or raw logs are included.

Codex and AI reviewers produced the code, drafts and automated checks. The candidate supplied context, adopted policies and subsequently independently reviewed selected work and partially exercised the application, as itemized in [Candidate contribution and review](docs/CANDIDATE_REVIEW.md). Original contributions and material revisions remain in [AI_WORKING_LOG](AI_WORKING_LOG.md). The [exact adopted access-rule instruction](docs/ai/ACCESS_RULE_INSTRUCTION.md) remains unchanged; later decisions are separate. See the note for reasons and production proposals, and the linked evidence for what was actually executed.
