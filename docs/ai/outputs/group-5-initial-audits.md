# Group 5 initial AI review reconciliation

These are scratch-only AI reviews of our implementation and adopted assessment rules, distinct from the static AttachmentA review. Neither worker changed canonical files, ran the database tests or performed Git actions. Root integrates and verifies.

## Read-contract audit

Source: agent `01a17c28-82d1-7861-be76-af23874491ef`, actual returned final message `msg_01b3d737a52d44e6016ae04d2b7abc81919f2b15c818636035`.

The worker identified implementation obligations rather than a new policy blocker: compute BMI from the full authorized history before chart-window filtering; project individual BP component trends from an authorized parent; explicitly sort after visible-series map construction; count/page after visibility and derivation; parse malformed/duplicate query parameters inside the audited action; separate log rights from clinical rights; and test audit INSERT and deferred COMMIT failures. The current full-patient materialization and snapshot revocation/timing limits must be disclosed. Cross-request offset log pages can move as new audit events are appended; each response still needs an internally consistent count/page.

Root retained these gates. Root also proposed explicit derived-row/trend conventions to the candidate in C3 because they materially define the user-visible contract. The worker's absence of a new blocker does not constitute candidate adoption.

## Workflow audit

Source: agent `01a17c28-b2a2-7351-ad48-88e131b40511`, actual returned final message `msg_081edc3217a18fcd016ae04cec874c8191a60b6f88bb8f07dd`.

The worker identified two unspecified policies: the authority to revoke an individual proxy and whether completed classifications may change. Its implementation recommendations were narrow review-column UPDATE grants, conditional mutations with fresh Repeatable Read retries, no clinical-source UPDATE privilege merely to lock a source row, explicit target-proxy attribution, and a non-web local workflow mode. It proposed tests for source-critical floors, separate review/attestation/release, attribution, repeats/conflicts/races, atomic audit rollback and least privilege.

Root raised C1/C2 for actual candidate adoption and did not infer authority. Workflow code and its schema changes remain pending that answer. Recommendations are not runtime proof or clinical/governance sign-off.

## Independent implementation checkpoint

The shared audited-read wrapper, query parsing, access-log route and existing source-detail refactor proceed under the already-adopted rules. Three focused query tests passed after root corrected an exception-type mismatch for unknown analyte codes. HTTP/database audit-failure and role checks are still being executed; no complete Group5 claim follows from compilation or this report. Full response conventions and the local workflow remain pending C1–C3 adoption.

## Subsequent root checkpoint

Candidate explicitly adopted C1–C3 unchanged in actual message `01a17c40-e984-73a0-a0d1-3510d6e6cb1f`; [adoption](../../decisions/group-5-clarifications.md) supersedes the earlier pending status. Root then executed the independent shared-read increment checks: affected22-case ingestion/detail regression plus3 query cases passed; new10 access-log and2 deterministic revocation-snapshot cases plus3 query cases passed. Insert and deferred-commit audit faults withheld content and rolled back; recovery and expected-denial persistence passed. List/trend/derived views and workflow remain the next increment, not passing evidence in this report.
