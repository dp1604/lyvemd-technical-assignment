# Task 5 - history preservation and recovery verification

Status: the history-preservation workflow is established and its initial recovery test passed. Meaningful commits and final history/package/access verification remain duties through delivery; Tasks 91-94 are not completed by this record.

Executed with Git 2.55.0.windows.3 and PowerShell 7.6.5 on the candidate's Windows environment.

## Verified assignment snapshot

- Repository: https://github.com/dp1604/lyvemd-technical-assignment (private ownership rechecked during Task 5).
- Snapshotted commit: `a228a17adc2345e2d21d1aaa5eca8b8449bfcf47`.
- Complete tree: `ae15e3345f9e4cf7608c737d4eadc1635f451e4b`.
- Reachable commits recovered: 3.
- Recovered refs: two, refs/heads/main and refs/remotes/origin/main, both at the snapshotted commit.
- Fresh working-copy tracked files: 28, matching the source inventory.
- Bundle SHA-256: `ad6ac6cfd554f960a483c961afe74e158e443307893c43084a93e39e729194b3`.

The checkpoint's complete bundle passed Git verification. A new independent mirror passed object-integrity checks and matched all source ref/object mappings, the reachable commit set, HEAD and complete tree. The source remained clean and unchanged during the checkpoint. A separate normal clone recovered the main working copy with no diff from its committed files and no local work/ directory.

This snapshot precedes the commit adding this evidence document. A further checkpoint is created after that commit and recorded in the local completion report. Final submission needs another checkpoint after its final commits; an earlier snapshot never covers future work.

## Synthetic failure and branch test

Before shipment, the exact checkpoint script was tested in a separate disposable Git fixture with three commits, two branches (including a nonmerged branch) and a tag. All three refs/commits were recovered, and the side-only file could be read from the recovered branch. An ignored environment-file sentinel was absent from recovered history. A nonignored uncommitted file caused refusal before a new checkpoint directory was created.

The fixture is deliberately synthetic test history and is excluded from the assignment repository. Its script SHA-256 matched the shipped helper: `13f903cd774dc52ac81662f28d3c3cf8d46305aea9087c72e34dd7939f613931`.

## Review and limits

An independent AI agent reviewed the requirements and script read-only and found no concrete blocker. Its actual final responses are retained in [the audit record](../ai/outputs/task-5-history-audit.md). Root Codex executed the tests described here; no candidate-run test is asserted.

The helper verifies committed reachable history and a stable source snapshot. It does not back up uncommitted/ignored files, local Git configuration or hooks, credentials, downloaded tools or PostgreSQL data. It is not a secret scanner, application acceptance test or database restore drill. Commit content still requires deliberate privacy/security review; ignore rules cannot sanitize earlier committed secrets.

Native command results are checked. Checkpoints use new fixed ignored directories, reject reparse points in the output path, and never delete, stage, commit, push or rewrite source. Bundles are also ignored by extension. See [the maintained workflow and delivery instructions](../HISTORY.md).
