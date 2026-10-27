Current contribution status: [subsequent candidate review](../CANDIDATE_REVIEW.md). The evidence below preserves its original checkpoint; later human review does not alter that historical execution or result.

# Group 10 — integrated correctness and security evidence

Original Tasks 82–85 are complete against the adopted assessment contract. Root executed all reported checks. The candidate's actual client walkthrough (Task 86), dataset performance measurement (75–76), restore/clean-start checks and final reconciliation remain separate gates.

## Executed checkpoints

The complete backend invocation at source commit `14a24f91e1b0d4dc18c098a93d199e1609b1d440` passed **163 unit and 74 integration cases (237 total)** with zero failures, errors or skips. Maven verify finished **2026-10-27 12:18:30 UTC (17:48:30 Asia/Colombo)**, elapsed 10:41. [Sanitized suite counters](group-10-regression.json) identify every suite. Original XML and local diagnostics are preserved privately. The 294.582-second ReadApi suite completed successfully; this elapsed duration is neither endpoint latency nor evidence of an established cause for its long duration.

A subsequent focused invocation passed the **one newly added ConnectionOutageIT** and one overlapping FhirRuntimeTest, zero failures, errors or skips. Maven verify finished **12:32:52 UTC (18:02:52 Asia/Colombo)**, elapsed 03:24; the outage suite itself took 77.848 seconds. [Separate counters and source hash](group-10-connection-outage.json) preserve this scope. This is 237 cases in one complete run plus an additional integration case run separately, not a single full 238-case rerun. Eight client model cases and actual Codex browser checks are [separate evidence](group-7-client.md). Earlier checkpoint counts overlap and are not added together.

## Coverage of the four tasks

| Task | Actual evidence |
|---|---|
| 82 — ingestion and retention | IngestionIT's 23 cases cover structural/unit/reference validation, all invalid entry indices, sibling rejection and whole-Bundle rollback; exact-body replay and changed-body conflict; actor/role scope and roster recheck; forward UUID resolution; real concurrent PostgreSQL contention/retry; retained original/head-linked corrections and competing corrections. [Group 6](group-6-dataset.md) independently records actual repeated and invalid HTTP Bundles and retained original/correction retrieval in the assignment cohort. |
| 83 — access and confidentiality | ReadApiIT covers stored entitlements on list/detail/trend, all declared roles and unrelated actors, malformed/duplicate/mismatched declarations, clinician encounter membership, all three protected classes and age boundaries at 12/18, visible-only totals/pages and bounded trends. AccessLogIT covers the separately adopted self-PATIENT/STAFF log policy. ReadSnapshotIT proves the adopted before/after revocation snapshot behavior. [Group 5](group-5-reads-and-workflow.md) and [Group 6](group-6-dataset.md) preserve earlier actual cross-route and cohort checks, including individual revocation with another proxy retained. |
| 84 — audit and failures | FoundationIT, ReadApiIT and AccessLogIT prove same-transaction content/audit coupling, committed denials, insert/deferred-commit failure withholding and recovery. WorkflowIT proves mutation rollback on audit faults; IngestionIT proves no clinical effect/receipt on audit failure and same-key recovery. Validator availability and bounded explicit-503 retry tests cover dependency failure. The added connection-provider case covers all five required HTTP routes during failed connection acquisition and after restoration, as scoped below. |
| 85 — concealment and clinical boundaries | ReadApiIT asserts identical hidden/missing status/body and no correction/provenance leaks, as well as invisible additions not changing totals. Group5TimingIT records descriptive distributions below. ClinicalRulesTest covers every supplied range branch and exact endpoints, no-range states, effective-time context, adult boundaries and leap-day policy. Independent EgfrOracleTest, equivalent-unit checks, HistoricalContextTest, BmiVisibilityTest and actual read integration cover the calculation and missing/hidden input cases. BP components retain independent ranges/flags and scalar trends. [Group 3](group-3-clinical.md) records oracle provenance/tolerances; Group 6 checks the required synthetic cases through ingestion/read paths. |

