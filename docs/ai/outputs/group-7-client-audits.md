# Group7 AI contribution record

The continuing read reviewer (thread01a17c28-82d1-7861-be76-af23874491ef) reviewed exact API/clinical/privacy/request lifetime contracts. The workflow reviewer (thread01a17c28-b2a2-7351-ad48-88e131b40511) drafted HTML/CSS and reviewed DOM/keyboard structure. Root authored JS/model/tests, integrated their concrete fixes and ran the actual tests/build/browser work. These are AI contributions, not human-origin findings or candidate verification. The following retained source-review outputs describe their actual bounded checks and still-open runtime gate at the time of review.

## Workflow source review
# Static client integration review

Reviewed root's scratch `app.js` and `client-model.js` against the drafted HTML/CSS and DOM contract. No application, browser, build, database or Git operation was performed. This is a source review, not keyboard/browser acceptance or candidate Task 86 verification.

## Changes made by this lane

- Changed the scratch HTML script declaration to `type="module"`, as required by its ES-module import/export use.
- Added a visible chart legend matching the implementation's marker vocabulary: Normal ○, High ▲, Low ▼ and No applicable flag □. It uses existing flag styles and requires no inline script/style or external asset.

## Findings and root corrections rechecked

1. A detail input/correction link was removed by `closeDetail()` before being stored as the focus-return trigger. Root now captures/preserves the original connected outer-row trigger while drilling through details and provides a results-heading fallback. The revised source avoids returning focus to the detached nested button.
2. Paging disabled the focused Previous/Next control during reload without an explicit focus destination. Root now focuses the view heading before reload; all three headings receive `tabIndex=-1` during initialization.
3. Trend filter edits now cancel and clear stale chart/table content on both `input` and `change`, rather than waiting only for a committed change.

The three corrections are present in the reread source. No further blocking DOM or keyboard-focus mismatch was found in this bounded pass.

## Contract and privacy checks

- All 30 literal `byId('...')` references resolve to existing HTML IDs; dynamic context/view/paging IDs match the agreed contract too.
- Result, trend and log rendering uses the expected six/four/six table cells. Navigation uses native buttons, `hidden` panels and `aria-current`, with labelled headings and native form controls.
- The SVG title/description are retained while drawing into the dedicated content group. Marker shapes agree with the visible legend and text flags. The equivalent table exposes exact timestamps, values, ranges and reasons; null values remain gaps rather than zero or connected numeric points.
- `RequestGate` invalidates/aborts requests and checks token identity before publishing responses. Context edits clear old content immediately. Safe text-node construction, encoded path/query components, same-origin requests and no browser storage preserve the intended assessment boundary.
- Derived provenance, source components and visible correction links use backend response fields; no authorization, clinical range, critical threshold or workflow state is invented by the presentation layer.
- The prominent D-17 synthetic-only terminology notice and impersonatable test-declaration explanation remain visible. The client adds no login, provisioning, notification or workflow endpoint.

Actual desktop/mobile rendering, chart behavior, keyboard operation, error/empty cases and guided candidate verification remain to be executed and recorded by root/candidate.


## API and privacy source review

# Group 7 API/client contract and browser verification

Source review completed against the original R6 paragraph, `docs/RESULT_READS.md`, `docs/ACCESS_LOG.md`, actual read controllers/services, `ReadResults`, `ResultRendering`, `ClinicalModels`, `Analyte`, `ReadQueries`, `ClinicalViewRepository`, `AuditedReadAction`, `ActorHeaders`, `PrivacyHeaders`, and `SafeErrors`. No application, browser, build, test, database or Git operation was executed by this worker. Client source audit is pending root's `app.js`; the checklist below is proposed verification, not executed evidence. Candidate's real human Task86 check remains pending.

## Actual API shapes

| Route | Actual success envelope and behavior |
|---|---|
| `/patients/{id}/results?offset=&limit=` | `{items,total,offset,limit}`. Server sorts exact effective descending, then ID ascending; total includes visible raw history and derived/unavailable rows. Page defaults0/50; offset0–1,000,000; limit1–200. |
| `/patients/{id}/results/{obsId}` | A single raw or derived DTO, matching the authorized list collection; no query parameters. Source UUID and virtual64hex IDs both resolve through this route. |
| `/patients/{id}/trend?code=&from=&to=` | Singular `trend`; `{code,points,total}`. Code required; empty optional bounds must be omitted. Inclusive bounds: date means complete UTC day; offset timestamp means exact instant. Points exact effective ascending, then ID. Total is returned point count. |
| `/patients/{id}/access-log?offset=&limit=` | `{items,total,offset,limit}`; same page bounds. Items newest UTC time then numeric audit ID descending. Current request's own event is excluded; the next request can see it. Offset pages are not a frozen export. |

