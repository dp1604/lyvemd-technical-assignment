# Access-log read contract

`GET /patients/{patientId}/access-log` uses the same audited action boundary as the secured detail route. It accepts the test actor headers defined in the adopted access instruction. These declarations are impersonatable test inputs; a matching roster pair is not production authentication.

A known STAFF actor or the known self PATIENT can read an existing patient's log. CLINICIAN and PROXY declarations cannot use this route, including when they can read clinical results. The entitlement query needs only patient existence. Well-formed unknown/mismatched actors and unavailable or unauthorized targets receive the shared404 envelope. Malformed actor headers receive400. These expected denials commit their categorical audit.

Query parameters are `offset` (default0, inclusive0–1,000,000) and `limit` (default50, inclusive1–200). Unsupported, duplicated, empty or malformed parameters receive an audited400 without echoing input. Parse after actor verification but before target entitlement, so query validity does not test patient existence for a known actor. All queries are parameterized.

The response has `items`, visible patient-scoped `total`, `offset` and `limit`. Each item contains the required actor ID/role, patient ID, action, outcome, source and UTC time, plus audit ID and the checked-test-roster marker. IDs are decimal strings to preserve bigint precision in JavaScript. Nullable identities remain null. No observation ID, analyte, measurement, request payload, full query, exception or clinical free text is added to these categories.

Items sort by UTC `at` descending then numeric audit ID descending. Count and page are materialized from one Repeatable Read snapshot, then exactly one audit for this log-read action is appended in that same transaction. The response excludes its own newly appended event; the next request can see it. This prevents recursive log reads while retaining attribution. Offset pages across separate requests may move when new events are appended; this is not a frozen multi-request export or cursor contract.

The action commits before HTTP receives its body. An audit insert or commit failure withholds content and returns the safe unavailable envelope. Complete loss of the audit database cannot persist an event in that same database; only a categorical operational signal is possible. No-store/privacy headers apply. These are assessment boundaries, not a claim of authenticated identity, universal timing indistinguishability, clinical certification or complete production compliance.

Individual revocation commands and any additional target-proxy field remain pending the separate C2 decision and implementation. This contract does not grant STAFF revocation authority.
