# Grouped execution plan

This grouping baseline assigned the87 items pending after Tasks1–9 exactly once to14 primary groups. Current completion/selection is authoritative in docs/EXECUTION_STATE.json; Groups 1–6 are now complete after their recorded gates; Group 7 is next, with actual candidate verification in Task 86 still open. This changes execution granularity; original task numbers, requirements and completion gates remain intact.

Main implementation order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 10 → 11 → 12 → 13 → 14. Start Groups 8 and 9 after Group 1 and run them alongside implementation; finish both before Group 13. Independent API/evidence work can continue if a guided check in Group 7 is awaiting the candidate.

## 1. Decisions and contract

Original tasks: **10–17**. Start: first decision group; completed status is tracked in the state file.

Create the register; obtain and record the required FHIR, replay/correction, identity, demographic/time, release/critical and audit decisions.

Completion gate: Candidate decisions required before affected code. An unanswered decision remains open.

## 2. Service and database foundation

Original tasks: **33–34**. Start: After 1.

Runnable Spring Boot foundation, migrations and source-model constraints; begin the build/start command.

Completion gate: Build/start and schema checks pass; added constraints are documented as choices.

## 3. Clinical rules and calculation verification

Original tasks: **52–62**. Start: After 1–2.

Canonical units, BP components, all table ranges/flags, BMI/eGFR, cannot-compute reasons and independent numerical checks.

Completion gate: Every clinical row and boundary has evidence; explain the calculations and limits to the candidate.

## 4. FHIR ingestion and retained history

Original tasks: **42–47**. Start: After 2–3.

Validate/atomically ingest transaction Bundles, resolve UUID references, enforce idempotency, append corrections and return decided responses.

Completion gate: Focused own-build tests cover successful/invalid/replayed/concurrent/reference/amendment cases.

## 5. Secure reads and transactional auditing

Original tasks: **35–41, 48–51**. Start: After 2–4.

Implement actor validation, entitlements/proxies/confidentiality, all four GET routes, consistent visibility/counts and successful/denied/failing audit behavior.

Completion gate: All read paths use the controls; no content after audit failure. Use internal checkpoints for access rules, routes and failure tests.

## 6. Deterministic dataset and eight scenarios

Original tasks: **66–74**. Start: After 4–5.

Script reproducible data and all eight cases, including actual duplicate and invalid Bundle submissions; create/load command implementation.

Completion gate: Reproduce scenarios and assert their effects; manual-looking rows do not substitute for actual submissions.

## 7. Client and guided client verification

Original tasks: **63–65, 86**. Start: After 5–6.

Results with range/flag, trend chart, access log, non-color cues and distinct no-applicable state; keyboard/client walkthrough.

Completion gate: Actual guided checks recorded. If candidate verification is pending, keep 86 open and continue independent API/evidence work.

## 8. Specification and architecture — parallel lane

Original tasks: **18–32**. Start: Start after 1; finish before 13.

Assign document/review ownership; draft both note sections, Dana conflicts/vendor/mobile recommendation, estimates/acceptance criteria and production architecture/clinical governance/operations.

Completion gate: Draft alongside 2–12; finish against actual decisions, implementation and evidence. Never describe a projection as a measurement.

## 9. Static PR review — parallel lane

Original tasks: **77–81**. Start: Start after 1; finish before 13.

Review all eight supplied files; verify printed locations, consequences/remedies/severity/origin; guided review walkthrough.

Completion gate: Static only: do not fix, build or run Attachment A. Clinical-source checks and actual candidate walkthrough completed.

## 10. Integrated correctness and security regression

Original tasks: **82–85**. Start: After 4–6; alongside 7 if needed.

Run integrated ingestion/retention, access, audit/failure, hidden/missing and clinical-boundary checks on our build.

Completion gate: Required failures investigated and resolved; an unmet requirement keeps its task open. Reuse meaningful earlier evidence and rerun affected integration checks.

## 11. Dataset and performance evidence

Original tasks: **75–76**. Start: After 6 and correctness gate 10.

Record exact dataset counts/reproduction, chosen target, conditions and measured endpoint results/query evidence.

Completion gate: Use one controlled database workload at a time; report actual dataset/latencies and distinguish production projections.

