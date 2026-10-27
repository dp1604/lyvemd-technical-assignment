# Measured local read performance

The five representative read cases met the **p95 ≤500 ms per case** target on the actual synthetic cohort: **100 patients, 106 encounters and 10,000 stored source Observations**. All 551 requests returned 200 with the expected basic response contracts; audits increased by exactly 551. This is a warmed, serial, loopback assessment result, not a production SLO or a capacity test.

The [target and fixed method](PERFORMANCE_PLAN.md) were declared at 2026-10-27T12:52:12.6873129Z and committed as `3449416b974c4cf1999216e741bd3abf19d900fa` before the measurement. The brief supplies no numerical performance target; 500 ms was chosen for a responsive local read including interpretation and committed auditing. Earlier smaller-view timings were known and disclosed before selection. [Curated machine-readable evidence](evidence/group-11-performance.json) records conditions, counts, results and query-plan observations.

## Workload and result

One reusable HTTP keep-alive client issued one request at a time. Each case received 10 warmups followed by 100 measured requests: 50 warmups and 500 measured calls in total. One preceding authorized list call discovered a real mapped BP detail ID; this was excluded from latency samples and included in audit accounting. Case order was fixed as shown below. There were no retries, discarded outliers or unsuccessful responses.

Times are milliseconds, from immediately before HTTP dispatch through receipt of the complete body. They include server authorization, data reads/calculation, required audit commit, serialization and loopback transfer; JSON assertions follow the timer. p95/p99 use nearest rank; the median averages the central pair for 100 samples.

| Case and endpoint | Measured calls | p50 | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Mixed p010 history: `GET /patients/{id}/results?offset=0&limit=50` |100|34.5055|57.7036|152.3717|631.5012|
| p010 source BP: `GET /patients/{id}/results/{observationId}` |100|21.97615|36.3106|62.2656|122.3204|
| p006 full 94-point sodium history: `GET /patients/{id}/trend?code=2951-2` |100|19.76055|40.6085|140.2020|761.7399|
| p010 calculated eGFR: `GET /patients/{id}/trend?code=98979-8` |100|18.78495|27.0271|31.8438|38.8710|
| p000 permitted STAFF log: `GET /patients/{id}/access-log?offset=0&limit=50` |100|20.99725|48.4941|72.5437|81.9538|

Every case passes its p95 target. The list 631.5012 ms and sodium 761.7399 ms maxima remain in the samples and exceed 500 ms; the target is not an all-request deadline. Their causes are not established. The cases cover all four required GET routes plus source/component, derived, long-history and audit shapes. They are representative selections, not a verified worst case. Logical recipe aliases above are not operational patient identifiers.

Measurement ran 2026-10-27T12:55:39.819Z–12:55:57.466Z. The global inventory remained 100 patients/106 encounters/10,000 sources/10,000 reviews/134 receipts. Audit rows grew 54,326→54,877, exactly 551; no clinical source was added or changed by the probe. Each successful read is audited, so log totals may grow during the workload.

## Conditions and query evidence

The existing application used Java 21.0.12.1+1, PostgreSQL 16.15, repeatable-read actions and a connection pool with maximum 8/minimum 1. Client: Node 24.22.1, Windows 10.0.19045 x64, AMD A9-9425 RADEON R5, 2 logical CPUs and 7,935,598,592 bytes of memory. HTTP was loopback without TLS. No other controlled database workload ran concurrently; unrelated host load was uncontrolled. The warm existing application excludes startup/cold-cache behavior. The recorded harness SHA-256 is `030bd23cbec265b56cb6e93d3a9725c4834d40fbbf1d3ccb97edf66dd1e9c22d`; the serving JAR SHA-256 is `6a4571ed57b1bca44759fad1d96fa4373e29aa035bf77c06bace1cb7ce1d3312`.

After the HTTP run, source-matched bound SQL was inspected with **EXPLAIN FORMAT JSON without ANALYZE**, in read-only transactions. Six plans cover assigned-clinician access and clinical views for p010/p006, plus p000 log count/page. The final capture at 13:01:33.0183935Z–13:01:36.4454026Z also recorded 14 actual index definitions from schema `atlas`.