These checks cover the adopted assessment contract; they are not a complete Cartesian product, clinical validation or security certification. The task-first failed FHIR transaction response and task/LOINC mapping conflict remain expressly disclosed decisions, not standards-conformance claims.

## Connection-provider fault and recovery

ConnectionOutageIT uses the actual loopback HTTP server, restricted serving DataSource and real designated synthetic PostgreSQL fixture. A scoped spy temporarily makes serving `getConnection()` throw a fixed synthetic SQLTransientConnectionException; a separate real owner connection verifies state. Each valid POST, results, detail, trend and permitted access-log request demonstrably reaches that failing provider and returns safe no-store 503 without clinical values, identities, input, SQL or private diagnostic text. Global patient, receipt and audit counts remain unchanged.

After restoration in bounded finally cleanup, all four GET routes return their real fixture content and four committed attributable ALLOWED events. The original POST body/key succeeds once with one patient and exact-hash receipt; replay returns the same body/ID and creates no duplicate clinical effect. Captured ordinary output excludes the synthetic diagnostic marker, identifiers, key, payload and measurement. The test uses a guarded atlas_test database and finite HTTP/JUnit/query budgets. Readable release rows are fixture state, not evidence of a new clinical release workflow.

This is a **controlled connection-provider injection**, not an executed stopped-cluster, network-partition, failover or disaster-recovery drill. The shared candidate application/database were not stopped. An action cannot commit its own audit to an unavailable database; the implemented response is to withhold content and avoid a completed idempotency receipt. Production independent failure telemetry and operational recovery still require design and verification.

## Hidden versus missing timing

The current artifact was produced at **2026-10-27T12:11:39.319467900Z**. Ten warmup pairs and 100 measured pairs alternate hidden and absent detail requests through one reused loopback HTTP client. All 220 requests assert equal 404 status/body and 220 committed DENIED audit events. This small fixed atlas_test view uses a known active age-16 proxy, an ordinary visible result and a released MHEV-protected result; absent and hidden UUIDs have equal length. It is not the 10,000-source assignment cohort or a production benchmark. Java 21.0.12.1+1 and PostgreSQL 16.15 were used. Timing includes complete HTTP body receipt and committed auditing.

| Statistic (milliseconds) | Hidden | Missing |
|---|---:|---:|
| Median | 22.62955 | 20.341701 |
| Nearest-rank p95 | 49.8372 | 44.314899 |
| Mean | 26.01149498 | 23.92337911 |

Paired signed hidden-minus-missing median was 0.9538 ms, mean 2.08811587 ms, range -45.108001 to 58.2935 ms. Load was uncontrolled; no cause for tails or this difference was established. These finite descriptive samples do not establish constant time, calibrated indistinguishability or absence of a practical classifier. No arbitrary acceptable margin or artificial delay was introduced. Group 5's earlier numbers are a different historical sample. Task 76's target and larger-cohort measurement remain pending.

## Original failure and correction

The initial complete invocation passed 163 unit cases but failed one of 74 integration cases: a two-second concurrency observation did not see both expected advisory waiters. Original reports were preserved. Source inspection established that its observation window included real prevalidation and its SQL counted unscoped waiters. This supports a harness correction, not a proven sole cause or production idempotency defect.

The [scoped test correction](../ai/outputs/group-10-concurrency-audit.md) synchronizes completed real validation outside transactions, records the two actual Repeatable Read transaction PIDs, observes only their exact real lock object and retains rollback/retry, identical response, one receipt/clinical effect and two audit assertions. Its focused 6-unit/23-integration recheck passed, followed by the complete 237-case PASS above. Production transaction/lock limits and adopted policy were unchanged. AI authorship and root execution are recorded in AI_WORKING_LOG G10-001/G10-002.