## 12. Restore, run instructions and clean startup

Original tasks: **87–89**. Start: After 7, 10–11.

Small isolated restore comparison; final prerequisites/two commands/sources; clean-checkout and empty-database verification.

Completion gate: Use designated synthetic databases; prove commands work and protect the working dataset. Integrate fixes and rerun affected checks.

## 13. Reconciliation, guided defense and package freeze

Original tasks: **90–93**. Start: After all 1–12 completion gates.

Align matrix/note/build/review/evidence; finalize AI log and real history; guided interview walkthrough; inspect/freeze all four components.

Completion gate: Actual user walkthrough required; package/history/privacy checks completed. A later fix reopens affected checks and requires a new package/checksum.

## 14. Delivery

Original tasks: **94–96**. Start: After 13.

Verify actual recipient/method/access, prepare exact submission reply, assist candidate submission and verify correct version delivered.

Completion gate: Candidate sends. No inferred address, public access, collaborator invitation or automatic send; confirm delivery before 22:00 Asia/Colombo.

## Interruption, stall and recovery precautions

1. Keep the original 96-item checklist and its evidence requirements. Each remaining item has one primary group; tests and explanations may support several items. Mark only actually completed items. At each group response end, announce the completed original task numbers and any still-open items.
2. Work in small resumable slices inside larger groups, saving a short recovery note after each coherent slice (target 20–30 minutes, sooner before risky or long operations). Record current group/task IDs, last good commit, changed files, actual decisions, tests/results, unresolved failures and one exact next action. Record owned services/ports/process or session IDs without secrets.
3. Use one implementation/integration writer. Limit parallel work to the main implementation plus two narrowly scoped document/review workers. Give each worker separate scratch output paths under ignored work; only the integrator changes shared canonical files, stages, commits or pushes. A slow worker returns partial findings and a resume note; progress on independent work continues.
4. Give long commands operation-appropriate timeouts. Run long builds/downloads in resumable sessions, poll them, keep logs locally and return compact relevant output. Provide a meaningful progress update at least every 60 seconds while working. Silence or a timeout triggers inspection of the process/log/network/state before any retry; do not start duplicate jobs blindly.
5. After two identical failures with no progress, stop the retry loop, diagnose the cause and try a targeted alternative. Ask a concise question if information or a candidate decision is genuinely required; continue independent work. Elapsed time never counts as approval.
6. Serialize migrations, dataset loads, restore checks and benchmarks on the same database. Use separate designated synthetic databases for tests/restore. Keep app/database state recorded so restart does not duplicate loads, collide on ports or overwrite data. Use synthetic inputs; keep secrets and clinical payloads out of ordinary logs and Git.
7. Commit inspected coherent increments and maintain matrix/AI disclosure continuously. Push reviewed changes and run full history recovery at substantive group milestones and final delivery, rather than once per tiny checklist item. The checkpoint script requires a clean tree: collect scratch-worker handoffs and integrate coherent changes before running it; never discard work to make it pass.
8. On interruption, crash or context loss, inspect the recovery note, Git status/diff, running services, migration/data state and last test evidence before resuming. Preserve partial edits; do not re-download/recreate/reset everything automatically. No workflow can guarantee zero crashes; these measures bound loss and make restart explicit.
9. Candidate decisions, guided verification and final sending are real gates. Bundle related questions with clear recommendations; hold affected work until the answer arrives. Keep pending human-check items open while independent lanes proceed. Static review remains static; preserve all assignment/security/privacy duties.
10. Reforecast after the first working ingestion/read slices (Groups 4–5), using actual coding/testing throughput. These groups do not establish a new promise that all remaining work fits 12 hours. Protect final verification, packaging and delivery time.

Coverage check: 87 assigned items, 87 unique items, no omissions or duplicates. Current checklist/state mark56 tasks complete after Groups1–6. On the next-group request select Group7 (63–65 and actual candidate gate86).

Group4 completion dependency: originalTask46 required both retained versions retrievable with correctionOf, so Task49 was implemented and verified early. It remains assigned once toGroup5; currentstate/checklist mark it complete. Group5 subsequently completed its remaining ten tasks and corresponding workflow/cross-route checks; Task49 is not counted again. No other original group allocation is changed.
