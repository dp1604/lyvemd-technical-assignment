# Task 4 - project structure and initial private-repository verification

Status: structure verified. Application features, final written deliverables and their acceptance evidence remain pending.

## Verified initial snapshot

- Repository: https://github.com/dp1604/lyvemd-technical-assignment
- Owner: dp1604; private=true was checked through the GitHub API after creation and after the first push.
- Branch: main.
- Initial local and remote commit: c77232fda2b58be75a4de9ea66bc712e25cfa493.
- Verification time: 2026-10-27T06:50:13.6816662+05:30.
- Initial remote inventory: 23 curated text and directory-marker files, matching local tracked files exactly.
- Working tree after the initial push: clean.

The initial snapshot contains the service/test/migration/static-client/dataset-source locations, four clearly unfinished deliverable documents, engineering/security rules, exact current candidate request and confirmed decisions, source hashes and a curated environment summary. It contains no executable assignment feature yet.

## Checks performed

Before staging, ten representative protected paths were checked against Git ignore rules: local password, database data, PostgreSQL binary, extracted review listing, local planning checklist, environment file, local application properties, build JAR, generated dataset and raw evidence. All were ignored.

The 23 candidate files were checked against a curated path inventory, size/binary checks, the known local database-password value and common GitHub/AWS/private-key patterns. No match was found. The staged diff passed the whitespace/error check. These are targeted content and inventory checks, not a comprehensive security certification.

After the push, local HEAD matched the remote main commit, the complete remote file tree matched the 23 reviewed files, private ownership was rechecked and the working tree was clean. No collaborators were invited and no submission was sent.

An independent AI agent reviewed the actual scaffold and found no blocking omission or accidental clinical/credential content in the inspected files. Its response and origin are retained in the AI output record. If Maven Wrapper is later selected, its required bootstrap files must be deliberately allowed through the binary-ignore rules.

## History and remaining work

History starts with the actual scaffold work, with no invented earlier commits. Subsequent implementation, documentation and verification changes must preserve meaningful incremental commits. Final recoverable-history and reviewer-access checks remain mandatory before delivery. This note describes the initial snapshot; later verification-record changes are separate authentic commits.