| Query shape | Observed planner choices |
| --- | --- |
| Patient access | `patient_pkey` Index Scan plus an encounter Seq Scan; estimated patient output 1 and encounter scan 52 rows. |
| Full authorized source history, p010/p006 | Bitmap access through `observation_exact_trend_idx`, review Seq Scan/right hash join and final exact-time sort; estimated output 113/96, review scan 10,039. |
| Patient-scoped log count | Aggregate over `access_log_patient_time_idx` Index Only Scan; estimated scan 51. |
| Patient-scoped log first 50 | Bitmap access through `access_log_patient_time_idx`, sort then limit; estimated pre-limit 51/output 50. |

These are **estimates and planner cost units, not executed row counts or SQL timings**. A sequential scan can be a chosen small-cohort plan even when an index exists; the capture does not justify forcing a scan/index or assigning a cause to latency tails. Fresh prepared statements used `plan_cache_mode=auto`; a warmed JDBC custom/generic plan may differ.

The current list/detail/trend implementation obtains the authorized patient's full source history before Java ancestry filtering, derived calculations, windowing and paging. Response pagination therefore does not bound per-patient computation. The 10k cohort result does not prove behavior for very large single-patient histories. Production work should measure realistic history distributions and concurrent traffic, inspect executed plans through a reviewed audited diagnostic path, and evaluate projections/materialized views or bounded query changes while preserving visibility, correction ancestry, exact time, provenance and audit semantics. The [architecture note](../SPECIFICATION_AND_ARCHITECTURE.md) labels 9-million/90-million-row planning as forecasts.

## Reproduce locally

Use the [dataset prerequisites/command](DATASET.md) and [start instructions](../README.md#build-and-start). Load the fixed recipe, then keep the normal restricted-runtime app listening on loopback 18081. Finish other loads, migrations, integration suites or restore work before measuring. Do not run them concurrently on this database.

From the repository root, with Node 24 available, use a new ignored output name for each run:

```powershell
node ./scripts/measure-read-performance.mjs ./work/dataset/v1/load-manifest.json http://127.0.0.1:18081/ ./work/performance/read-run.json
```

The command checks the manifest/configuration and prior scenario PASS, creates its declaration before any HTTP request, discovers the BP ID through an authorized list, and writes samples/summary without response bodies or IDs. It refuses to overwrite evidence. Limits: 15 seconds per request, 600 seconds overall and 4 MiB per response. A failure stops and remains a failed sample; no key, retry or target rotation is performed. Reruns add audit rows and therefore change log state.

Record current source/review/receipt/audit inventory before and after using an authorized local operator connection; reconcile the successful probe count 551 against audit growth and verify source inventory unchanged. The manifest alone is not current persisted-count proof. Retain tool/build hashes, wall time, environment and any surrounding operator actions. Curate sanitized summaries; keep raw operational artifacts ignored.

To reproduce the planner capture, set PG connection/authentication variables in the current process from the existing local credential mechanism, never literal passwords, command arguments or shell history. The script requires loopback, database `atlas`, PGPORT/PGUSER and a new output directory. With those environment variables already set:

```powershell
pwsh -NoProfile -File ./scripts/capture-read-plans.ps1 -RepositoryRoot . -ManifestPath ./work/dataset/v1/load-manifest.json -PsqlPath (Join-Path $env:ATLAS_PG_BIN 'psql.exe') -OutputDirectory ./work/performance/read-plans
```

Set `ATLAS_PG_BIN` to the installed PostgreSQL 16 bin directory first if using that form. Runtime or owner credentials remain in environment only; clear authentication variables after the child finishes. The tested provisioning configures the runtime role's database search path as `atlas,pg_catalog`; any other operator role must likewise resolve the intended schema. The capture extracts the actual repository SQL, binds actual manifest values, and executes only non-ANALYZE plans/catalog reads with statement 5s/lock 1s/process 15s bounds. Full bound SQL/plans remain private because planner conditions can contain IDs; only the sanitized shape/index/estimate summary is suitable for publication.

This evidence does not establish real-network/TLS/browser rendering latency, cold starts, concurrent or sustained load, failure availability, universal timing indistinguishability or production-scale throughput. No production benchmark or legal/clinical certification is claimed.
