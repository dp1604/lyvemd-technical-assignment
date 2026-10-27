# Requirement coverage

| Requirement | Implementation and evidence |
|---|---|
| Part 1 | [Two-section note](../SPECIFICATION_AND_ARCHITECTURE.md): scope/vendor/order/acceptance plus architecture, scale, enforcement, clinical ownership and operations. |
| R1 | [FHIR contract](FHIR_INGESTION.md): atomic validation, references, actor-scoped idempotency and append-only corrections; supported subset and response-envelope departure disclosed. |
| R2 | [Read paths](READ_PATH_INVENTORY.md) and [visibility](RESULT_VISIBILITY.md): stored rights on every route/count/derived view. **Timing indistinguishability remains unestablished.** |
| R3 | [Clinical rules](CLINICAL_RULES.md), [oracle](evidence/clinical-oracle.md) and [run instructions](../README.md): canonical creatinine, adult race-free 2021 CKD-EPI, chosen LOINC and sources. |
| R4 | Complete task table, effective-time context, component ranges and distinct unavailable/no-applicable flags in [clinical rules](CLINICAL_RULES.md). |
| R5 | [Audited reads](RESULT_READS.md): synchronous read/audit transaction, denied events and withholding on audit/commit failure. Database-outage self-audit limit disclosed. |
| R6 | [Client](CLIENT.md): list, chart with equivalent table, log, textual flags and keyboard access. Automated checks and partial personal walkthrough are separated in [verification](VERIFICATION.md). |
| Dataset | [Generator and loader](DATASET.md): deterministic 100 patients/106 encounters/10,000 sources, all eight required cases, actual replay and invalid rejection. |
| Part 3 | [Static review](../PR_REVIEW.md): location, defect, clinical consequence, remedy, blockers/preferences, origin and checking. Attachment code was not run. |
| Submission | [README](../README.md), authentic history, dataset script, [AI log](../AI_WORKING_LOG.md), R1–R6 delegation and exact access instruction. Submission is a separate candidate action. |

[Verification](VERIFICATION.md) states actual measurements and limits. This summary replaces the detailed working matrix, retained in Git history at 356502c7:docs/REQUIREMENT_MATRIX.md.
