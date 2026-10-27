# Engineering, privacy and security working rules

Current human contribution: [candidate decisions, independent review and partial execution](CANDIDATE_REVIEW.md). Original AI execution evidence remains scoped to its recorded checkpoint.

Status: working rules with implemented controls and bounded evidence linked below; current human contribution is recorded separately. This assessment is not a claim of regulatory certification or production readiness.

## Sources and scope

The supplied Technical Task, its house rules and contract govern the exercise. The candidate requires sound engineering, privacy and security practices and an explanation they can defend. Any ambiguous or believed-wrong requirement must be recorded in the specification and raised with the candidate before choosing uncertain behavior.

The brief deliberately uses untrusted X-Test-Actor-Id and X-Test-Actor-Role headers and excludes real authentication. Preserve that interface, verify its inputs and enforce stored entitlements server-side. Explain the production trust boundary without adding excluded identity/login or cloud/container work. Do not assert an unprovided legal jurisdiction, certification, security assurance level or clinical policy. Follow [scope boundaries](SCOPE_BOUNDARIES.md): exclusions do not waive authorization, audit, clinical/privacy rules, the required production architecture note or reproducible local operation. Native mobile, firmware and the interview IoT scenario are separate agreed non-additions.

## Rules for implementation and review

- Keep responsibilities clear and use the smallest architecture that satisfies the contract. Centralize authorization, clinical rules and transaction boundaries so alternate read paths cannot bypass them.
- Deny unauthorized access server-side. Apply the same visibility predicates to list, detail, trend, totals and pagination. Test patient, clinician, proxy, STAFF, revocation, adolescent boundaries and hidden/missing behavior.
- Couple each clinical read and its required audit write in one transaction. Persist denied-read events according to the documented policy. Do not return clinical content when required audit persistence fails.
- Use synthetic records only. Minimize stored/returned data, keep results out of ordinary logs, and avoid browser persistence, third-party analytics or external client asset requests for clinical content.
- Keep secrets in local configuration/environment inputs. Commit only safe examples. Use parameterized database access, bounded and validated inputs, controlled errors and least-privilege application database credentials. Separate migration privileges from runtime privileges when implementing the database setup.
- Preserve clinician release, non-silent critical handling, individual revocable proxies, canonical units, amendment history, effective-time ranges, non-fabricated derivations and the outbound-data rule. Document unspecified workflow/threshold policies and obtain a decision rather than inventing them.
- Pin dependencies and retain reproducible migration/data scripts. Verify clinical equations and unit handling against primary sources and an independent oracle. Verify failure cases as well as successful ones.
- Use semantic HTML, visible focus, keyboard operation and textual/symbolic flags. Charts need an accessible representation of their values; color alone is insufficient.
- Keep meaningful, authentic commits. Inspect staged content for credentials, private data and accidental tool/build files. Run checks appropriate to each actual change and retain honest outcomes.
- Keep the specification, README, tests and evidence synchronized. Distinguish implemented behavior, proposed production design and limitations; never convert a forecast or static inspection into a measured claim.

## External verification references

These references support engineering verification without replacing the assignment or expanding its build scope:

- [OWASP ASVS 5.0.0](https://asvs.owasp.org/): select and trace applicable application-security requirements as controls are implemented. No ASVS level or full conformance is claimed at this stage.
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/): use applicable testable accessibility criteria, including keyboard access and use of color. The assignment's explicit client requirements remain mandatory; full WCAG conformance requires broader verification.
- [HL7 FHIR R4](https://hl7.org/fhir/R4/): use version 4.0.1 for the required input. Record and resolve any assessment-versus-standard discrepancy explicitly before implementing it.

Each substantial decision has its reason, source, affected requirements and bounded evidence recorded; later changes require the same traceability. The [requirement-to-evidence matrix](REQUIREMENT_MATRIX.md) establishes baseline coverage in Task 8; update it with actual evidence through reconciliation. The [decision register](DECISIONS.md) records actual Group 1 adoptions and their limitations; implemented behavior and its bounded evidence are linked from the reconciled matrix. Follow the [group plan](EXECUTION_PLAN.md) and [state](EXECUTION_STATE.json) for continuation and bounded recovery.