Raw list/detail DTO keys are `id,code,value,unit,unitCode,effective,status,range,flag,flagReason,correctionOf,correctedBy,releaseState,criticality,sourceCritical,components,derived:false`. `range` is null or `{low,high}` with no embedded unit. `value` and correction predecessor can be null. Each BP component has `code,value,unit,unitCode,range,flag,flagReason`.

Derived DTOs carry the common identity/value/unit/date/status/range/flag fields plus `derived:true,computed,cannotComputeReason,sources,method,notice,sourceReleaseStates`. Each source is `{observationId,effective}`. Each source release entry is `{observationId,releaseState,criticality}`. Correction predecessor is null, successors/components empty, release state `INHERITED_FROM_VISIBLE_INPUTS`, and criticality null. `sourceCritical` is inherited from selected visible inputs. These are caller-visible-input views, not separately persisted or clinically released source records; preserve their notice and exact source IDs/dates.

Flags are exactly `LOW,NORMAL,HIGH,NO_APPLICABLE_FLAG`. The literal is not `NOT_APPLICABLE`. Reasons include `NONE,NO_REFERENCE_RANGE,COMPONENT_RESULT,VALUE_UNAVAILABLE,AGE_UNAVAILABLE,AGE_INCONSISTENT,AGE_NOT_APPLICABLE,SEX_UNAVAILABLE,HEIGHT_UNAVAILABLE,WEIGHT_UNAVAILABLE,HEIGHT_AMBIGUOUS,WEIGHT_AMBIGUOUS,HEIGHT_NONPOSITIVE,WEIGHT_NONPOSITIVE,CONTEXT_INCONSISTENT,CREATININE_NONPOSITIVE,CREATININE_NOT_STANDARDIZED,NUMERIC_UNREPRESENTABLE,INPUT_STATUS_INELIGIBLE`. Show an understandable label without losing the code/reason; never translate unavailable into normal or zero.

BP parent code is85354-9; component codes8480-6/8462-4. Parent scalar/range may be null with `COMPONENT_RESULT`; display component values/ranges/flags. A BP component trend projects its component fields, retains the parent's `id`, adds `parentObservationId`, and has empty components. Raw latest-visible ineligible trend heads have null value/range with `INPUT_STATUS_INELIGIBLE`; do not revive an earlier value. Derived null values are explicit cannot-compute points. Server already selects visibility, correction heads and prior-window inputs; the browser must not recalculate these decisions.

Access-log item keys are `id,actorId,actorRole,patientId,action,outcome,source,at,actorRosterVerified,targetProxyId`. Audit IDs are decimal **strings**, preserving bigint precision. Identities and targetProxyId may be null. `actorRosterVerified` indicates only a checked test declaration, not authenticated identity.

## Actual access and failure boundaries

- Send exactly one `X-Test-Actor-Id` and `X-Test-Actor-Role`; accepted role spellings are PATIENT/PROXY/CLINICIAN/STAFF. Header declarations are untrusted and impersonatable. A roster match is not a login, verified identity, real authentication or production access control.
- Clinical APIs require known self, active eligible proxy or an encounter clinician; STAFF is denied. Patient/proxy views require the clinician-release/critical gates; proxies12+ additionally omit protected encounters and end at18. The server governs these rules; hiding a tab does not authorize or enforce access.
- Log API permits known self PATIENT and STAFF only. CLINICIAN/PROXY must receive denial even if clinical access works.
- Hidden, absent and unauthorized targets share404 `OperationOutcome` diagnostics “Not available.” Malformed declarations/queries get400 “Request cannot be processed.” Required read/audit failure returns503 “Service unavailable.” Do not infer why a404 occurred, disclose existence, or leave content from a prior successful request visible after denial.
- Every GET shares the audited action and commits before body delivery. Browser tests must not claim universal timing equivalence, production authentication, or whole-system compliance.
- Server headers include no-store/no-cache, nosniff, DENY framing, no-referrer, denied camera/microphone/geolocation, and a self-only script/style/connect/image CSP. Inline scripts/styles/event attributes and remote fonts/chart libraries conflict with this design; use local assets and DOM-created SVG/classes.

## Focused browser verification checklist

