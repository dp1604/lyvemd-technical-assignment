# Group3 — actual candidate clarifications

These extend the adopted A/B assessment profile; the immutable packages themselves are unchanged. They are candidate choices where the source is silent, not employer clarifications or universal clinical/legal rules.

## D-09: Derived-only BMI/eGFR input

The initial answer asked Codex what the interviewer would expect and requested a recommendation; it did **not** adopt a policy. Codex recommended deriving both values from the required inputs and rejecting upstream BMI/eGFR Observations with a clear invalid-entry/whole-Bundle response, disclosing the input-profile restriction. Source message01a17bb0-f0e2-7933-b12e-2d966b970ec0.

The later actual adoption question was: "Do you adopt this assessment input policy: Atlas derives BMI/eGFR from the required inputs; an incoming BMI/eGFR Observation causes a clearly identified whole-Bundle rejection; the restriction and its rationale are disclosed?"

Actual candidate answer: **"Adopt derived-only policy (recommended)"**, source message01a17bb1-9678-73f0-89bd-2fa90c9e39df.

Reason: the task explicitly requires local derivation from converted creatinine and weight/height. A single authoritative calculation/provenance path is readily testable and defensible. FHIR itself can carry derived results; rejecting these inputs is our bounded adapter profile, not a FHIR prohibition or knowledge of an interviewer's unstated preference. Supporting independently labelled upstream calculations would require additional reconciliation/provenance semantics. The clinical normalizer now rejects them; actual whole-Bundle/entry response enforcement remains Group4.

## D-10: Leap-day completed age

Actual question: "For someone born on 29 February, when should completed age advance in a non-leap year? The brief does not specify this, and the choice affects age-based ranges and access boundaries."

Actual answer: **"1 March: use ISO calendar completed-year arithmetic consistently (recommended)"**, source message01a17bb0-f0e2-7933-b12e-2d966b970ec0.

Use completed ISO calendar years at the observation's UTC date for clinical interpretation; later proxy-age enforcement must use the same completed-year convention at the request's UTC date. This is a reproducible assessment convention, not an asserted jurisdictional age rule. Tests cover Feb28/Mar1 immediately around the eighteenth year of a Feb29 birth.

Both source messages belong to chat01a1782c-e9ba-7022-b8ea-958dae51df21. Exact access-rule instruction and A/B hashes remain unchanged. No candidate-run calculation check or clinical sign-off is implied by these answers.