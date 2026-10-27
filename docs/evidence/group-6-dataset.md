# Group 6 — deterministic dataset and actual execution evidence

Codex executed the fixed synthetic cohort, all eight required scenarios, accepted-prefix preservation and a separate fresh-local-journal replay. These are automated results, not candidate-run verification or production certification. Client, final regression, measured read performance, restore/clean-start acceptance, final note/review integration and candidate walkthroughs remain their separate gates.

## Actual inventory and reproducibility

| Quantity | Dataset cohort | Global assessment database at inventory check |
|---|---:|---:|
| Patients | 100 | 100 |
| Encounters | 106 | 106 |
| Stored source Observations | 10,000 | 10000 |

The clinician result lists additionally contained **3693 derived rows**. These are caller-visible calculated or unavailable-result views, not extra stored source Observations; BP components likewise belong to one source parent. The duplicate operation contributes one stored source, and the rejected operation contributes none. The operator's read-only inventory independently checked persisted cohort counts rather than trusting journal lengths alone. Unrelated rows were not deleted.

Version `atlas-g6-v1`, seed `20261027`, semantic as-of `2026-10-27`, recipe SHA-256:

```text
f0a5f4cbbfd88d7f54dd27713862beb827e4f531bb8b5e40d74933f522345be6
```

Independent fresh-JVM generation matched all 100 initial request hashes and this fingerprint. A deliberately conflicting 99-patient generation was rejected while all 101 existing input/configuration files remained unchanged. Fresh databases assign new UUIDs/action times; deterministic logical input does not mean byte-identical database or audit history. [DATASET.md](../DATASET.md) provides the create/load command, parameters, scenarios, privilege split and explicit recovery controls.

**Terminology boundary:** official LOINC defines `2947-0` as sodium in blood, while the task calls it potassium. The candidate's actual [D-17 decision](../decisions/group-6-loinc-clarification.md) retains that mapping only for this synthetic assessment, with prominent disclosure. Specimen confirmation, verified mapping and clinical-owner approval gate real clinical feeds. This is not employer approval or full semantic conformance.

## Eight actual scenario results

All eight checks passed at `2026-10-27T10:36:50.460441800Z` (request UTC date `2026-10-27`), and passed again after the independent journal replay at `2026-10-27T10:53:14.869228700Z`.

| Case | Actual secured runtime check | Result |
|---|---|---|
| DS-01 | The same patient's standardized creatinine1mg/dL and88.4umol/L normalized equally and produced equal applicable adult eGFR. | PASS |
| DS-02 | Age13/16 individual proxies saw the ordinary released control; ABSL, SUBU and MHEV encounter history remained hidden, while self/entitled clinician reads succeeded. | PASS |
| DS-03 | The same female patient's Hb11.5 immediately before/after age14 changed from NORMAL under11–14 to LOW under12–15.5, using effective-time age. | PASS |
| DS-04 | The original assessment-convention potassium4 was reviewed/released before amendment5.8. Both details retained their values/link, and the latest-visible trend used the amendment. | PASS |
| DS-05 | The weight74kg persona had no height anywhere in its generated/source history; secured BMI returned HEIGHT_UNAVAILABLE instead of a number. | PASS |
| DS-06 | The actual ordered sodium trend contained94monthly points from January2019 through October2026. The browser chart remains Group7 work. | PASS |
| DS-07 | One identical standalone Bundle was actually sent twice over HTTP; complete response bytes/IDs matched and the visible total remained 5 → 5. | PASS |
| DS-08 | A valid sibling plus one invalid-unit entry were actually submitted together; HTTP400 transaction-response identified entry1, with sibling424 and no source effect. Total remained 5 → 5. | PASS |

Additional checks passed for adult proxy expiry, self revocation of one individual proxy while another retained access, ordinary held results, reviewed-but-held critical results, separately attested/released critical results, role/encounter scope and STAFF log access. The failed envelope is the documented assessment-first R4 deviation, not a universal FHIR response claim. Proxy age uses the real request UTC date; the recipe's as-of date does not freeze authorization time.

Clinical creation used actual `POST /fhir`. Review, critical completion attestation, separate release and proxy revocation used the implemented runtime workflow services, with audit. The short-lived privileged fixture code registered only receipt-verified roster/relationship data. It did not create clinical sources or fabricate review/audit/receipt states. Synthetic attestation does not establish real patient contact or clinical sign-off.

## Interrupted-load chronology and corrections

