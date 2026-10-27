# Deterministic synthetic dataset

**Assessment convention:** the supplied table calls code `2947-0` potassium, while official LOINC defines it as sodium in blood. Under the candidate's [D-17 decision](DECISIONS.md), this adapter retains that mapping only for the synthetic assessment. It must not process real clinical feeds unchanged. Confirmed specimen, verified terminology mapping and clinical-owner approval are prerequisites for production use. This conflict does not establish the cause of the separate validation failure described below.

Version `atlas-g6-v1` defines **100 patients, 106 encounters and 10,000 stored source Observations**. The complete load, all eight secured end-to-end scenario checks and a separate fresh-local-journal replay passed. See the execution evidence for actual counts, failures investigated, preservation checks and limits. BP components belong to one source Observation. Calculated BMI/eGFR views are additional response rows, not stored source Observations. The deliberately rejected Bundle contributes no sources, and submitting the replay case twice contributes one source.

## Create and load

From the repository root, with the same PowerShell 7, Java 21+, Maven and PostgreSQL 16 prerequisites as the start command:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1
```

On success, this single command prepares the designated local assessment database, builds the application, applies migrations, generates the inputs, starts its own loopback HTTP helper, submits the clinical Bundles, executes the actual clinician workflow, registers patient/proxy fixtures and verifies secured reads. It then stops its own helper. PostgreSQL, existing records and any separately running application are preserved. The command is a local assessment operator tool; it adds no administrative HTTP endpoint or production identity-provisioning feature.

The helper uses port `18091` and refuses an existing listener. The public load command targets the assessment database `atlas`; automated verification remains separate in `atlas_test`. It reports stage progress and fails visibly if a stage does not finish successfully. A partial manifest or a progress counter is not a completion result. See [the current execution evidence](VERIFICATION.md) for what has actually passed.

Generated requests, the assigned-ID manifest, the request-key plan and operational artifacts remain under ignored `work/dataset/v1`. Generator source and curated evidence are delivered; local tools, credentials and generated operational files are excluded from source publication. No Node installation or external generation package is required.

## Fixed recipe and determinism

| Parameter | Value |
|---|---|
| Version | `atlas-g6-v1` |
| Seed | `20261027` |
| Semantic as-of date | `2026-10-27` |
| Configured patients / encounters / source Observations | `100 / 106 / 10000` |
| Recipe SHA-256 | `f0a5f4cbbfd88d7f54dd27713862beb827e4f531bb8b5e40d74933f522345be6` |

The recipe fixes ordering, integer generation, logical aliases, clinical values, effective dates, historical contexts and workflow choices. Initial Bundle bytes are repeatable for that recipe. An actual empty-Bundle receipt binds the reserved dataset namespace to its recipe fingerprint; a different reserved recipe fails rather than silently replacing or appending another cohort.

Fresh databases assign different real resource UUIDs and action/audit times. Subsequent correction requests contain those assigned IDs, so their wire bytes can differ across fresh databases too. The promise is deterministic logical clinical data and a repeatable recipe, not byte-identical database files or audit histories. On the same database, replaying the same actor, key and exact request bytes recovers the same IDs from durable receipts. The local manifest caches those mappings; it is not the authority for clinical identity or access.

## Required scenarios

Each scenario below passed against the actual loaded cohort, then passed again after fresh-local-journal replay. The evidence records the concrete checks and remaining assignment gates.

| Case | Logical persona | Required verification |
|---|---|---|
| DS-01: both creatinine units | `p000` | The same patient's standardized `1 mg/dL` and `88.4 umol/L` normalize equally and produce equal adult eGFR. |
| DS-02: parent/teen proxies | `p001`, `p002` | Individual proxies for ages 13 and 16 can read ordinary released history; ABSL, SUBU and MHEV records remain hidden while self and entitled clinician access remains available. |
| DS-03: haemoglobin across age 14 | `p003` | The same female patient's value immediately before and after the fourteenth birthday uses the respective effective-time ranges. |
| DS-04: retained amendment | `p004` | The original is actually reviewed/released before its amendment; both remain retrievable with the correction link, while the trend selects the amendment. Its potassium label follows the disclosed assessment convention above. |
| DS-05: weight without any height | `p005` | No height exists anywhere for this patient; BMI is unavailable with `HEIGHT_UNAVAILABLE`, rather than a fabricated number. |
| DS-06: multi-year series | `p006` | Ninety-four monthly sodium measurements span January 2019 through October 2026 in ordered trend output. |
| DS-07: actual replay | `p000` | The identical standalone Bundle is sent twice over HTTP; response bytes and IDs match and the second request adds no source result. |
| DS-08: one invalid entry | `p000` | A valid sibling and one invalid-unit entry are submitted together; the response identifies the invalid entry, leaves the sibling uncommitted and adds no source results. |

Additional fixtures exercise an expired adult proxy, separate revoked and retained proxies for one child, an ordinary held result, a reviewed critical result still held, and a separately attested/released critical result. Background personas supply the remaining source rows, including BP components, both creatinine units and explicit unavailable-context cases. They never add a height to the weight-only persona.

Critical fixtures carry explicit source critical interpretation. No numerical critical threshold is invented. Synthetic classification and completion attestation demonstrate attributable actions; they do not constitute real clinical validation or proof of patient contact. Every source begins held, and the loader calls the same [review, attestation and separate release services](WORKFLOW.md) as the local workflow command. It never writes release state directly.

Proxy authorization uses the actual request's UTC date. The semantic as-of date does not freeze the server clock. The scenario verifier must stop rather than claim teen access after its valid age window expires. Historical range/eGFR interpretation still uses each observation's effective-time context.

## Privilege and interruption boundaries

The short-lived fixture code registers only the synthetic authoritative roster and individual proxy links. Its schema-owner credential remains privileged and is confined to that operator phase. They verify role collisions, exact receipt actor/key/hash, successful assigned Patient location and existing patient IDs before registration. They do not create clinical source rows, rewrite receipts, fabricate audit events or set review/release state. Existing roles and revoked links are preserved. Owner credentials are removed before any serving or non-web runtime context starts.

Clinical creation uses real `POST /fhir`. Each workflow action has its own authorization, state checks and audit transaction. Successful actions stay committed when a later action fails; repeats are logged no-ops retaining the first actor/time. Corrections are deferred until their original has genuinely completed release. A held predecessor must not be force-released to make a correction visible.

The loader does not reset the database, remove failed receipts, alter accepted IDs, kill an unrelated listener or treat unfinished work as successful. Unrelated database records are preserved, and cohort counts must be reported separately from global counts. After an ordinary interruption, rerun the unchanged recipe to recover accepted IDs and finish missing actions.

## Explicit revalidation of a rejected initial request

A durable `400` normally replays its original failure. The ordinary loader does not silently change keys to bypass it. For the narrowly guarded operational recovery described in the evidence, an operator can explicitly request one fresh validation attempt:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1 -RevalidateRejected
```

