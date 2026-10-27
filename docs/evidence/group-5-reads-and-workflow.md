Current contribution status: [subsequent candidate review](../CANDIDATE_REVIEW.md). The evidence below preserves its original checkpoint; later human review does not alter that historical execution or result.

# Group 5 — secured reads, workflow and audit evidence

## Actual scope and decisions

Implements original Tasks35–41,48,50,51; Task49 was already completed in Group4 and is retained/extended to secured derived detail. [Actual request](../ai/prompts/group-5-user-request.md), [adopted C1–C3](../decisions/group-5-clarifications.md), [read contract](../RESULT_READS.md), [visibility](../RESULT_VISIBILITY.md), [log policy](../ACCESS_LOG.md) and [local workflow](../WORKFLOW.md) define the assessment boundaries. No public workflow endpoint, production authentication, notifications, Careline write-back, real patient data or invented clinical thresholds were added.

All four GET paths share bounded test-header parsing, known roster verification, stored entitlements, consistent visibility and one read/audit Repeatable Read transaction. Body materialization and audit commit finish before HTTP delivery. Self/entitled clinician/active individual proxy rights are server-side; STAFF is log-only. UTC request age enforces12+ confidentiality for ABSL/SUBU/MHEV and proxy expiry at18. Known self PATIENT revocation retains its first actor/time and dedicated affected proxy attribution. The log response deliberately excludes its own subsequently committed read event.

Raw history remains retrievable, while trends select the caller's latest visible correction heads and label ineligible heads as gaps. Visible-only totals, IDs, metadata and derivations do not use hidden successors/inputs/ancestors. BMI uses the full visible history before display bounds; eGFR explicitly labels caller-visible inputs/source dates and preserves its equation link. Derived rows are views with inherited source restrictions, not independently classified/released source records.

## Executed automated checks

The complete run passed **204 cases:130 unit plus74 real PostgreSQL/HTTP/service integration cases**, zero failures/errors/skips. After the final eGFR notice correction and expanded timing probe, the affected focused run passed3 query unit plus18 read/timing integration cases,21 total, also zero failures/errors/skips. These are repeated affected cases, not225 distinct tests. The compiled product changes were confined to that display notice; no clinical equation or authority policy changed after the complete run.

| Suite | Cases | Relevant evidence |
|---|---:|---|
| Existing clinical/FHIR plus query/CLI units |130| Complete clinical table, source/context/unit/calculation/oracle checks, strict FHIR boundary, bounded audited-query parser and CLI/batch input validation |
| FoundationIT |8| Actual PG16/schema/constraints, restricted runtime, read/audit atomicity, safe failures |
| IngestionIT |23| Prior atomic ingest/replay/correction regressions plus actual POST correction forced to overlap first release |
| AccessLogIT |11| Role/header matrix, patient scope, tie order/count/page/current-event semantics, query denials, insert/deferred-commit failures and actual revoke-to-permitted-HTTP target projection |
| ReadApiIT |17| All clinical routes, visible-only list/count/page/derived identities, concealed input/ancestor/successor parity, correction history versus trends/gaps, BMI/eGFR/BP, exact nanosecond bounds,12/18/all protected classes, malformed/duplicate headers and audit failures |
| ReadSnapshotIT |2| Revocation before snapshot denies; controlled revocation after roster-established RR snapshot permits only the already-authorized in-flight action, following request denies |
| WorkflowIT |12| Actual review/critical attestation/separate release; status/role/entitlement/floor guards; preserved no-ops/conflicts, individual revoke, all mutation audit insert/commit rollbacks, least privilege and forced overlapping review/release/classification actions |
| Group5TimingIT |1| Identical hidden/absent404 status/body, durable denials and bounded paired descriptive timing |

