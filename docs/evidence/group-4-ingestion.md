# Group4 — ingestion and retained correction history

Group4 Tasks42–47 are complete for the declared assessment profile. Task49 was brought forward from Group5 because Task46 explicitly requires both retained versions retrievable with correctionOf. This is an authorized dependency, not a claim that the rest of Group5 or the submission is complete. The other three GET endpoints, actual clinician review/release commands, deterministic assignment dataset, UI, performance/timing/restore/final checks and candidate walkthrough remain open.

## Implemented behavior and decisions

- Strict bounded JSON R4 transaction adapter for POST Patient/Encounter/Observation. Every entry and relationship is validated; unsupported operations/semantics reject. Complete local UUID/relative alias maps support forward references, with fresh server IDs. Stored clinical references require the known ingester's existing encounter entitlement; no in-Bundle encounter can bootstrap access to an existing unauthorized patient.
- Validate/plan before clinical writes; one Repeatable Read action commits clinical rows, held review rows, patient-scoped categorical audits and immutable completed receipt together. Internal stored clinical reads are included. Expected invalid outcomes commit denial/audit/receipt only; audit/validator/infrastructure faults withhold output and do not poison a key.
- Exact-byte SHA-256 actor+role+key replay, byte-identical saved status/body/IDs,409 changed-body conflict, roster recheck before replay, immutable success/deterministic400 receipts and bounded fresh-transaction retries after stale-snapshot/unique conflicts. Two requests were explicitly blocked in PostgreSQL before releasing the gate; one effect and identical responses were verified. Correction races similarly forced both plans to reach INSERT before commit.
- Append-only head-linked corrections retain original values. Same-patient/analyte/encounter/exact effective Instant, no self/cycle/branch, bundle-local chains and unique successor enforcement. No observation/audit UPDATE/DELETE grant was added. Supported nanosecond effective time is preserved across PostgreSQL's microsecond storage with a residual column; historical clinical context/source units/critical signals remain traceable.
- Secured detail returns value, unit, range, textual flag/reason, correctionOf, visible correction metadata and BP components. Both retained versions are available to an entitled clinician. Patient/proxy views require release/critical completion and complete visible ancestry; hidden successors do not alter original metadata, and hidden ancestors conceal descendants. Proxy links are individual/revocable; request-UTC exact12/18 boundaries, missing/future DOB, source-critical inconsistency and STAFF clinical denial are checked.

The candidate actually adopted [D-11 HTTPS identifiers and D-12 POST reference behavior](../decisions/group-4-clarifications.md). The original A/B packages and exact access-rule instruction are unchanged. See [FHIR contract](../FHIR_INGESTION.md), [read boundary](../RESULT_VISIBILITY.md), [clinical rules](../CLINICAL_RULES.md) and [AI audit reconciliation](../ai/outputs/group-4-audit-summary.md).

**Intentional standard departure:** the brief requests invalid entries in a transaction-response; normative R4 prescribes a single OperationOutcome on failed transactions. The adopted HTTP400 transaction-response identifies invalid indexes and gives valid siblings424 without committing them. Malformed transport input uses a safe top-level OperationOutcome. The create-only profile, context-changing correction rejection and example canonical namespace are disclosed limitations, not a full FHIR-server/conformance claim.

## Actual verification

Final command: `pwsh -NoProfile -File ./scripts/verify.ps1`, using the real PostgreSQL16.15 designated synthetic atlas_test database and loopback HTTP. Test business clock is explicitly2026-10-27UTC; operational audit timestamps remain actual database events. Source fixtures vary IDs to preserve repeated test runs; they are not the pending deterministic assignment dataset.

