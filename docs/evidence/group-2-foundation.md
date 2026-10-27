# Group2 — service/database foundation

Tasks33–34 implementation and focused checks passed on27October2026. Private publication/recovery remain milestone gates recorded in ignored work after the final commit. This checkpoint does not complete R1–R6, clinical behavior, the dataset, UI or the final submission.

## What exists

Root Maven build pins Spring Boot3.5.16/Java21 and HAPI8.12.1 with the required R4 modules/cache provider, JDBC, PostgreSQL driver and Flyway PostgreSQL module. Executable service source, deterministic UTC clock/configuration, safe OperationOutcome errors/privacy headers, bounded Repeatable Read transaction helper, V1 schema and separate migration/runtime paths are implemented. No supplied AttachmentA code was incorporated, compiled, run or fixed.

The [schema rationale](../DATABASE.md) distinguishes all27 source columns, five primary keys, two explicit foreign keys and four explicit nullable observation columns from added implementation choices. New fields/constraints/indexes preserve the source meanings; service-level role/clinical/correction checks are still required in later groups.

## Executed by Codex

1. `pwsh -NoProfile -File ./scripts/verify.ps1`: final result **BUILD SUCCESS**, one FhirRuntimeTest plus eight FoundationIT cases, zero failures/errors/skips. The final complete verification took1m41s on this workspace and finished09:57:23 Asia/Colombo; this is a test-run duration, not a clinical latency or completion forecast. Later start commands also reran the one unit test successfully.
2. Actual HAPI R4/4.0.1 JSON parser and instance-validator runtime: valid synthetic Patient accepted; Observation missing required elements rejected. This smoke check does not prove the future ingestion profile or full FHIR conformance.
3. Real PostgreSQL16.15 schema inspection: all required columns/keys, explicit nullables, component JSONB and foreign-key presence. A nonexistent relationship failed. Runtime observation DELETE, audit UPDATE and schema CREATE were rejected.
4. RequestTransaction: actual Repeatable Read/read-write settings; parameter-bound values including SQL-looking input; content/audit commit together; audit constraint failure rolls back clinical work and prevents callback content return; an expected denial for unknown target/unverified actor commits its minimal audit row. These foundation tests are not proof that every future clinical route is audited.
5. HTTP integration: unknown route404 with safe OperationOutcome, no path/query/stack echo, no-store/nosniff/CSP; test-only argument/missing-parameter400 and safe409 without supplied reason. The test-only controller is not packaged in the serving JAR.
6. **Actual documented start command** `pwsh -NoProfile -File ./scripts/start.ps1`: repeated preparation preserved existing data/credentials; package/unit test passed; separate migration process applied V1 to atlas; serving runtime responded with404/OperationOutcome/no-store at10:02:44 Asia/Colombo. Clinical/UI routes remain absent intentionally at this foundation stage. App was then stopped using Ctrl+C; PostgreSQL remains available for subsequent groups.
7. Unsafe serving role: a separate JAR process on test port18082 used the migration-owner account and exited unsuccessfully with the safety error;100ms polling observed no accepting socket (45s bounded check). Eager initialization is the structural startup gate; finite polling is not proof of all scheduling interleavings. The integration test independently checks that the owner cannot pass the same gate.

## Findings and corrections

The [independent AI read-only audit](../ai/outputs/group-2-foundation-audit.md) identified late ApplicationRunner safety validation and masked client4xx responses. Eager InitializingBean validation and safe ErrorResponse/type-mismatch handling addressed both; added integration cases passed. A follow-up read-only audit found no new blocker.

The repeat-run bootstrap initially hit a Windows Set-Acl privilege error. It now checks an already-correct protected DACL idempotently and uses the .NET ACL setter when changes are needed; subsequent preparation/verification/start runs passed. Actual start smoke testing then caught an unquoted PowerShell Java option; quoting it at both migration and serving sites fixed startup. The first polling check timed out on that failed start. A scratch HTTP checker also initially treated PowerShell's FHIR byte-array body as text; explicit UTF8 decoding corrected that checker, without changing the already-correct service response. Failed attempts are not reported as passes.

Nonfatal test output included JDK/Mockito dynamic-agent warnings and HAPI's notice that the optional schematron library is absent. No schematron validation, production authentication, legal/clinical/security certification, clean-machine acceptance, restore proof or performance result is claimed. Source profile validation, every endpoint's access/audit behavior, JSON2MiB/1000entry enforcement, release/idempotency tables, clinical calculations and other required groups remain pending. In particular the Tomcat form-size setting is **not** proof of a JSON body limit.

## Parallel lanes and evidence limits

Group8 produced a two-section specification draft plus coverage notes. Group9 produced a static PR-review draft plus source/coverage/walkthrough notes. Root retains these in ignored scratch for later review/integration; their new recommendations are not silently adopted and neither group/task set is marked complete. No candidate-run walkthrough is claimed. Real source locators and AI authorship are maintained in the AI working log/provenance index.

Raw build/HTTP/startup logs, XML reports, tools, local credentials, PostgreSQL files, original PDFs and correspondence remain ignored. Curated evidence can be reproduced with the documented commands. Full clean-run, restore and final package/history/access checks remain later gates.