The owner preparation phase checks the original generated bytes against the stored request hash and the fixed actor for that patient. Revalidation is eligible only when the original whole-Bundle failure has one or more `FHIR_INVALID` entries, all other entries are uncommitted `424` siblings, and there is no successful entry or other failure category. A changed body, wrong actor, orphan alternate or incompatible stored outcome fails closed.

For an eligible failed base key, the explicit flag can select only its exact `-revalidated-1` alternate. This is a new request that undergoes normal validation and authorization; it is not a validator bypass. The original receipt remains unchanged. A durable alternate failure, or exhaustion of its bounded same-key `503` retries, stops loading; no second alternate or automatic key rotation is provided.

If that alternate has already succeeded with the same actor and body hash, an ordinary run without the flag resumes using it. Already accepted base requests retain their original keys and IDs. The complete plan is written atomically only after its receipt-metadata transaction commits. The plan permits exactly the base key or that one alternate; a manually edited plan is not trusted as clinical authority.

The first `p013` rejection's cause remains unresolved. A later `p066` rejection produced the trusted primitive-pattern engine exception category; the version-specific source establishes an operational-error path, without proving the particular exception's underlying cause. Both historical responses remain preserved. Recovery permits one alternate per eligible original operation, not repeated rotation of a failed alternate.

Known primitive-pattern engine exceptions and regex timeouts at error/fatal severity now return audited temporary unavailability, without a completed receipt or accepted source. Mandatory validation still has to succeed. The loader can retry only an explicit HTTP `503`, with the exact same prebuilt request, actor, bytes and key: at most three attempts, with one-second and two-second pauses. It stops on the final failure, other statuses, interruption or an unknown transport exception. It never converts a failed validation into acceptance. No validator deadline, definition or dependency was relaxed.

The declared target and actual 100-patient/10,000-source measurements are recorded in [PERFORMANCE](PERFORMANCE.md). Neither a successful load nor the earlier bounded hidden/missing timing probe measures the fictional nine-million-observation production workload.