| Suite | Tests | Scope |
|---|---:|---|
| ClinicalRulesTest |73|All table ranges/units/BP/input branches and clinical boundaries|
| BmiVisibilityTest |13|Caller-visible inputs, corrections/absence, missing/invalid inputs, long ancestry|
| EgfrOracleTest |26|Independent numeric references, published examples, applicability and units|
| HistoricalContextTest |3|Explicit historical sex intervals/context|
| FhirRuntimeTest |1|Actual pinned R4 parser/validator runtime|
| FhirProfileTest |5|Local definitions/valid-invalid extension instances, strict JSON/profile representations|
| InputBoundaryTest |3|Headers/key/timestamp precision|
| FoundationIT |8|Real schema/model, grants, transaction/audit rollback and safe HTTP errors|
| IngestionIT |22|Actual POST/detail, refs, full invalid-entry rollback, replay/conflicts/forced concurrency/corrections/holds/roles/age/revocation/hidden histories/audit+validator faults/transport|
| **Total** |**154**|**Zero failures, errors or skips**|

Final complete build finished11:58:24 Asia/Colombo on27October2026, duration3m31s. This is build/test duration, not measured endpoint latency or future completion promise. An earlier complete154-test build and focused22-HTTP rerun also passed before the final raw representation tightening. Final source includes assertions for canonical creatinine range/flag, BP component ranges/flags and null height value/range with no applicable flag; no zero is fabricated. Required assignment dataset and latency measurements remain separate groups.

Failed probes are preserved honestly in ignored logs: full StructureDefinition self-validation attempted a public FHIR core-package download then failed on a transitive class; root prohibited managed HTTP and replaced that test with strict artifact checks plus local valid/invalid instance checks. A fresh workspace JVM home had no FHIR package cache afterward. An initial contention-observation assertion failed (14/15 tests); stronger explicit lock gates subsequently proved overlapping requests with pg_locks. A test-source var declaration compile error was fixed before final verification. No provided AttachmentA code was executed or changed.

The final logs showed only categorical intentional fault signals; targeted checks found no injected private validator/audit messages, clinical UUID references or SQL text. Library payload/diagnostic logging is disabled. This is limited test evidence, not a guarantee about every possible log or a security certification.

## Defend the implementation

1. The replay key belongs to an actor and exact request bytes; DB uniqueness is the durable guard. An advisory lock alone is insufficient because Repeatable Read snapshots can be old after waiting; retry the whole transaction with a new snapshot.
2. Validate all entries first so an invalid child cannot leave valid sibling writes. Treat expected invalidity as a returned outcome so the denial/receipt can commit; infrastructure failures roll back and remain retryable.
3. Preserve an amendment as a fresh row and held review state, never an UPDATE. Only a chain head can be corrected; retrieve authorized originals and corrections separately. Correction metadata is derived from the caller's complete visible chain.
4. Unknown or unauthorized stored references receive the same categorical indexed400 for POST. GET hidden/missing details share the same patient-view query, denial audit and404 body; no submitted references/internal validation text are echoed.
5. A final source status is not clinician release. Original source criticality cannot be downgraded through a conflicting noncritical review. Release-state fixtures exercise reads but are not the pending real workflow.

## Practical limits and next work

Test headers are impersonatable; no production identity assurance is claimed. All data is synthetic. No actual vendor/upstream format, assay or clinical suitability was verified. Managed network prohibition is a library boundary, not OS egress proof; dependency retrieval is a separate build step. No complete implementation-guide validation or clinical/security/legal certification is claimed.

The shared read view is fully materialized per eligible patient with a patient-first index and linear ancestry/successor processing. It has a memory/CPU cost; SQL/transaction timeouts do not hard-bound arbitrary Java work. No history is silently truncated. No9-million/90-million-row result or constant wall-clock timing proof is claimed. Group5/final regression must cover all remaining routes, paging/totals, derived outputs, revocation snapshots and statistical timing. Production scaling, restore and equation governance remain note/evidence obligations.

Source increments privately published: fdad0b5 (boundaries/local definitions/schema),7ccedbe (atomic ingestion/replay/corrections),816331a (secured detail/strict representation). This final documentation/state increment and exact-head history recovery complete the publication milestone separately. Full source/history/private-state checks remain ongoing through delivery.
