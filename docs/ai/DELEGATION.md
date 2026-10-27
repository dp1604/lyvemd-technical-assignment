# Technical ownership and AI delegation

I retained technical accountability for all six requirements. I used Codex to implement the application, draft documents, research primary sources and execute automated checks, while I directed the work, decided unresolved policies, reviewed the results and accepted the final recommendations. I also completed the personal checks recorded in [my review summary](../CANDIDATE_REVIEW.md).

| Requirement | Delegated execution | Work I retained | Reason |
|---|---|---|---|
| R1 — FHIR ingestion | Validator, transaction/idempotency/correction code and automated tests | Contract decisions, supported-profile choices, review and acceptance | Closely related FHIR and database changes benefit from coordinated implementation; policy and trade-off decisions require an accountable owner. |
| R2 — Server-side access | Shared access/visibility code and adversarial route tests | Access-policy decisions, boundary review and acceptance | A single implementation avoids inconsistent enforcement; I retain the decision on what each actor may see. |
| R3 — Units and eGFR | Source research, conversion/equation code and oracle tests | Review of the chosen method, applicability, provenance and limitations | Traceable numerical checks support the decision. Production clinical sign-off requires an appropriate clinical owner. |
| R4 — Ranges and flags | Table implementation, historical-context handling and boundary tests | Interpretation policies, review of examples and acceptance | Automation covers repetitive table combinations; I retain responsibility for the meaning and disclosed assumptions. |
| R5 — Read auditing | Transaction wrapper, denial auditing and failure/concurrency tests | Transaction-policy decisions and review of failure behavior | The read and its audit must be implemented together; the consequences of withholding a response need an explicit decision. |
| R6 — Minimal client | Client code, automated model tests and browser checks | Client choice, personal walkthrough cases, usability feedback and acceptance scope | A same-service client keeps the build small. My manual checks complement automated checks; the unfinished checks remain disclosed. |

The original work agreement was that Codex writes the code and I review decisions and verify the result. The retained/delegated distinction above describes technical responsibility and execution; it does not claim that I manually wrote the generated implementation or personally ran every automated check. The [working log](../../AI_WORKING_LOG.md) records revisions and reasons, and the [exact access-rule instruction](ACCESS_RULE_INSTRUCTION.md) remains unchanged.
