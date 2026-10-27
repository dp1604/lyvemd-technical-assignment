# Aldercrest Atlas — LyveMD technical assignment

Atlas is a local demonstration of clinical ingestion, patient and proxy access rules, interpretation, and auditable reads. All data is synthetic. The API and browser client run in one Spring Boot service.

| Deliverable | Location |
|---|---|
| Specification and Architecture Note | [Dana proposal and engineering design](SPECIFICATION_AND_ARCHITECTURE.md) |
| Implementation and dataset generator | `src/`, `pom.xml`, `scripts/`; run instructions below |
| Attachment A review | [Findings and recommendations](PR_REVIEW.md) |
| AI working log and delegation | [Working log](AI_WORKING_LOG.md), [R1–R6 responsibilities](docs/ai/DELEGATION.md), [exact access-rule instruction](docs/ai/ACCESS_RULE_INSTRUCTION.md) |
| Review and decision record | [Candidate contribution and review](docs/CANDIDATE_REVIEW.md) |
| Development history | Authentic Git commits; archives also include `history.bundle` and `HISTORY_RECOVERY.md` |

## Before starting

The automated setup supports **Windows with PowerShell 7**, **JDK 21+**, **Maven 3.9+**, and **PostgreSQL 16 binaries**. PostgreSQL must include `initdb`, `pg_ctl`, `psql`, `createdb`, `pg_dump` and `pg_restore`. Set `JAVA_HOME`, put Maven on `PATH`, and set `ATLAS_PG_BIN` to PostgreSQL's `bin` directory. Tools and downloaded dependencies are not included in the submission. The first Maven build needs access to Maven Central.

The pinned application uses Java 21, Spring Boot 3.5.16 and HAPI FHIR 8.12.1 with R4/FHIR 4.0.1. Verification used JDK 21.0.12.1+1, Maven 3.9.16 and PostgreSQL 16.15. See [setup and troubleshooting](scripts/README.md) for prerequisite checks, environment examples, ports and recovery.

## The two required commands

Open PowerShell 7 in the repository root. Build and start the service:

```powershell
pwsh -NoProfile -File ./scripts/start.ps1
```

