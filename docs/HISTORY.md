# Development history and delivery

The assignment requires the build with its history. The candidate chose a private GitHub repository. Authentic commits begin with the actual Task 4 scaffold; earlier planning and the isolated environment probe are not reconstructed into invented development commits.

## Workflow through delivery

For each meaningful completed increment:

1. Inspect the change against the brief, current decisions and affected requirements. Update relevant documentation, evidence and AI provenance.
2. Run the checks appropriate to that change. Record failures and limitations truthfully; do not replace them with an unsupported success claim.
3. Inspect the exact files and diff, including for credentials or clinical data. Stage explicit reviewed paths, run `git diff --cached --check`, and inspect `git diff --cached`.
4. Commit with a concrete description of the change. Preserve actual authorship and timestamps. Keep subsequent fixes visible; do not rewrite or squash published history without an explicit reason and agreement.
5. Push reviewed changes to the private repository and check the remote commit matches the intended local commit. Recheck private ownership at checkpoints. No collaborators or submission recipients are added without the candidate's instruction.
6. Create a history checkpoint after a coherent committed milestone and again after the final delivery commits.

Use focused commits and independent review where it helps. Application CI/checks will be added when a runnable build exists. Feature correctness requires its own verification evidence.

## Verified checkpoint command

Requires Git and PowerShell 7.2+; run from the repository root:

```powershell
pwsh -NoProfile -File ./scripts/checkpoint-history.ps1
```

The command refuses staged/unstaged or nonignored untracked work, shallow/unborn/detached source, unexpected ref namespaces, and unsafe local output directories. It never stages, commits, pushes, deletes or rewrites the source. Its fixed destination is a unique directory under ignored `work/history/`; `*.bundle` is also ignored as secondary protection.

It exports all reviewed refs and reachable objects into a self-contained `history.bundle`, verifies the bundle, restores it into a new mirror, runs object-integrity checks, and compares every ref/object map, reachable commit set, HEAD and complete file tree. It checks source stability and writes a SHA-256 sidecar plus a verification manifest. The manifest identifies the exact snapshotted commit; later commits are outside that snapshot.

The first test used a separate synthetic fixture with three commits, a nonmerged branch, a tag and an ignored environment-file sentinel. Recovery passed, the ignored file was absent, and a nonignored uncommitted file caused refusal before any new checkpoint was created. This fixture is test data, not assignment development history. Actual-repository mirror and normal working-copy recovery also passed; see [the exact snapshot and outcomes](evidence/task-5-history.md).

## What the checkpoint contains

The bundle contains committed reachable Git objects and refs, including earlier versions and other included branches. Ignore rules do not remove secrets already committed, even if later deleted. Review the included history before final packaging; the checkpoint command verifies integrity, not secret-free content or application correctness.

It does not back up uncommitted files, ignored work, the index, local Git configuration/hooks, credentials, tools or PostgreSQL data. This history recovery check does not replace the planned database restore verification.

## Delivery with history

- Repository delivery: preserve the complete Git history in the private repository and verify that the intended reviewer can access it in Task 94. A link alone does not establish access.
- Archive delivery if used: include the final committed source/documents plus the fresh self-contained `history.bundle`, its SHA-256 digest and recovery instructions. A source-only ZIP has no commit history. Do not copy the local working folder indiscriminately.
- Regenerate and verify the bundle after final commits, compare its HEAD with the source in the package, inspect contents for secrets/unrelated files, and recheck integrity. Tasks 91-94 remain pending until that final work is performed.

When a delivered bundle is in the current directory, a normal working copy can be recovered with:

```powershell
git clone --branch main -- history.bundle restored-assignment
git -C restored-assignment log --oneline --all
git -C restored-assignment fsck --full
```

The checkpoint's mirror restore verifies all included refs; this normal clone is for browsing the delivered main branch. Both rely on Git's documented [bundle](https://git-scm.com/docs/git-bundle) and [clone](https://git-scm.com/docs/git-clone) behavior.

## Continuing candidate instruction — Group3 onward

Every meaningful repository change must receive its own meaningful commit message and be pushed to the private remote. This applies throughout future groups and delivery. Keep focused checks and AI disclosure with each increment; the full bundle/recovery checkpoint remains a coherent-group/final milestone. Do not wait until a large group ends to commit and push already-meaningful changes, and never fabricate history. Source: actual user message 01a17bae-4f42-78b0-a9cc-855a267d3f1a.