1. **Startup/context:** initial page explains test declarations and uses no real login language. Choose actual synthetic IDs from the ignored dataset manifest, not guessed IDs. Verify entered actor/role/patient and the active view are clear. Fetch only relative same-origin routes and use no-store behavior. No localStorage/sessionStorage, service worker, external assets, clinical URL-history persistence, console payloads or telemetry.
2. **List and pagination:** a clinician opens a real background patient with scalar, component and derived rows. Verify value, explicit unit, date, range and text flag. Height/weight “no applicable flag” must look and read differently from NORMAL; LOW/HIGH must remain textually identifiable without colour. Verify first/next/previous states and counts from the server, empty page beyond total, and safe empty-result rendering. Source history includes both correction versions.
3. **Details and corrections:** open a source row by keyboard. Compare detail fields to its list row. For p004, both original/correction remain retrievable, `correctionOf`/visible `correctedBy` open the correct API detail, and no unshown global successor is inferred. BP detail renders both component ranges/flags with no invented parent scalar. Show held/released, reviewed criticality and source-critical signal as distinct states.
4. **Derived provenance:** p005 BMI shows no computed number and `HEIGHT_UNAVAILABLE`, not0/NORMAL. A computable BMI/eGFR shows method/notice, exact source IDs and source dates, selected source restrictions, and inherited release wording. Clicking a derived ID uses the normal detail route. Source links issue new authorized detail reads; never treat an ID or caller-side derivation as permission.
5. **Real trend:** use actual sodium p006 multi-year series and compare chart/table point count and dates with returned points. Choose code8480-6 and8462-4 for BP component trends. Apply valid date bounds; omit empty bounds. Verify a narrow empty window, one-point series and null/cannot-compute data all render safely. Mixed numeric/null points must split the line at null, never connect across a gap or coerce null to0. Preserve server point order; chart Date geometry may be milliseconds, but displayed ISO/table/provenance text must preserve exact source time. Do not recalculate flags, hide returned rows or silently truncate the series.
6. **Accessible chart alternative:** chart has a useful accessible name/description including analyte/unit, and its actual points are available in a semantic data table with dates, values/gaps, reasons and flags. A visual tooltip or coloured line alone is insufficient. Empty/all-gap chart has a textual explanation.
7. **Log view:** self PATIENT and STAFF see paginated attributable rows including UTC time, actor/role, action/outcome/source and nullable revocation target. IDs stay strings; nullable identities remain explicit. CLINICIAN/PROXY log attempts fail generically. Refresh can add a prior ACCESS_LOG event; do not expect a constant total or the current event inside its own response.
8. **Rights contrasts:** patient sees only released inputs/results; compare p009 clinician versus self for held/critical records. Test teen proxy protected omissions and expired/revoked proxy denials using actual manifest context. STAFF gets log only. Known but unrelated actor, unknown actor, wrong role, missing/invalid declarations, unavailable patient and unavailable detail all clear relevant content and show only the appropriate generic error.
9. **Context/request races:** start a request then edit actor/role/patient before it completes. Immediately invalidate and clear results, detail, provenance, trend/chart/table and logs. Abort pending work and also check a context-generation token after every asynchronous completion; abortion alone is insufficient. Repeat rapid same-context page/filter/detail changes with per-view request tokens so a slower old response cannot overwrite newer intent.404/503/network failure must not resurrect stale clinical content. Inspect the DOM, not only visible panels, for retained prior-context data.
10. **Injection/failure:** render all API/user text through text nodes; no dynamic innerHTML, event-handler strings or arbitrary HTML notices. Do not display raw server body/exception on error. Verify safe400,404,503/network-error states and that recovery requires a fresh successful read. Existing API fault tests supply backend audit-failure proof; browser checks must not fabricate a database outage result.
11. **Keyboard every view:** Tab/Shift+Tab reach and visibly focus context controls, view navigation, page controls, analyte/date inputs, result/detail/correction/source controls and the log. Enter/Space activate native controls. Labels, table headers, headings and live loading/error announcements have semantic associations. Hidden panels contribute no reachable controls. Focus moves sensibly to loaded detail/errors and returns sensibly when leaving; no trap or mouse-only interaction. Check zoom/narrow viewport for readable tables/chart and visible focus.
12. **Evidence and candidate gate:** record actual browser/version, viewport, synthetic role/case, observed result and limitations. Save appropriate screenshots of list/trend/log/detail states without credentials. Automation and source checks support Tasks63–65; the candidate's actual manual keyboard/readability explanation and any amendments remain a separate Task86 gate. Do not claim WCAG certification or candidate approval from an automated/source check.

## Client source audit and resolved findings

Read the complete scratch `app.js`, `client-model.js`, six model/gate tests, and the corresponding HTML/CSS. Re-read the installed canonical resolutions without executing the client or tests. No remaining source-level blocker was found in this focused API, presentation and request-lifecycle review.

