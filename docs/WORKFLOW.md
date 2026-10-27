# Local clinician workflow and individual proxy revocation

These explicit local assessment commands implement [candidate-adopted C1/C2](DECISIONS.md) and Package B. They add no public endpoint, login, provisioning, notification or Careline write-back. Actor IDs are impersonatable test declarations matched against the roster and stored rights, not production authentication. A trusted local operator runs these commands; production authority and clinical governance require separate validation.

## Review, critical path and separate release

Every new/amended/corrected source starts HELD. Source `final` alone does not release it. A known CLINICIAN on any stored encounter for that patient may explicitly classify the individual source as NONCRITICAL or CRITICAL. Source-critical parent/component signals are an immutable floor: they cannot be classified NONCRITICAL. HIGH/LOW calculation flags are not invented critical thresholds.

Review is distinct from release. CRITICAL additionally requires a separate affirmative critical-path-completion attestation before a separate release action. The attestation records this demonstration action and its actor/time; it does not verify patient contact. Only final/amended/corrected source statuses may release. A held predecessor with an existing correction cannot newly be released simply to make its descendant visible. A released correction whose ancestor is concealed stays withheld by complete-visible-ancestry filtering.

The first classification and each completed transition retain their actor/time. An identical repeated completed action is an audited no-op; a conflicting classification or missing prerequisite returns409 without overwriting prior state. There is no reset, reclassification, retraction or reinstatement command. Released source history remains retained after a correction. Real re-review/retraction needs separately governed production policy; a workflow mistake should not be disguised as changing a measurement.

V3 grants UPDATE only on the separate review metadata columns; source Observations and audit events remain append-only to runtime. Correction ingestion touches its predecessor's mutable review row before appending the new source. Although the value is unchanged, PostgreSQL creates an MVCC version shared with first release. An overlapping stale Repeatable Read updater retries its whole transaction; a correction committed first is then visible and prevents new release of the predecessor. This does not modify the clinical source or retract a release that validly committed before the correction.

## Individual revocation

A known self PATIENT may revoke one existing `(patient,proxy)` link. Other roles cannot use this action. Retain the row with inactive flag, first revocation time and revoker ID. Repetition is an audited no-op preserving that first attribution; a different proxy link is unaffected. The audit has a dedicated nullable `target_proxy_id`, exposed as `targetProxyId` only through the already-permitted self-PATIENT/STAFF log view. No proxy identity is squeezed into free-text action/source or ordinary logs. Legacy unknown attribution is not fabricated.

The service exposes no proxy provisioning or reinstatement. Existing limited runtime SQL grants permit updates of link state; they are not a database-level proof against arbitrary trusted runtime SQL reactivation. The caller service authorizes every action. No guardian law, staff-administration power or legal revocation authority was inferred; production governance remains a gate.

## Atomicity and safe results

Each action verifies the declared roster pair and stored entitlement, reads/checks state, conditionally mutates metadata and appends one categorical ALLOWED/DENIED audit in the same fresh read-write Repeatable Read transaction. Success is returned only after commit. Expected denials commit their audit. Audit insert/commit failure rolls back the state change and returns categorical503. SQLSTATE40001/40P01 retries the entire action with a new snapshot, at most three attempts, never a statement in an already-failed transaction. No clinical payload or caller-supplied text is printed.

A revocation committed before a content action's snapshot denies it; a later one cannot retract an already-authorized in-flight response. Complete database outage cannot write an audit to that same database; it fails closed with a constant operational signal. Neither bound is disguised as an impossible availability/audit guarantee.

## Commands

Build and migrate the assessment database with the documented start command first; verification migrates only the separate `atlas_test` database. From the repository root, use the packaged non-web mode through the local wrapper, replacing the declared IDs with synthetic roster/resource IDs:

Set `$clinicianId`, `$patientId`, `$observationId`, `$criticalObservationId` and `$proxyId` to actual synthetic roster/resource IDs first; the documented placeholders do not imply that a roster has already been loaded.

```powershell
./scripts/workflow.ps1 -Action review -ActorId $clinicianId -PatientId $patientId -TargetId $observationId -Classification NONCRITICAL
./scripts/workflow.ps1 -Action release -ActorId $clinicianId -PatientId $patientId -TargetId $observationId
./scripts/workflow.ps1 -Action review -ActorId $clinicianId -PatientId $patientId -TargetId $criticalObservationId -Classification CRITICAL
./scripts/workflow.ps1 -Action attest-critical -ActorId $clinicianId -PatientId $patientId -TargetId $criticalObservationId -AttestComplete
./scripts/workflow.ps1 -Action release -ActorId $clinicianId -PatientId $patientId -TargetId $criticalObservationId
./scripts/workflow.ps1 -Action revoke-proxy -ActorId $patientId -PatientId $patientId -TargetId $proxyId
```

The wrapper uses the assessment database `atlas`, runtime credentials in the environment and the current JAR; it does not build, provision identities or migrate schema itself.

For a deterministic loader, a bounded UTF8 JSON batch can hold ordinary command argument arrays without `--workflow`, for example `[ ["review","clinician-id","patient-id","observation-id","NONCRITICAL"], ["release","clinician-id","patient-id","observation-id"] ]`. Invoke `./scripts/workflow.ps1 -BatchFile ./work/commands.json`. Maximum file2MiB/10,000 commands; malformed structure is rejected before actions. Runtime IDs/roles/rights and transition meaning are still checked independently for every command.

Batch mode starts one non-web Spring context, then runs sequential **independent action transactions**. It stops on the first failed result; earlier successes stay committed and later commands are untouched. It is not an all-or-nothing bulk transaction. Replaying prior successes preserves their first actor/time and adds an audit per repeated attempt. Output is one categorical status/code and completed-prefix count; exit0 means all completed,2 a safe input/access/conflict failure,3 unavailable. Use the actual services from a loader rather than fabricating released review rows. No real clinical data belongs in these assessment files.
