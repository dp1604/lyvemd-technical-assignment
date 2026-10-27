# Group 5 — proposed workflow and result-view clarifications v1

Status: proposed by Codex, not adopted. These are assessment choices where the brief and adopted Packages A/B do not specify every transition or response detail. They are not employer facts, legal authority or clinical sign-off. Existing policies, including visible-only derivation, remain unchanged.

## C1 — bounded, one-way clinician workflow

Use separate local actions: review/classify, attest critical-path completion, and release. Each checks the known CLINICIAN and stored encounter entitlement, writes its audit in the same transaction and returns only after commit. No new public endpoint or notification service.

The first explicit classification is retained. Repeating the same completed action is an audited no-op, preserving its original actor/time. A conflicting reclassification returns a safe conflict; there is no review reset, reclassification or release-retraction command in this assessment. A new correction is a new source row with its own held workflow. Real clinical re-review/retraction needs a separately governed production policy; this does not imply that correcting a workflow mistake should be disguised as changing a measurement.

Source-critical signals cannot be downgraded. CRITICAL requires explicit clinician review, then a separately recorded critical-path-completion attestation, then a separate release. Only final/amended/corrected source records are release-eligible. A held original that already has a correction cannot newly be released merely to unblock its descendant. Existing released history is retained; complete-visible-ancestry rules still apply.

Reason: demonstrate the required attributable release and non-silent critical path without silently inventing a clinical reclassification/retraction process or overwriting prior decisions. The attestation demonstrates the action; it does not prove actual patient contact.

## C2 — individual proxy revocation authority

A known PATIENT may revoke one of their own existing individual proxy links through a local action. Other roles cannot use this action. Repetition is an audited no-op preserving the first revoker/time. Reinstatement and proxy provisioning are outside this command.

Record the affected proxy ID in a dedicated nullable audit field, together with the normal actor/patient/action/outcome/source/time. Store the first revoker on the link. Do not place identities or clinical values in free-text action/source or ordinary logs. The permitted patient/STAFF access-log view can show that target attribution.

Reason: satisfy individual, revocable, logged access with the least additional authority. No unprovided guardian law, staff administration authority or clinician power to revoke another person's proxy is inferred. Production revocation authority remains a governance/legal verification gate.

## C3 — retained source history and derived read representation

The results list includes authorized raw originals/corrections and caller-visible BMI/eGFR rows, including explicit cannot-compute rows. Its total counts those same visible rows before paging. Derived rows have deterministic 64-character SHA-256 IDs built solely from the method/version, analyte, visible input IDs/times and visible anchor. They can be retrieved through the same secured detail route; they are computed views, not persisted source Observations or independent release actions. Their source IDs/dates and view-scoped notice are shown.

Trends show each chain's latest visible source version; a concealed successor cannot suppress an original. A visible latest version with an ineligible status appears as a labelled gap with INPUT_STATUS_INELIGIBLE, not a revived earlier value. Original versions remain in the list/detail history. BMI/eGFR continue using the adopted latest eligible visible inputs. Compute BMI using the full visible history before applying the requested chart date window. Support individual BP component codes by projecting the matching component from an authorized parent.

Reason: keep correction history inspectable while the chart presents the caller's current visible versions, and give derived/missing-input results a consistent, privacy-preserving API and client representation. These are declared response conventions, not an assertion about what the interviewer privately expects. No hidden record or global successor state contributes to totals, IDs, calculations or metadata.

Adoption would approve C1–C3 only. Implementation, tests and the remainder of Group 5 are not complete merely because this proposal is adopted.
