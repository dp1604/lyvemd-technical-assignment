# Verification and remaining limits

The implementation was checked with automated backend tests, focused client tests, browser use, a deterministic dataset, local read measurements, a restore drill and a fresh-checkout run. The results below describe those checks and their conditions. Documentation edits do not constitute another application test run.

## Backend and clinical checks

A complete backend run passed **237 cases: 163 unit and 74 integration tests**, with no failures, errors or skipped cases. This includes real PostgreSQL and HTTP integration coverage for ingestion, replay, corrections, visibility, audited reads and workflow behavior. The earlier run had a failed concurrency-observation assertion. Its scoped test harness was corrected and the complete suite rerun; the original failure was retained rather than counted as a pass. See the [complete-run record](evidence/group-10-regression.json).

A later, separate connection-failure check passed **one new integration case**, plus one overlapping unit case. It forced connection acquisition to fail in the serving DataSource, exercised all five required routes and checked recovery. The independent owner connection remained available. This was not a stopped-cluster, network-partition or failover drill, and it is not presented as a new full 238-case run. See the [connection-failure result](evidence/group-10-connection-outage.json).

The eGFR checks use a separate numerical oracle and published examples, including unit equivalence, applicability and the conversion to the task's per-second display unit. The [clinical oracle record](evidence/clinical-oracle.md) identifies sources, methods and limits. Numerical agreement does not establish clinical approval, and the supplied potassium/LOINC conflict remains a synthetic-assessment convention requiring resolution before real patient use.

## Dataset and client

The deterministic recipe produced **100 patients, 106 encounters and 10,000 stored source Observations**. Clinician lists additionally contained 3,693 calculated or unavailable view rows; these are not stored source Observations. Verification exercised all eight required scenarios through real ingestion and secured reads: both creatinine units, protected adolescent encounters, historical haemoglobin across age 14, retained amendments, weight without height, multiyear history, exact-Bundle replay and invalid-entry rollback. The fresh-run [scenario record](evidence/group-12-clean-start.json) includes unchanged totals after replay and rejection.

Eight focused Node client-model cases passed. Browser checks covered served assets, result and trend rendering, source details, role restrictions, correction history, missing calculations, decimal preservation, pagination, date windows, keyboard navigation and clearing stale content after context changes. These checks were performed with AI assistance and are separate from my own walkthrough.

In my walkthrough, I observed the same haemoglobin value, 11.5 g/dL, change from Normal with range 11–14 on 30 June to Low with range 12–15.5 on 1 July. I checked weight 74 kg with unavailable BMI because height was missing, the matching trend table without numeric chart points, and visible focus with Tab and Enter on the trend controls. The sodium trend changed from 94 points to 10 after the stated 2026 date filter. STAFF could read the log but not clinical results; the assigned clinician could read the result but not the log. The latter result showed sodium 140 mmol/L, range 135–145, Normal.

I deferred the remaining guided client checks to prioritize delivery. In particular, I have not supplied a completed observation for the equivalent-creatinine/eGFR detail and source-link focus-return exercise. Automated browser checks do not replace that remaining personal check. No accessibility certification or universal browser compatibility is claimed.

## Performance

The target, selected before measurement, was **p95 no greater than 500 ms for each representative read case**. Five cases covered all four GET routes on the actual 100-patient, 10,000-source dataset. Each had 10 warmups and 100 measured calls, plus one discovery call: **551 HTTP 200 responses and exactly 551 added audit events**, with source inventory unchanged.

Measured p95 ranged from **27.0271 to 57.7036 ms**. The list and sodium-trend maxima were **631.5012 and 761.7399 ms**; these samples were retained. The test used warm processes, serial loopback requests and no concurrent controlled workload. It does not prove a production SLO or capacity at nine million records. The [performance report](PERFORMANCE.md) gives all cases, percentiles, conditions and reproduction instructions; the [measurement record](evidence/group-11-performance.json) preserves the structured results.

The implementation loads an authorized patient's source history before Java filtering, calculation and paging. Response pagination therefore does not bound per-patient work. Larger histories and concurrent traffic need further measurement and query/projection work.

## Restore and clean startup

The restore drill used a PostgreSQL custom-format backup and a new isolated restore database. All **10 tables**, content, schema, grants and sequences matched before four attributable allowed/denied HTTP checks. The complete drill took **103.386 seconds**, including a **5.786-second** pg_restore phase. Existing roles were prerequisites. The [restore record](evidence/group-12-restore.json) is evidence for this local drill, not cluster-global/key recovery or an approved production recovery objective.

Both documented public commands were exercised from a fresh, full-history checkout and a new empty database cluster, using already installed external tools and a warm Maven cache. The start command reached service readiness; the dataset command exited successfully, verified the cohort and passed its scenario checks. This is a fresh-source/database check, not a fresh-machine or cold-dependency test.

The enclosing clean-start wrapper then failed while changing Timeout on an already-used HttpClient, before an additional foreground GET. That supplemental GET and its audit assertion were not performed. Separate follow-up Git checks passed, but no overall wrapper PASS is claimed. The [clean-start record](evidence/group-12-clean-start.json) preserves both the successful commands and the failed wrapper boundary.

## Security and review qualifications

Hidden and missing lookups have matching tested status/body behavior, and authorization, visible counts and metadata filtering are enforced on the server. Finite timing samples were unequal, however; **timing indistinguishability under house rule 5/R2 remains unestablished**. This requirement is not marked fully satisfied.

Authorized reads are withheld if their required audit insert or commit fails. A completely unavailable database cannot record its own failed request; production monitoring needs an independent operational channel. Proxy revocation takes effect for new transaction snapshots and cannot withdraw an already authorized in-flight response.

Test actor headers are impersonatable assessment inputs checked against the stored roster, not authentication. Database operators remain a trusted boundary; no database row-level-security policy is claimed. The implementation is an explicitly bounded ingestion/read adapter, not a general-purpose FHIR server or a production clinical deployment.

My later 147-item review response recorded 100 independent-review Yes, 45 Partial and two No selections; execution selections were 25 Yes, 103 Partial and 19 No. Attachment A execution selections mean static inspection/manual reasoning. That was a reported review snapshot, not a replacement for automated execution evidence. I subsequently confirmed thorough review of the Attachment A findings and take responsibility for the final recommendations. Finding origin and review method remain disclosed in the review and AI working log. Attachment A was never fixed, compiled, built or run. Assignment submission is a separate manual action and is not established by this verification record.