Leave that terminal open. In a second PowerShell 7 terminal, also in the repository root, create and load the dataset:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1
```

Wait for `ATLAS_DATASET 200 LOAD_AND_VERIFICATION_COMPLETED`, then open **http://127.0.0.1:18081/**. There is no separate client installation or build. Use a current browser supporting `JSON.parse` reviver source context; an unsupported browser receives an explicit capability message.

The dataset's generated `work/dataset/v1/load-manifest.json` contains the synthetic patient, actor and resource IDs. Apply a matching test actor ID and role in the client before reading results. These controls declare the assignment's test headers; they are **not authentication**. The server checks the declared pair against its stored roster and relationships. See [dataset guide](docs/DATASET.md) and [client guide](docs/CLIENT.md).

The scripts create an isolated PostgreSQL cluster on `127.0.0.1:55433`, generate local credentials and separate migration/runtime roles, and preserve existing data. Dataset loading uses its own temporary service on `127.0.0.1:18091`, leaving the browser service running. The database and generated files stay under ignored `work/`; do not commit or share them.

Stop the foreground service with **Ctrl+C**. To stop the assessment database afterwards:

```powershell
pwsh -NoProfile -File ./scripts/stop-local-database.ps1
```

This stops the cluster without deleting it. The next start reuses its data and credentials.

## Dataset and clinical sources

Recipe `atlas-g6-v1`, seed `20261027`, semantic date `2026-10-27`, produces **100 patients, 106 encounters and 10,000 stored source Observations**. Calculated BMI/eGFR views are additional response rows; the measured clinician lists contained 3,693 such rows. Server-assigned IDs can differ on a fresh database; the clinical recipe remains reproducible.

The generator and loader exercise all eight required scenarios: creatinine in both units; age-13/16 proxy confidentiality; the same patient's haemoglobin across age 14; retained amendments; weight without any height; a multiyear series; a Bundle actually submitted twice; and an invalid-entry Bundle actually rejected without partial clinical writes. Ordinary reruns use their receipts and idempotency keys. They do not reset the database. The [dataset guide](docs/DATASET.md) explains resume and exceptional rejected-receipt handling.

Adult eGFR uses the race-free **2021 CKD-EPI creatinine equation**, verified against the [NIDDK equation and creatinine conversion](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults). The chosen code is [LOINC 98979-8](https://loinc.org/98979-8). Creatinine `umol/L` is divided by 88.4 to obtain canonical `mg/dL`; the equation's per-minute result is divided by 60 for `mL/s/1.73m2`, machine UCUM `mL/s/{1.73_m2}`. Calculation requires suitable explicit clinical sex, completed age of at least 18 at measurement time, positive standardized creatinine and valid context. Otherwise the response explains why it cannot compute. [Calculation rules](docs/CLINICAL_RULES.md) and [oracle checks](docs/evidence/clinical-oracle.md) provide the detail.

**Terminology conflict in the brief:** its potassium row uses `2947-0`, which official LOINC identifies as sodium in blood. This synthetic assessment preserves the supplied mapping and documents the conflict. Real feeds require verified specimen/terminology mapping and clinical approval before use.

## Verification

Run the backend unit and PostgreSQL integration checks against the designated separate `atlas_test` database:

```powershell
pwsh -NoProfile -File ./scripts/verify.ps1
```

The complete recorded backend run passed **237 cases: 163 unit and 74 integration**. A subsequently added connection-provider outage/recovery integration case passed separately; this was not a full rerun with a new total. [Regression evidence](docs/VERIFICATION.md).

The read target was **p95 ≤500 ms per measured case**. Five cases covered all four GET routes using the 100-patient/10,000-source dataset: 10 warmups and 100 measurements per case, plus one discovery call. All **551 requests** returned valid HTTP 200 responses with exactly **551 audit rows**. Per-case p95 was **27.0271–57.7036 ms**. List and sodium-trend maxima were **631.5012 and 761.7399 ms**. This was a warm, serial, loopback measurement, not a production-scale load test. [Full conditions and results](docs/PERFORMANCE.md).

The two public commands were also exercised from a fresh checkout and empty cluster with installed tools and a warm dependency cache. The additional verification wrapper later failed after loading; its report identifies what completed and what did not. [Clean-start evidence](docs/VERIFICATION.md). A separate restore drill matched all ten tables, schema, grants and sequences before four allowed/denied HTTP checks: **103.386 seconds overall**, including **5.786 seconds for `pg_restore`**. [Restore evidence and limits](docs/VERIFICATION.md).

Optional Node checks are listed in [script instructions](scripts/README.md). Do not run loaders, tests, restores or benchmarks concurrently against the same database.

## Important boundaries

The adapter accepts a bounded subset of JSON R4 transaction Bundles: POST creates of Patient, Encounter and Observation. Incoming BMI/eGFR is rejected because Atlas derives these views. The brief's failed-transaction response envelope is followed and its difference from normative FHIR R4 is documented in the [ingestion contract](docs/FHIR_INGESTION.md).

Access rules, release/confidentiality filtering, visible-only calculations/counts and synchronous transactional auditing apply to the application paths. The [read-path inventory](docs/READ_PATH_INVENTORY.md) explains the trusted database-operator boundary. A failed required audit or commit withholds the clinical response; a database outage cannot record an audit in that unavailable database.

Hidden/missing responses have tested status/body and metadata controls. Timing indistinguishability is **not established**, so that part of HR5/R2 remains open. Eligible patient history is materialized before Java calculations and pagination, which limits scale; the local measurements do not establish performance at 9 million records. Critical-path attestation records an action, not proof of patient contact. Production identity, clinical/feed approval, operator controls, keys and recovery governance remain outside this demonstration.

The review of Attachment A is static only; its supplied code was not fixed, built or run. The [working log](AI_WORKING_LOG.md) records substantial AI assistance, retained decisions, revisions and verification. The [contribution record](docs/CANDIDATE_REVIEW.md) distinguishes candidate direction and review from tool execution.
