# Group6: supplied potassium-code conflict

## Verified source conflict

The supplied Technical Task §2.4 table explicitly assigns potassium code2947-0 and sodium2951-2. The adapter follows that literal table. Official LOINC defines [2947-0 as sodium in blood](https://loinc.org/2947-0), [2823-3 as potassium in serum/plasma](https://loinc.org/2823-3), and [2951-2 as sodium in serum/plasma](https://loinc.org/2951-2). Codex's source-checking AI worker verified those official pages on2026-10-27. This is a concrete terminology conflict; HAPI structural validity does not prove the intended clinical meaning is correct.

The brief does not specify the intended potassium specimen. Substituting2823-3 without confirming serum/plasma would add an unverified clinical assumption. Neither the task text nor candidate adoption can redefine the official LOINC meaning.

## Actual candidate adoption, D-17

Codex presented the source conflict and recommended retaining the supplied mapping only for this synthetic assessment, prominently documenting it and requiring verified clinical/specimen mapping before production. The actual question/answer is identified by question item `["request_user_input_async","call_804336dde2f84590812a1df7d016c23d",0]`. Candidate answered:

> Retain assessment mapping and prominently disclose conflict (Recommended)

This is a candidate-adopted assessment handling decision, not an employer clarification or clinical approval. It preserves the supplied input/code/range convention for the synthetic test without silently inventing a replacement.

## Implementation and delivery consequences

- Keep the task's code2947-0 convention and potassium ranges solely in the declared assessment adapter. Do not claim that this is valid general LOINC potassium interpretation or full semantic FHIR conformance.
- Prominently flag this discrepancy in the Specification and Architecture Note, ingestion/clinical contracts, README and forthcoming client. The client must identify the supplied potassium mapping as an assessment convention rather than hide the issue.
- Production integration remains blocked on confirmation of specimen, verified terminology mapping and clinical-owner approval. A real clinical feed's sodium2947-0 must never be silently treated as potassium. The synthetic adapter is not suitable for real clinical use unchanged.
- Preserve the source task and this actual adoption. Do not rewrite the historical table, earlier immutable instructions or already-recorded receipts. No new clinical threshold, assay fact or specimen is inferred.

The separate p013 validation failure is still under investigation. Its isolated resource and exact initial validation prefix passed local HAPI/strict-parser diagnostics; this terminology conflict has not been established as its cause.
