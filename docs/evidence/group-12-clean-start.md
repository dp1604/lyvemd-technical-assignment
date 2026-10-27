Current contribution status: [subsequent candidate review](../CANDIDATE_REVIEW.md). The evidence below preserves its original checkpoint; later human review does not alter that historical execution or result.

# Fresh-checkout two-command evidence — qualified outcome

Root executed both documented public commands against a fresh full-history local checkout at **f109684701b88ca38dd424205fcb68537c0a5364** and a newly initialized, empty PostgreSQL cluster. The build/start command reached its real client; the create/load command completed once and its actual HTTP/workflow dataset verifier passed. **The enclosing verification wrapper nevertheless finished FAIL after those checks**, before its supplemental GET. These outcomes are deliberately separate; there is no claimed overall wrapper PASS. [Sanitized record](group-12-clean-start.json).

The run was **2026-10-27 13:10:42.3095302–13:43:50.1485549 UTC** (18:40:42–19:13:50 Asia/Colombo), **1,987.5221117 seconds** including cleanup. The original FAIL report and logs remain private and unchanged. Its SHA-256 is `6d6eb2f4e4f0d515b793f8e179f7d4b14490a5c6e95c968872f58d1e9865726e`.

## What actually passed

| Scope | Observed result |
|---|---|
| Fresh source | The expected HEAD matched; tracked status was clean and history was non-shallow. Ignored local work/tools/database/credentials/dataset manifest/build output were absent before the commands. |
| Prerequisites | Existing external JDK 21.0.12.1+1, Maven and PostgreSQL 16.15 binaries, with an external warm Maven repository. These were not copied into the fresh checkout. This is not a fresh-machine or cold-dependency test. |
| First public command | `pwsh -NoProfile -File ./scripts/start.ps1` built, applied all three migrations to the previously empty schema and served the actual client on loopback port 18081. Readiness took 356.7714693 seconds. It is a foreground server, not a process reported as having exited successfully. |
| Empty database before load | A separate owner metadata query confirmed the server's actual data directory was the new checkout's cluster, PostgreSQL major 16, with **0 patients / 0 encounters / 0 observations / 0 audit events**. New credential values differed from the preserved source configuration. |
| Served client | All four HTML/CSS/module assets returned 200, no-store and exactly the checked-out source content. |
| Second public command | `pwsh -NoProfile -File ./scripts/load-dataset.ps1` was invoked **once**, with no `-RevalidateRejected` switch or public-command retry. It exited successfully and printed `ATLAS_DATASET 200 LOAD_AND_VERIFICATION_COMPLETED`; measured stage time was 1,601.6094996 seconds. |
| New persisted dataset | An independent metadata query confirmed **100 patients / 106 encounters / 10,000 source Observations**. Clinician response lists contained 3,693 derived rows, counted separately from stored sources. The fresh loaded audit count at this checkpoint was 20,241. |
| Actual secured scenarios | The fresh dataset's `verification.json` is PASS for all eight required cases and the additional held/critical/expired/revoked/scope case. It was produced at 13:43:21.986331300 UTC. Its SHA-256 is `c0af8e1f0492e58523bde1cee3ca9b2d6d3455fd1b0f617b04ccaffee7c9684b`. |

Those cases are equivalent creatinine units; both teen/proxy examples and all protected classes; haemoglobin across age 14; retained original/correction; missing-height BMI; the 94-point multi-year sodium trend; exact Bundle replay; and indexed invalid-entry whole-Bundle rejection. The duplicate case retained identical response bytes and visible total 5; the invalid case returned 400 with offending index 1, total 5 before/after and no partial source writes. Extra cases checked held/critical release, expiry, individual revocation with another proxy retained, and actor scope.

These are actual ingestion/workflow and HTTP reads through the loader's owned loopback port 18091 helper, using the same fresh JAR/database and restricted runtime access. They are not fabricated SQL clinical fixtures or a claim that every read ran through the foreground port 18081 listener. Startup/assets independently exercised port 18081. Source/hash and scenario evidence are synthetic, not real clinical approval or contact.

## What failed and was not performed

After the completed public-command/dataset checks, the wrapper prepared an audit cursor for an additional foreground-server clinical GET. It then tried to change `HttpClient.Timeout` from 3 to 30 seconds after that instance had already sent readiness/asset requests. Root reproduced the resulting `InvalidOperationException` with an isolated .NET probe. This identifies the wrapper's configuration error before the supplemental GET; it does not establish an application failure.

The private run directory contains `audit-before-get` output but no `audit-after-get` output. The wrapper did **not** execute/verify the supplemental GET or its one-new-audit assertion. Its later post-run Git HEAD/status assertions were also not reached. Neither missing assertion is reported as passed. The retained wrapper SHA-256 is `490eb4abe52fc81e786990b53fd77c35e6c9deb02ac1c91153e6101f680a14ff`.

**Separate post-cleanup source check:** root subsequently performed read-only Git checks, independently of the failed wrapper. The fresh checkout's `git status --porcelain --untracked-files=all` was empty and its HEAD still exactly `f109684701b88ca38dd424205fcb68537c0a5364`. Root's comparison of that tested commit with the current canonical HEAD showed no changes in `src/`, `pom.xml`, `scripts/start.ps1`, `scripts/load-dataset.ps1` or `scripts/lib/`. This is a later, separately attributed source/status result; it does not change the wrapper's original report, execute the omitted GET/audit assertion or claim another application run.

The wrapper's terminal report remains **FAIL / ATLAS_CLEAN_ACTION_FAILED**. No failed artifact is removed, no public-command retry or second dataset load is concealed, and no synthetic overall PASS replaces that report. Representative secured reads were already part of the successful dataset verifier; the extra wrapper assertion is distinct. Root separately reviews the original Task 89 scope—two documented commands from a fresh checkout/empty database, schema/client, reproducible scenarios/counts and real reads—rather than rerunning the successful load solely to repeat the optional assertion.

## Cleanup, preservation and limits

The report records the owned fresh cluster stopped, the preserved original cluster restarted, and original patient/encounter/observation/audit counts plus credential-file hash unchanged. Original counts were 100/106/10,000 with 54,877 audit events. This establishes those checked invariants, not byte equality of every PostgreSQL file or every source value. Data directories, credentials and failure evidence were preserved; no reset/drop/delete occurred. Restarting the candidate-facing original application is a separate operator action.

This evidence does not rerun the full 237-case backend suite, prove a clean machine/cold dependency download, establish production scale, or complete deferred manual Tasks 81/86/92. It identifies the tested source checkpoint; later document commits do not retroactively become tested application changes. Final source/history/package/recipient checks remain separate. Task 89 completion is root's bounded acceptance of the actual command scope, never a statement that this wrapper passed.
