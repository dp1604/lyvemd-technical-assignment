# Group 1 — proposed ingestion policies A v1

Status: PROPOSED by Codex; not adopted yet. Covers Tasks 11–13. These choices will be recorded as assessment implementation policies, not statements that the brief specified every detail. No application code has started.

## A1. Supported FHIR input

Recommend an explicitly scoped JSON FHIR R4 ingestion adapter: a transaction Bundle containing POST creates of Patient, Encounter and Observation. These cover the supplied model and required clinical scenarios. Validate every entry with HAPI's R4 structural/invariant validation plus application checks. Unsupported resource types, PUT/PATCH/DELETE/GET entry operations, conditional operations and unsupported clinical representations reject the entire Bundle; never silently ignore an unsupported entry. An empty transaction may succeed. This is not a general FHIR server.

This is a deliberate scope choice: the task does not enumerate every supported resource/interaction. We must prominently state the supported profile and limitation; candidate approval does not turn the subset into an employer-supplied requirement. R4 permits selected interactions, but that alone does not guarantee an assessor will accept every chosen restriction. [R4 REST/transactions](https://hl7.org/fhir/R4/http.html#transaction)

Validate all required model relationships, the supplied analytes and UCUM quantities, patient/encounter consistency and correction links. Numeric observations use Quantity; BP uses the two required components. Missing/inconsistent calculation inputs are kept distinct from structurally invalid input: accepted records can report a reason that a derived result is unavailable. Detailed demographic and clinical interpretation choices are in Package B.

Use a complete fullUrl map to resolve forward/backward urn:uuid: references. Resolve supported relative references to the current Bundle or stored local records; reject duplicates, wrong targets, unresolved or external/remote references and never fetch a caller-supplied URL. Server-assigned IDs are returned and references rewritten; resource.id/fullUrl do not authorize overwriting an existing row. The dataset generator retains returned ID mappings. [R4 Bundle references](https://hl7.org/fhir/R4/bundle.html#references)

For the local Encounter profile, require one principal Practitioner participant referencing a known seeded clinician and map that to practitioner_id. Proxy authority and clinician roster are explicit synthetic fixtures; an incoming RelatedPerson/relationship does not create authorization. Actual upstream formats remain unverified. Identity/header/ingestion permissions are in Package B.

## A2. The failed-transaction response conflict

The brief explicitly asks for invalid-entry identification in a transaction-response. R4 instead specifies a single OperationOutcome for a failed transaction. Both envelope rules cannot be satisfied at once. [R4 response rule](https://hl7.org/fhir/R4/http.html#transaction-response)

Recommend following the assessment's explicit envelope: for a parseable transaction rejected for an entry error, HTTP 400 plus Bundle(transaction-response). Give each offending entry a safe OperationOutcome with its zero-based entry index/expression; mark valid siblings as not committed, never as successful. Preserve request entry order. No clinical writes from that Bundle are committed. Document the intentional failure-envelope deviation prominently in the note and README.

Success uses HTTP 200 transaction-response with actual created identities. Malformed JSON, non-Bundle/wrong-type input and transport/header errors use a safe top-level OperationOutcome because entry indexes may not exist. Infrastructure failure returns a safe 5xx and no partial clinical transaction. Conflict cases such as key reuse use HTTP 409 with safe diagnostics.

Alternative: return only the normative OperationOutcome for failed transactions and explicitly depart from the assessment's requested envelope instead. I recommend the assessment-first choice above because it preserves their concrete requested behavior while disclosing the standards discrepancy.

## A3. Idempotency and append-only corrections

Require X-Idempotency-Key: 1–128 characters from A–Z, a–z, 0–9, dot, underscore and hyphen. Missing/invalid key returns 400. Bind each key to the verified test actor ID/role and SHA-256 of the exact received UTF-8 body. Revalidate the actor before replaying any cached response. Same actor/key/body returns the saved status/body/identifiers without new clinical effects; different bytes return 409. JSON formatting differences therefore count as a changed request. Keep keys for the assessment database lifetime.

Use database uniqueness/transaction coordination so concurrent identical requests execute once. Save completed successes and deterministic validation failures with the key; rejected Bundles commit no clinical rows. Temporary infrastructure/process failures are not stored as completed results and remain retryable. This is an explicit chosen replay contract, not a guarantee inherent in HTTP POST. [HTTP idempotency](https://www.rfc-editor.org/rfc/rfc9110.html#name-idempotent-methods)

Each amended/corrected result is a new Observation with fresh server identity and an explicit correction reference:

```json
{
  "status": "amended",
  "extension": [{
    "url": "urn:atlas:StructureDefinition:correction-of",
    "valueReference": {"reference": "Observation/original-id"}
  }]
}
```

Publish the project-specific extension's StructureDefinition in the submission. The target may also use a matching Bundle-local urn:uuid:. Require an existing same-patient/same-analyte target; reject self-links, cycles and competing branches. Only the latest correction-chain head may be corrected, with a database concurrency check. Preserve original contents and every version; both remain retrievable. Newly amended results start held for a new clinician review/release under Package B.

The extension is our declared convention, not an HL7-defined URL. Do not misuse derivedFrom for corrections: that field represents measurements used to derive a value. [R4 extension definitions](https://hl7.org/fhir/R4/defining-extensions.html), [Observation.derivedFrom](https://hl7.org/fhir/R4/observation-definitions.html#Observation.derivedFrom)

## Choice requested

Adopt A1–A3 unchanged as the ingestion policy package, or identify changes. Adoption applies only to the policies above; it does not approve Package B, future implementation, unexecuted tests or general FHIR conformance. Required validation/profile details and actual evidence remain traceable as implementation proceeds.