Three concrete findings were raised and resolved by root/the HTML worker:

1. **ES module initialization:** the initial HTML loaded a classic deferred script while `app.js` line 1 contains an import. Canonical `index.html` line 10 now uses `type="module"`. This removes the source-level initialization mismatch; actual browser startup remains to be observed.
2. **Detail focus restoration:** initially, following a correction/source link replaced the original results-row opener with a button that `closeDetail` removed from the DOM. Canonical `app.js` lines 211–214 now preserve the original opener during navigation within `detail-body`; lines 32–34 restore that connected opener or fall back to the Results heading. Line 365 makes all view headings programmatically focusable. Root also focuses the relevant heading before pagination can disable its trigger at line 347. Verify these focus transitions in the browser.
3. **Uncommitted filter edits:** the initial trend invalidation listened only for `change`. Canonical `app.js` lines 354–358 now listen for both `input` and `change`, cancel the old trend request and clear its chart/table before an older response can appear under edited filter fields.

The reviewed protections and compatibility checks are supported directly by source:

- One relative same-origin GET uses the captured test context; responses are checked against the channel token both after fetch and after JSON parsing. Context edits/navigation invalidate all epochs and clear all clinical/log/detail/chart DOM state. New requests cancel older requests on the same channel; detail close cancels its own channel. The gate remains the protection when abort alone is ineffective.
- Literal routes, envelopes, field names, flags and units match the actual API. Source details use the same endpoint as derived details; provenance and correction links perform fresh authorized reads. BP component values/ranges/flags, explicit derived cannot-compute reasons, exact source IDs/dates, inherited release wording and nullable independent criticality are displayed without clinical recalculation.
- Null/nonfinite values are not coerced to zero. The chart breaks line segments at unavailable points, keeps server order, and uses relative nanosecond positions while retaining full timestamp text. Chart axis scaling/rounding is presentation only; table values and server interpretations are not recalculated. The installed model uses reductions for extrema rather than spreading an unbounded array into Math.min/Math.max.
- Access-log IDs remain strings, nullable identities/targets have explicit placeholders, and roster checks are described as test declarations rather than authentication. Errors use generic status-based wording and clear the relevant content. Dynamic text is assigned through textContent; no remote assets, browser clinical storage, dynamic HTML or client-side entitlement rules were found.
- Semantic table alternatives, textual flags, a marker legend, native controls, hidden-panel handling and visible focus rules are present. Their actual usability and visual behavior still require browser and candidate checks; source presence is not an accessibility certification.

## Gates still pending

Actual browser/DOM/network/keyboard/layout execution, observed error/race behavior, and the candidate's real manual Task 86 check remain pending. Root reported the six pure Node checks passed by direct file invocation; this worker did not execute them. The source audit does not substitute for browser evidence or human approval.

## Decimal source-text follow-up

Read the scratch `parseApiResponse` change, display helpers, chart guards, eight pure tests and the updated `app.js` read path. Root reported those eight tests passed; this worker did not run them. No source-level blocker was found in the narrow precision change.

The [TC39 JSON.parse definition and InternalizeJSONProperty algorithm](https://tc39.es/ecma262/multipage/structured-data.html#sec-json.parse) were reopened. They establish that a primitive's unmodified parsed value receives its original source text in the third reviver argument and that the reviver is called with the holder as `this`. The implementation stores the numeric lexeme for value/low/high under a local Symbol on that holder. Nested range/component objects retain their own text, while ordinary counts remain numbers. The exact text is rendered with textContent; it does not change units, server flags or clinical interpretation.

A nonzero decimal that underflows to binary zero is not drawn as zero; overflow and unrepresentable plot bounds also result in chart gaps. Exact server text still appears in value/range labels and tooltips. Chart geometry remains approximate presentation. No clinical computation or new clinical eligibility rule is introduced.

If a relevant numeric field lacks context.source, the parser throws BrowserCapabilityError rather than silently falling back to rounded clinical text. The app imports that class, checks the same request/context token after reading/parsing, and uses the safe browser-capability error callback before success rendering. The required bound is fail-closed behavior, not a custom JSON parser or an unverified polyfill. A browser capability probe and actual rendered-value check remain necessary; the standard alone proves no particular browser/version's implementation.

One presentation correction was sent to root: the chart description/empty/gap text at scratch app.js lines 236, 239 and 268 calls every unplotted point unavailable or nonnumeric. Some gaps now mean a valid server decimal is not representable in chart geometry. Use “unplotted points” and “no representable chart points; exact values and reasons are in the table,” keeping client presentation limitations distinct from missing clinical input. This does not require a policy or API change.
