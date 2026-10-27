# Declared assessment read-performance target

Declared before the Group11 measurement. The task supplies no numerical target; this is an engineering target for the stated local assessment workload, not an employer-approved or production SLO.

**Target: nearest-rank p95 at most500milliseconds for each case**, with zero unsuccessful/invalid responses, at concurrency1. Time starts immediately before HTTP dispatch and ends after receiving the complete response body. The application's successful response boundary includes committed auditing. This targets a responsive interactive local read while leaving room for interpretation and audit work. It does not include browser rendering or real-network latency. Existing smaller-view timing checks were known before choosing this larger-cohort target; this is not a blinded experiment.

Measure the actual deterministic100-patient/106-encounter/10,000-source cohort (additional calculated views are not source Observations). Verify inventory and attributable audit growth before/after. Use the current reviewed application, restricted runtime role, a reused loopback HTTP client,10warmups and100measured requests per case, one request at a time, no retry or discarded outlier. Report p50/p95/p99/maximum and all statuses, with a fixed per-request timeout. A timeout/error counts as a failed target, not a removed sample.

Fixed cases are: mixed-history p010 results(limit50); a p010 BP source detail selected from an authorized discovery list; p006 sodium's full94-point trend; p010 eGFR trend; and p000's permitted STAFF access log(limit50). These cover all four required GET routes and source/component/derived/history/log shapes. They are representative, not a verified worst-case selection. There are550warmup/measured requests; any discovery request is counted separately in audit reconciliation. Generated operational IDs and raw samples remain ignored local artifacts.

Serialize this workload with other database work; no dataset load, integration suite, restore or clean-start runs concurrently. Record tools/OS/CPU/memory, source commit, runtime settings, wall time, cohort cardinality and other uncontrolled load limits. Inspect catalog indexes and EXPLAIN JSON without ANALYZE for actual bound query shapes; these are planner observations, not executed SQL timings. Do not bypass required clinical auditing to obtain a query benchmark.

Record failures and investigate a demonstrated problem without changing the declared target after seeing results. The sample does not establish behavior at9million or90million records, concurrent production traffic, cold caches, network failure or universal timing indistinguishability. Larger-scale architecture remains an explicitly labelled forecast.

Declared atUTC: 2026-10-27T12:52:12.6873129Z.
