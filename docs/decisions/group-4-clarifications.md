# Group 4 clarifications — actual candidate decisions

These supersede only the points stated below. The reviewed A/B files and the exact access-rule instruction remain byte-identical. Adoption is a policy decision, not a passed implementation test.

## D-11 — HTTPS extension canonical identifiers

Codex found that the proposed `urn:atlas:StructureDefinition:...` spellings conflict with R4's explicit requirement that extension definitions use URLs rather than URNs. Codex recommended correcting the identifiers and supplying the definitions locally. The example namespace is an assessment identifier, not a claimed LyveMD/production deployment or ownership claim.

Current identifiers:

- `https://example.org/fhir/StructureDefinition/atlas-clinical-sex-context`
- `https://example.org/fhir/StructureDefinition/atlas-correction-of`

The extension contents and adopted historical-sex/correction policies are unchanged. The original package spellings are retained as historical proposals and superseded here. The standardized-creatinine boolean uses the same assessment namespace: `https://example.org/fhir/StructureDefinition/atlas-standardized-creatinine`; it encodes B4's explicit synthetic-feed standardization assertion and does not verify a real laboratory assay.

Actual question: “With that reasoning, do you adopt the HTTPS extension-identifier correction, preserving the original packages and recording the superseding decision?”

Actual answer: “Adopt the HTTPS correction (recommended)” — candidate message `01a17bcd-56b9-7b10-bd2c-1eba3a615450`.

The earlier answer in `01a17bcc-7d18-7233-9191-52b51cb381fe` asked for advice and valid reasoning; it was not adoption. The subsequent answer above is the approval.

Source: [FHIR R4 extensibility](https://hl7.org/fhir/R4/extensibility.html), requirement for extension definition identifiers. Definitions are bundled; validation must not fetch caller-supplied URLs.

## D-12 — unavailable stored references in POST transactions

Actual question: “For a POST /fhir Bundle referencing a missing or unauthorized stored patient, I recommend the same HTTP 400 transaction-response with an indexed REFERENCE_UNAVAILABLE error in both cases. This preserves the task's invalid-entry contract without revealing whether the patient exists; GET patient lookups and unknown actors retain the adopted 404 behavior. Do you adopt this clarification?”

Actual answer: “Adopt the POST-reference clarification (recommended)” — candidate message `01a17bd9-54e8-7a92-8259-6da13f7647a3`.

This reconciles A2's invalid-entry envelope with B1's non-disclosure policy: an unresolved/unauthorized stored patient reference has the same indexed failure shape in either case. General GET unavailable lookups and well-formed unknown/mismatched test actors keep their adopted 404 behavior. No patient-existence reason or data is echoed. The task-first failure-envelope departure from normative R4 remains explicitly disclosed.

Current root continuation request: `01a17bc7-5815-77d0-b6b3-a7b10a79634d`, “continue execution of next group”. Group 4 means Tasks 42–47 under the saved execution state.