| Checkpoint | Observed result and retained evidence |
|---|---|
| Initial command | 140-unit build passed;10patient Bundles/113sources committed, then the first background Bundle exhausted the15-second transaction while local structural validation was inside it. Earlier successful Bundles stayed committed. |
| Transaction-boundary correction | Input-only HAPI preparation moved before the database action. Six focused unit plus35 real integration cases passed. Stored roster/rights/reference/receipt/planning/inserts/audit remain one bounded15-second Repeatable Read action; the cooperative45-second preparation budget cannot interrupt a single active validator call. |
| First resume | 146-unit build passed;13patients/443sources accepted, then p013 retained an indexed400FHIR_INVALID. Generated and stored request hashes matched. Its cause remains unresolved. |
| Diagnostics and initial recovery guard | Two distinct temporary diagnostic methods passed across two invocations; one optional method was skipped initially then explicitly run. All28 deliberately invalid HTTP prefix probes rejected only their intentional marker and left the13/443population unchanged. Privacy/profile/boundary checks and eight selector guards passed; no diagnostics established p013's cause. |
| Investigated revalidation | 155-unit build passed; the one permitted p013 alternate succeeded and loading reached66patients/6,273sources. p066 then retained another indexed400, with trusted TYPE_SPECIFIC_CHECKS_DT_PRIMITIVE_REGEX_EXCEPTION diagnostic. |
| Operational classification correction | Version-specific installed bytecode and [core primitive-validation source](https://github.com/hapifhir/org.hl7.fhir.core/blob/6.9.12/org.hl7.fhir.validation/src/main/java/org/hl7/fhir/validation/instance/InstanceValidator.java#L3461-L3486) show that an internal regex exception can enter the validation error list. The adapter now treats exact trusted operational IDs as unavailable, never valid, before considering ordinary input errors. It does not establish the underlying p066 cause or infer a p013 cause. |
| Current focused checks | 23 unit plus35 real PostgreSQL ingestion/workflow cases passed, zero failures/errors/skips:58cases in that invocation. This is not a newly executed full-backend suite. |
| Completed command | Full163-unit build passed, zero failures/errors/skips. Preparation/build/migration/configuration, owned helper, actual HTTP/workflow load, roster relationships and all secured scenario checks completed successfully. The owned helper stopped; PostgreSQL/data were preserved. |

These runs overlap and belong to different checkpoints; do not add them into an invented distinct total. The prior full204-case backend checkpoint and affected21-case recheck remain historical [Group5 evidence](group-5-reads-and-workflow.md). Final assignment-wide regression remains Group10.

Known regex engine exceptions/timeouts now reach the existing audited503/unavailable boundary without a completed receipt. Genuine primitive mismatches, other invalid inputs and unknown/spoofed rule IDs remain invalid. The loader retries only an explicit503, at most three total attempts with1s/2s pauses, retaining the same prebuilt request/actor/body/key. Final failures, interruption, other statuses and unknown exceptions stop visibly. No validator deadline, rule, definition or dependency was relaxed. Ordinary logs contain fixed categories, never raw diagnostic text, location, payload or throwable.

## Receipt and identity preservation

The operator explicitly revalidated two investigated historical failures, each with exactly one new `-revalidated-1` key. Both original400responses remain unchanged, independently compared with saved response digests; both generated request SHA-256 values and fixed actors match their successful alternate200receipts. Neither original receipt was deleted or rewritten, and no failed alternate was rotated again. This is a distinct request under the existing contract, not relabelling a failure as success.

The receipt's patient_ids are affected audit targets, including on a rejected operation. The preservation proof therefore queries actual patient-table membership: neither rejected attempt persisted its candidate patient, while each successful alternate has one persisted patient. Array length is not used as a clinical commit count.

All accepted IDs from each of the three saved journals were compared with the completed dataset:10patients/113sources,13/443 and66/6,273. Every previously accepted Patient, Encounter and Observation ID was retained. Read-only persisted inventory confirmed100/106/10000. MD5 was used only as a metadata equality check for accidental mutation; request identity remains SHA-256.

The separate replay started from independently generated identical inputs with **neither local manifest nor key plan**. Ordinary flag-free owner preparation recovered all100operation keys, including the two already-successful alternates, from durable receipt metadata. Actual HTTP replay recovered the same100Patient,106Encounter and10,000source IDs, then repeated all scenario checks. Review/release/attestation metadata and proxy-link state hashes were unchanged, including first actor/time and revocation attribution. Audit growth from real replay/no-op actions is expected and was not hidden in the state comparison. The proof completed at `10/27/2026 10:53:15` and published PASS only after owned-process cleanup/environment clearing.

This proves same-database journal recovery for the synthetic cohort. It is not a restore-to-another-database proof, a guarantee of universal timing or a production disaster-recovery claim. Raw requests, IDs, credentials, logs and operational artifacts remain ignored; source, tests and curated evidence are published privately. Measured read targets/latencies, fresh-checkout/empty-database acceptance and human verification remain open.