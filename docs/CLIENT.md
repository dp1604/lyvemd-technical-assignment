# Minimal same-origin client

Current human contribution: [candidate decisions, independent review and partial execution](CANDIDATE_REVIEW.md). Original AI execution evidence remains scoped to its recorded checkpoint.

Open the root URL after the normal start command. Java serves `index.html`, `styles.css`, `app.js` and `client-model.js` directly. There is no client installation or build command, remote chart library, font, analytics, service worker or browser storage. All requests are same-origin GETs. The existing no-store, referrer, framing, permissions and self-only CSP headers apply to the page and APIs.

## Use and boundaries

Enter a synthetic Patient ID, a known synthetic actor ID and one exact role (PATIENT, PROXY, CLINICIAN or STAFF), then apply the test context. Generated IDs are local operational outputs in the ignored dataset load manifest, not hardcoded application defaults. A patient-self context uses that patient's server UUID as both patient and actor ID. Clinician/proxy/staff declarations must match the prepared roster and relationships. These are impersonatable assessment headers, not authentication. The server decides each clinical/log entitlement; navigating to a view grants nothing.

Results show server values, display units, full UTC timestamps, ranges, text flags and release state. Blood-pressure component values/ranges/flags remain separate from the nonscalar parent. Source details expose only server-returned visible correction links. Calculated details preserve the method, notice, exact source IDs/dates and inherited release state; an unavailable calculation has a reason, never zero or Normal. No client clinical calculation, demographic inference or authorization rule is added.

The trend view accepts the required measurement and optional inclusive UTC calendar dates (the API additionally supports exact timestamp bounds). Server point order is preserved. A local SVG plots relative nanoseconds and uses circle/triangle/square markers with a visible textual legend; missing numeric points break lines. Exact unrounded response values, full timestamps, ranges, text flags and gap reasons appear in an equivalent semantic table. Axis labels may be rounded for display. Blood pressure uses its scalar component codes. The persistent terminology notice discloses the assessment's2947-0 conflict before clinical values; this is synthetic-only and not a verified clinical mapping.

Access log shows attributable server events and string event IDs without precision loss. Roster-checked declarations are explicitly not authenticated identities. Only self PATIENT and STAFF can read logs under the adopted policy. The new event for a log read appears on a later request; offset pagination is not a frozen export.

## Privacy and request lifetime

Editing any patient/actor/role field immediately invalidates the context and clears all clinical, detail, provenance, chart and log DOM content, including hidden views. Navigation clears prior views; filter edits clear their old trend. Abort controllers are paired with per-channel identity and context-generation checks after asynchronous steps, so a late completion cannot restore a prior context or overwrite a newer read. Closing detail cancels that request. Responses are never written to storage, URL history, telemetry or console. IDs are placed only in the required same-origin API route/header, not form query parameters. Page-hide handling also clears fields/content; it is not a universal browser-memory erasure guarantee.

Loading, empty, generic denial, invalid query, unavailable and network/30-second timeout states are explicit. A failed read clears content and displays only status-based wording, never raw server/error text or an inferred reason for a404. Dynamic content uses text nodes rather than HTML interpolation. Browser controls cannot establish real identity or replace the server's audited transaction boundary.

## Keyboard and checks

Native labelled form controls, buttons, view navigation, table regions, a skip link and visible focus styles support keyboard use. Hidden views have no reachable controls. Detail opens with focus in its region and closes to the original row control, including after following source/correction links; fallback is the Results heading. Pagination moves focus to the relevant heading before disabling its button. Text and marker shape distinguish High/Low/Normal/No applicable flag independently of colour. The chart's equivalent table supplies exact values and reasons.

Optional focused checks require Node24+ and no downloaded packages:

```powershell
node scripts/client/client-model.test.mjs
```

Eight meaningful cases cover missing-vs-normal/zero, null-gap segments, constant/all-null geometry, nanosecond positions/order, late context completion despite ignored abort, and independent new-read/detail cancellation. The root package also passed all163 Java unit cases. These checks and source review do not prove browser behavior, full accessibility conformance or human verification. Actual browser evidence and the candidate's separate Task86 check are recorded at the Group7 milestone. Production authentication, provisioning, workflow screens, mutation endpoints, exports and mobile applications remain outside this minimal-client build.

## Decimal text and chart representability

The standard [ECMAScript JSON.parse reviver source context](https://tc39.es/ecma262/multipage/structured-data.html#sec-json.parse) preserves the server numeric token separately for value/low/high under a Symbol key. Display values and ranges retain that text instead of silently rounding through binary floating point. IDs stay strings and page counts stay bounded integers. A browser lacking reviver source support fails closed for these clinical numeric reads with an explicit capability message; actual support must be observed, not inferred from a guessed version. The optional pure checks use the locally verified Node24 runtime.

Chart geometry remains approximate. A nonzero decimal that underflows to binary zero, a nonfinite approximation or overflowing chart bounds is left unplotted, while its exact text stays in the table. Such a gap is a display representability limit, not a newly inferred clinical missing-data reason. Table interpretation/flags are still returned by the server. Two additional checks preserve long decimal/range tokens and cover underflow/overflow/true-zero handling. The first six client cases remain in the eight-case suite; they are not eight additional tests. Actual IAB rechecks passed after this hardening: original decimal text in list/detail, missing-height gap,94-point chart/10-point window, rejected-window clearing and age-boundary flags. See the Group7 evidence. Candidate86 remains partial: later independent review is reported, while personal execution and individual client checks remain partial.