The first full203-case attempt had one test-harness failure: an old correction test expected both calls to reach an Observation INSERT relation lock. The new predecessor review-row serialization correctly blocks a request earlier. Root moved the gate/blocker-chain evidence to that real shared lock and retained one-successor/outcome assertions; the final full run passed. Both run logs remain ignored locally. Direct owner review-state setup in read-only visibility tests is explicitly fixture state. WorkflowIT and the separate CLI checks use actual workflow actions, not fabricated released rows.

## Correction/release ordering

Appending a correction touches its predecessor's separate mutable review row before source INSERT. First release updates the same row. Stale overlapping RR updaters retry the whole action in a fresh transaction. An actual HTTP correction test pauses after its row touch/source write, proves first release has an ungranted PG lock behind it, then commits correction: release retries, sees the successor and returns409 with a durable denial, predecessor stays held. Source/audit UPDATE/DELETE grants remain forbidden. Three additional forced DB-contention tests cover identical reviews, competing classifications and repeated release; no duplicate transition overwrites first attribution.

## Actual non-web CLI smoke

Used only designated synthetic `atlas_test`, the current packaged JAR and runtime credentials without migration secrets. One actual batch completed six independent actions: noncritical review/release, critical review/affirmative attestation/release and individual proxy revocation. A second batch committed its first review, returned a safe unknown-actor404 on its second command, and left its third command untouched. Eight action audits persisted in total. Returned no fixture identity, clinical payload or stderr; successful process exit0, safe-failure exit2.

The Spring mode is NONE; process-owned listening-socket sampling observed no listener across14 samples in the success run and11 in the failure run. Actual durations48.448s/53.968s include local startup/monitor overhead and are not API latency claims. Single-action and bounded batch forms are documented in the workflow contract; batch does not promise all-or-nothing rollback of its already-committed prefix. The default wrapper targets atlas; the isolated smoke invoked the same JAR against atlas_test, not the pending assignment dataset.

## Hidden/missing timing investigation

Final probe: known active16-year-old proxy, one ordinary visible result, a released MHEV-protected detail versus an absent same-length UUID, same patient and reused loopback client.10 warmup pairs then100 measured pairs, alternating which target goes first. All220 requests returned the identical404 body and committed a DENIED event. Timing includes request/content/audit/commit/response. Raw paired latency samples (no fixture identities) remain in ignored `work/group5-timing.json`.

| Final100-pair measure | Hidden ms | Missing ms |
|---|---:|---:|
| Median |25.6689|24.83345|
| p95, nearest-rank |77.3406|85.7046|
| Mean |37.652164|38.385614|

Paired signed hidden-minus-missing median:0.29245ms; mean:-0.73345ms. Earlier30-pair runs had variable tails, so root expanded the paired investigation instead of silently accepting one p95 difference. In the final run the larger p95/max shifted to missing, and outliers occurred in both classes. This does not establish a stable directional separation in these measurements; it is not a calibrated adversarial classifier or universal indistinguishability proof. SQL binds the patient only and both nonmatching IDs traverse the same complete visible collection/audit/error path. No artificial delay/randomness was added to manufacture similar times.

System load was not controlled; scheduler/GC/cache/audit-flush causes were not instrumented and are hypotheses, not proven explanations for outliers. Full-patient history materialization has memory/CPU costs and SQL timeouts do not hard-bound Java work. Production-scale histories, deployment and universal timing/security guarantees are not claimed. Later dataset/performance/final acceptance remain their groups.

## Reproduction and limits

Run `pwsh -NoProfile -File ./scripts/verify.ps1` from the repository root with documented prerequisites. It preserves data and uses atlas_test only; no AttachmentA code is built/run/fixed. Current complete report includes204 cases. Runtime has no source Observation/audit UPDATE/DELETE or schema-creation privilege; V3 grants only separate workflow metadata/link-state columns. Test headers/local actor declarations remain impersonatable. Complete DB outage cannot audit into that same failed DB; actions fail closed with categorical operational signals. No candidate-run verification, real patient contact, clinical/legal/security certification or full assignment submission completion is inferred. Dataset/client/specification/static-review finalization and actual candidate walkthroughs remain open.
