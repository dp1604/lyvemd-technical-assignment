# Independent AI audit - Task 5 history preservation

Origin: history_audit AI agent, read-only. Root Codex asked it to check the Task 5/91/93 scope, privacy/history pitfalls and then the actual checkpoint script. The following are its actual final responses. Root performed runtime checks separately.

## Workflow audit

Read-only audit complete. No files changed and no GitHub contact made.

Task 5 should establish this workflow:

1. Preserve authentic commits at meaningful completed increments; inspect explicitly staged files, relevant checks and AI provenance before each commit.
2. Push reviewed commits to the existing private repository.
3. Generate a self-contained bundle of committed reachable history in ignored `work/history/`, using unique filenames and checking every command result.
4. Refuse dirty tracked or nonignored untracked files, missing HEAD, shallow/incomplete history and unexpected checkpoint destinations.
5. Verify the bundle, restore into a new independent mirror, run integrity checks, and compare every ref name/object ID. Also verify the restored main commit/tree and ancestry.
6. Save a SHA-256 digest and sanitized evidence identifying the exact snapshotted commit.
7. Repeat after the final delivery commits; inspect the final package and verify reviewer access in Tasks 91–94.

The bundle preserves committed history; it excludes the working tree, index and local configuration. A mirror restoration permits checking all refs. These behaviors are documented by [Git bundle](https://git-scm.com/docs/git-bundle) and [Git clone](https://git-scm.com/docs/git-clone).

Material risks:

- `.gitignore` currently lacks `*.bundle`; add it as secondary protection.
- Ignore rules do not remove secrets previously committed or present on other included refs.
- A normal clone plus main equality does not establish recovery of every bundled ref.
- Concurrent commits or ref changes can invalidate a snapshot; compare source state before and after.
- Use a fixed contained ignored directory; reject reparse-point escapes, existing destinations and arbitrary cleanup.
- Evidence committed after the checkpoint is outside that checkpoint; identify the boundary accurately.

Recommended completion wording: **“Completed Task 5’s history-preservation setup and recovery verification. Meaningful commits and final history verification remain ongoing through delivery.”** Leave Tasks 91 and 93 unchecked.

## Actual script audit, before fixture execution results

No concrete blockers found in the drafted script.

It enforces clean committed source, reviewed ref namespaces, a fixed ignored destination, unique checkpoints, complete bundle creation, independent mirror recovery, integrity checks, ref/commit/HEAD/tree equality and source stability.

The script’s scope statement is accurate: this verifies recovery of committed reachable history. Secret-content review and final delivery verification remain separate duties. Await the fixture results before recording a successful verification.
