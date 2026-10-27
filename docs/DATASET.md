# Deterministic synthetic dataset

The candidate approved100patients/about10,000sourceObservations in actual answer `01a17c85-0aa5-7591-8064-e5ba870532fc`. Version `atlas-g6-v1` fixes100patients,106encounters and10,000stored source Observations. BP components are part of one source Observation; derived BMI/eGFR rows are computed views, not additional stored sources. The rejected Bundle contributes no sources, and the replayed Bundle contributes one source only.

## Reproduce

From the repository root, with the same prerequisites as the start command:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1
```

The command prepares/builds/migrates the designated isolated development database, generates the inputs, starts its own loopback helper, ingests through real HTTP, executes actual clinician actions, registers the synthetic patient/proxy roster, verifies the secured reads and stops only its own HTTP helper. PostgreSQL and any separately running application are preserved. This is a local assessment operator command, not an exposed administrative endpoint.

Generated requests, assigned-ID manifest, operational logs and verification JSON remain under ignored `work/dataset/v1`. They are synthetic, but are still excluded from ordinary source publication. No downloaded tools, database credentials or original attachments are included. Curated measured evidence is recorded separately after execution.

## Recipe and scenarios

Seed20261027 and semantic as-of2026-10-27 are explicit. An explicitly defined integer generator, fixed ordering and logical UUID aliases reproduce the clinical input bytes. Dates, values, contexts and workflow choices do not depend on the wall clock. Fresh databases assign different real resource UUIDs and audit times; byte-identical database files are not promised. The loader obtains real IDs from actual transaction responses. On the same database, actor-scoped exact-body durable receipts return the same IDs.

| Case | Persona | Required effect |
|---|---|---|
| DS-01 | p000 | Standardized creatinine1mg/dL and88.4umol/L on distinct dates normalize equally and produce equal adult eGFR |
| DS-02 | p001,p002 | Age13/16 parent proxies see ordinary history; all three ABSL/SUBU/MHEV classes are hidden while self/entitled clinician retain access |
| DS-03 | p003 | Same female patient's Hb11.5 immediately before/after14 uses the effective-time age and changes NORMAL to LOW |
| DS-04 | p004 | Original K4 is actually reviewed/released before an appended amendment5.8; both versions remain readable and the trend uses the amendment |
| DS-05 | p005 | Weight74kg, no height anywhere: BMI is null with HEIGHT_UNAVAILABLE |
| DS-06 | p006 |94monthly sodium values span January2019–October2026 in ascending trend order |
| DS-07 | p000 | Actual identical standalone Bundle submitted twice; response bytes/IDs and stored visible total remain unchanged |
| DS-08 | p000 | A valid sibling and an invalid-unit entry are actually submitted together; indexed400/424 response and no added result |

Additional fixtures include an expired18-year proxy, one revoked and one retained proxy for the same child, an unreleased ordinary result, a reviewed critical result still held, and a separately attested/released critical result. Background personas p010–p099 contain9,885sources covering all source analytes, BP components, both creatinine units, explicit unknown clinical sex and nonstandardized-creatinine reasons. Background data never supplies a height to p005.

The two critical fixtures explicitly carry source HH interpretation; no numerical critical threshold is invented. Synthetic NONCRITICAL classifications and the critical completion attestation are deliberate demonstration actions, not real clinical validation or patient contact. Source results begin held, and the loader calls the same review/attest/release services as the local workflow command. It never writes release state directly.

Proxy access always uses the real request UTC date. The semantic as-of date does not freeze server authorization. Verification refuses to claim the teen scenarios after their12–17age window expires (the older teen turns18on2028-02-28). Historical Hb/eGFR interpretation remains tied to each observation time.

## Privilege, interruption and replay

The short-lived fixture phase accepts only the designated loopback PostgreSQL16 cluster, atlas/atlas_test and the non-superuser schema-owner role. It checks reserved roles, exact successful receipt actor/key/body hash/assigned Patient location and existing patient IDs before inserting synthetic PATIENT actors and proxy links. Existing roles and revoked links cannot be overwritten/reactivated. It writes no clinical, review, receipt or audit rows. No owner credentials are inherited by the HTTP server or the non-web workflow/verification contexts.

The recipe fingerprint is checked against an actual empty-Bundle durable receipt before loading. A different reserved recipe is rejected rather than appended silently. Unrelated database content is preserved and cohort counts are reported separately. Each successful patient receipt is saved atomically; a failed/interrupted run can be rerun with the unchanged recipe to recover IDs from server receipts and finish missing actions. Review/release repeats are attributable audited no-ops preserving first actors/times. Corrections are deferred until the original has genuinely completed release. Never release a known-wrong original merely to enable a correction.

The command has bounded preparation/build/start/load/verification phases, categorical progress and owned-process cleanup. It must fail visibly on a bad phase; it never resets a database, kills an unrelated listener or treats a partial manifest as completion. Performance targets and measurements on this exact dataset belong to the later performance-evidence group; the9-million-record production context remains separate.
