# Group7 — minimal-client implementation and actual Codex checks

Original Tasks63–65 are complete. Actual candidate Task86 remains open; Group7 is therefore still in progress. The candidate has the running client and a separate guided worksheet. A policy adoption, AI source review, automated browser check or elapsed time does not count as that human gate.

## Implemented

One Spring Boot service serves plain HTML/CSS/JavaScript with no client build or external runtime dependencies. Results include values, display/machine units, full UTC timestamps, server ranges/text flags, source/BP components, held/release state, visible corrections and caller-visible calculation provenance. Calculated views inherit input release state and do not claim independent review. Missing calculations and no-applicable interpretation remain distinct from zero/Normal.

The local SVG trend and equivalent semantic table use server-selected points/order/interpretation. Inclusive date filtering, open windows, corrections, gaps and component codes use the required singular trend endpoint. Native controls, visible focus, a skip link, non-colour text/shape flags, labelled table regions and sensible detail/pager focus support keyboard access. Access logs use the server page and string event IDs, and explicitly distinguish checked test declarations from authentication. The D17 synthetic-only terminology conflict is prominently disclosed before clinical values.

Context changes immediately clear all tables, detail/provenance, chart and hidden views. Requests use no-store, same-origin relative GETs, no credentials and both cancellation and generation/identity guards. No browser clinical storage, service worker, remote assets, analytics, clinical URL-history serialization, payload console logging or dynamic HTML interpolation is used. Generic denials do not infer existence. The server still owns authorization and committed auditing.

Server decimal/range tokens are retained through standard JSON reviver source text, independently of approximate chart geometry. Missing browser support withholds affected clinical content explicitly. Underflow/nonfinite/unsafe bounds stay unplotted while the original text remains in the table; no false zero or invented clinical reason is introduced. This preserves API representation, not a claim of clinical accuracy to every displayed digit.

## Executed checks and attribution

Codex performed these checks in the actual local IAB against the preserved synthetic assessment database; a default narrow layout was observed, with no viewport override. The browser version was not exposed by the control inventory and is not guessed. A separate temporary tab verified the decimal update and was closed; the candidate-facing tab remains available.

| Check | Actual observation |
|---|---|
| Package/unit checks | Package passed163Java unit cases, zero failures/errors/skips. The later client-only repack reused those unchanged Java results rather than claiming a rerun. |
| Focused client checks | Eight Node24 cases passed: missing-vs-normal/zero; gap segments; constant/all-null geometry; nanosecond position/order; late context completion despite ignored abort; same-view/detail cancellation; decimal/range token preservation; underflow/overflow/true zero. |
| Served assets | Root HTML/CSS/both JS modules returned200, correct MIME types and byte-for-byte reviewed source. All four had no-store, self-only CSP and nosniff. Actual module initialization succeeded. |
| Equivalent creatinine/eGFR | p000 showed five rows, both canonical creatinine1mg/dL and equal eGFRs. Updated list/detail retained API text1.626251842848870166666666666666667mL/s/1.73m2, correct machine unit, method and visible source date. |
| Teen13/16 | Each actual proxy view showed one ordinary row; each patient-self view showed four. Hidden rows/counts were not reconstructed in the proxy UI. |
| Historical age | p003 showed11.5g/dL as Normal/range11–14 on30June, Low/range12–15.5 on1July. Rechecked after decimal hardening. |
| Correction history | p004 list retained4and5.8 task-potassium versions. Amended detail linked the original; the potassium trend contained one5.8 correction-head point. |
| Missing height | p005 showed weight74kg plus reasoned unavailable BMI, never0/Normal. BMI chart had one table row/zero numeric markers and an explicit unplotted-point description. Rechecked after hardening. |
| Long history/window | p006 showed94sodium table points and94SVG markers fromJan2019–Oct2026. Inclusive2026window showed10. A future window was explicitly empty. Reversed bounds returned the safe400message and left zero rows/empty chart content. Rechecked after hardening. |
| Age18/revocation | Expired proxy was unavailable; patient-self saw one. Individually revoked proxy was unavailable; a different retained proxy still saw one. |
| Held/critical | p009 clinician saw three; patient-self saw only the released6.3 task-potassium row. No workflow mutation UI was invented. |
| BP and pagination | p010 had151visible rows; first/next/previous pages were1–50/51–100/1–50. BP parent had no invented scalar/range/Normal, with separate122High systolic and78Normal diastolic components in the inspected row/detail. Systolic chart returned14component points. |
| Logs/roles | Patient-self and STAFF rendered actual log events, including revocation target. CLINICIAN log access and STAFF clinical access were generically denied. Counts grew after real reads;25-row log pages moved1–25→26–35, then1–25of36. These observed counts are a checkpoint, not fixed expected totals. |
| Keyboard | Enter opened detail/input and closed back to the original eGFR button; Tab moved Results→Trend and Enter activated it/focused its heading. Tab reached measurement, both dates and Show trend. Log table/page-size were reached; horizontal ArrowRight moved its scrollLeft to80. Pager actions focused their heading; skip link focused main. |
| Clearing/errors | Editing a context field left zero rows across all three tbody elements, empty detail/chart content and Not selected context. Unknown/expired/revoked contexts used the same safe unavailable wording. Browser400cleared old chart/table. |
| Browser console | Both bounded warning/error inspections were empty; no CSP/module exception was observed. This is bounded observed evidence, not proof that every browser/network failure was exercised. |

The duplicate/invalid-Bundle mandatory cases are actual POST/scenario evidence from Group6; p000's five-row client view is the resulting state, not an independent UI proof of atomicity. This client is intentionally read-only. Pure gate tests simulate late promise completion; an artificial browser-network delay or browser-triggered DB outage was not injected. Actual backend audit/dependency-failure checks remain their own evidence. No WCAG certification, production authentication, universal timing equivalence or candidate verification is claimed.

## Independent regression checkpoint — subsequently completed

While Task86 was pending, the permitted independent Group10 run passed163unit cases but its74integration cases had one failed concurrency-observation assertion and no errors/skips. The original two-second observation started before real prevalidation finished and counted unscoped advisory waiters; that test-design issue is established by source, not proven as the sole cause of the actual failure or a production idempotency failure. Original reports were preserved. A scoped two-phase real-validation/RR-snapshot harness retains real PostgreSQL locks/retries and all response/receipt/clinical/audit effect assertions. Its affected recheck passed six unit and23real ingestion integration cases, zero failures/errors/skips. A final whole-suite rerun is still required before claiming a fresh full PASS. Group10/Tasks82–85 remain open until their evidence is reconciled. No runtime lock/transaction limit or policy was relaxed.

Source review contributions and the root's changes/checks are retained in the [AI contribution record](../ai/outputs/group-7-client-audits.md) and AI_WORKING_LOG G7-001/G7-002. Client usage/limits are in [CLIENT](../CLIENT.md). Actual candidate worksheet answers and later regression outcomes must be appended truthfully rather than inferred.

Subsequent closure: the complete237-case backend rerun and separate added connection-provider fault/recovery IT passed. [Group10 evidence](group-10-regression.md) records actual counts, timing and scope. Tasks82–85 are complete. The candidate has actually reported historical haemoglobin, missing-height BMI list/chart and trend keyboard success; remaining Task86 worksheet steps are still open. G7-004 preserves this partial attribution.
