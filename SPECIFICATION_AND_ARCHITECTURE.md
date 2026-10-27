# Specification and Architecture Note

Status: outline only. Candidate-adopted Group 1 assessment policies are recorded separately; the remaining note content is still an outline. The supplied Technical Task and house rules govern the note; ambiguity must be documented and resolved rather than guessed.

## Section 1 - For Dana

Pending: one-sentence goal; release 1 inclusions and exclusions with reasons; conflicts with the house rules; vendor recommendation; build order, rough product estimates and first cut; testable acceptance criteria.

## Section 2 - For the engineering team

Pending: decisions and reasons for production-scale storage/indexing; tenfold-scale failure and detection; all house-rule enforcement and auditor evidence; clinical units, eGFR governance, versioning, oracle and invalid input; operations paragraph covering restore proof, three alerts and dependency failure; explicit ambiguities and limitations.

### Clarifications and adopted assessment policies

The [decision register](docs/DECISIONS.md) and its immutable reviewed/adopted packages form the current policy basis, not a completed implementation or completed note. Explicit ambiguities include the failed-transaction envelope, supported FHIR surface, replay/amendment matching, log-role rights, confidentiality time basis, historical clinical sex/BMI, release/critical representation, UCUM display-versus-machine notation, and same/own-transaction wording.

The assessment-first failed transaction-response intentionally differs from normative R4's failed-transaction OperationOutcome. The bounded POST-create adapter and correction context limits are stated limitations, not employer-specified restrictions. BMI is caller-visible-data scoped with dates; hidden/absent inputs and successors must be indistinguishable. Effective-dated clinical-sex assertions are explicit synthetic inputs rather than inferred administrative gender. The task's per-second eGFR display label is preserved alongside machine UCUM mL/s/{1.73_m2}. Real upstream, assay, clinical-workflow and legal/vendor facts remain unverified external gates.

Release remains an entitled clinician action; no invented thresholds/notifications are added. Each content-read action couples reads and audit in one transaction; audit failure withholds content, and total database outage is not claimed to provide impossible same-database denied-event durability. Final note drafting and reconciliation remain Groups 8 and 13.
