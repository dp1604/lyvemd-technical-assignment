# Group2 foundation read-only audit

Scope: Tasks33–34 only. Reviewed the installed POM, all current main Java/configuration, V1 migration, FoundationIT/FhirRuntimeTest and the start/verify/bootstrap/shared-environment/stop scripts. No code, script, Maven, application, migration or database operation was executed by this worker. Root's build/database checks were in progress; this report does not claim they passed. The separate Group9 drafts remain unchanged.

## Concrete actionable findings

### F01 — Finish privilege validation before direct HTTP requests can be served

**Location:** `src/main/java/com/aldercrest/atlas/config/DatabaseSafetyCheck.java`9–15.

The safety query is an ApplicationRunner. Boot initializes the web server during context refresh before it invokes runners. This project is a direct loopback service with no readiness-aware reverse proxy, so readiness state alone is not a request gate: an HTTP socket may already be serving while the database check is still running. The current foundation exposes no clinical endpoints, so no patient disclosure is demonstrated at this stage. However, adding clinical routes without changing this order could allow requests before an unsafe role/schema/version check rejects startup.

**Action:** either perform this essential bounded validation during eager component initialization before the web server begins accepting requests, or add an explicit startup readiness gate that refuses every application request until the check succeeds. Prefer the small eager validation for this local service, with a failure test using an unsafe role and confirmation that the service never becomes usable. Retain a bounded connection/query timeout. [Boot3.5 lifecycle ordering](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.application-events-and-listeners) places the server-initialized event before ApplicationStartedEvent, and runners after that; its readiness event is later still. This is a future clinical-path gate, not an invented claim that today's404 leaks data.

### F02 — Catch-all turns ordinary request errors into service-unavailable responses

**Location:** `src/main/java/com/aldercrest/atlas/http/SafeExceptionHandler.java`17–32.

Only no-resource and unreadable-body exceptions get a specific client-error mapping. The Exception handler catches framework failures such as missing required query parameters, argument type mismatches, unsupported methods/media, and ResponseStatusException. It returns503 for all of them. When the required endpoints are added, for example `offset=abc` on an int parameter can be reported as an infrastructure outage instead of a safe400; a deliberately thrown404/409 can also be masked.

**Action:** preserve appropriate safe framework client statuses without echoing their reason, path, query, rejected values or original exception body. Handle conversion/binding failures as400; use the status from a trusted Spring ErrorResponse/ResponseStatusException while sanitizing the body, and reserve503 for infrastructure/unexpected failures. Add a small test using framework exception instances or real test-only controller inputs so this foundation cannot silently override the later contract. [Spring6.2 ErrorResponse](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/ErrorResponse.html) identifies these framework exceptions and their status access; [argument mismatch](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/method/annotation/MethodArgumentTypeMismatchException.html) is a separate type not handled by the existing unreadable-body handler. No malformed-request behavior was executed by this worker.

## Deferred integration precautions, not extra Group2 blockers

- **JSON size:** `application.properties`9 (`server.tomcat.max-http-form-post-size=2MB`) bounds form parsing, not arbitrary JSON bodies. Do not cite it as proof of adopted B2's2MiB FHIR bound. Group4 must count/restrict actual request bytes before HAPI processing, including chunked/no-Content-Length requests. [Boot property reference](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html) describes this setting as form-content size.
- **One top-level action transaction:** `RequestTransaction.java`16 deliberately uses REQUIRES_NEW. Keep its invocation at the request/command boundary. Calling it recursively from ingestion validation or audit helpers would create independent commits and defeat the intended single transaction; no such nested caller exists now.
- **Source non-finite/component checks:** V1 finite numeric/JSON-array checks are useful structural defenses, not proof that the clinical table or BP component content is validated. Those checks belong to Groups3–4.
- **Later schema additions:** release/critical workflow state and durable idempotency are not present yet. They belong to later groups; their absence is not a failure of Tasks33–34. Add migrations before clinical routes depend on them.
- **Bootstrap failure output:** role credentials are generated hex values and piped as SQL through psql, rather than passed on the process command line. Avoid enabling psql echo/verbose SQL diagnostics or publishing raw bootstrap logs: failure context can contain submitted role SQL. No actual credential echo or leak was observed; credentials themselves were never opened or printed by this worker.

## Source/model and safety checks with no defect found

- All27 supplied columns remain under the correct tables. Five supplied primary keys, proxy composite key and the two explicit patient foreign keys are present. Observation's encounter_id/value_num/correction_of/component remain nullable, and component is JSONB. Additional FK/domain/nullability choices are distinguishable from the source requirements; none replaces a supplied meaning.
- Nullable encounter_id works with the composite encounter/patient FK under default MATCH SIMPLE. That added FK prevents a non-null encounter from naming another patient's observation; no source constraint is weakened.
- V1 grants the runtime role clinical/audit SELECT/INSERT but no UPDATE/DELETE, no DDL and no migration-table access. Separate migration credentials are used by a short-lived migration process and removed from the script environment before the serving JVM is launched. Bootstrap creates distinct non-superuser runtime/migration roles; the admin is local setup only. No clinical/audit deletion command is in the bootstrap.
- Random credentials are stored under an ignored local directory with a restricted Windows ACL; explicit staging must still be inspected by root. This read-only audit did not open database.json or any secret value.
- Local cluster and app bind loopback; the assessment cluster uses55433, preserving the earlier probe on55432. Existing cluster/data are not automatically deleted/recreated after a failure. Stop targets the named assessment cluster; it does not stop unrelated processes by executable name.
- Flyway clean is disabled, schemas must already exist, and checksum/history behavior is retained. No destructive migration or database reset is present.
- RequestTransaction is read-write, REPEATABLE_READ, bounded15s and returns through TransactionTemplate only after its commit path. It is a foundation abstraction, not evidence yet that every future read writes its audit.
- SafeErrors uses fixed PHI-free messages; HTTP headers disable storage and external resource origins; operational exception handlers do not log SQL/payloads/IDs or raw exceptions. No clinical endpoints, data generator or UI are implied complete by these controls.
- Integration tests explicitly require the designated loopback atlas_test URL, keeping synthetic verification out of the application atlas database. They meaningfully exercise transaction rollback, denied-event commit, privilege denial and safe unknown-route responses. Execution results remain root-owned and pending at this audit snapshot.

Root should record the actual fixes/evidence and review disposition before marking33–34 complete. This audit neither creates extra clinical-policy decisions nor claims general standards compliance from scaffold code.

## Follow-up disposition after root fixes

Read-only follow-up reviewed the latest DatabaseSafetyCheck, SafeExceptionHandler, FoundationIT, bootstrap ACL logic and Java invocation lines. The original audit above is preserved as the pre-fix snapshot. This worker did not run code, open credentials or alter canonical files.

- **F01 addressed in source:** `DatabaseSafetyCheck.java`8–14 now implements InitializingBean and performs its check in afterPropertiesSet during eager bean initialization, replacing the post-start ApplicationRunner. FoundationIT95–100 explicitly checks that the migration owner is rejected. The reviewed source no longer has the runner-order issue. The test calls the check directly; it is not independently claimed as a socket-race measurement.
- **F02 addressed in source:** `SafeExceptionHandler.java`31–40 returns400 for TypeMismatchException and preserves4xx status from Spring ErrorResponse while still building the fixed SafeErrors body. It never echoes the exception reason. FoundationIT162–174 exercises wrong-type and missing parameters as400 and a ResponseStatusException as409, including checks that synthetic markers/reasons are absent. The earlier blanket503 behavior for these cases is removed.
- **Repeat-run ACL fix reviewed:** `prepare-local-database.ps1`7–25 checks the restricted DACL before applying it, and uses FileSystemAclExtensions.SetAccessControl when replacement is needed. The intended allowed identities remain current user, SYSTEM and Administrators with full control; this change does not add another principal or print secrets. No concrete regression was identified in this bounded source review.
- **Java invocation fix reviewed:** `scripts/start.ps1`10 and `scripts/lib/local-environment.ps1`42 now quote `'-Duser.timezone=UTC'` as one native argument. The option remains before `-jar`, and no credential moved onto the command line. No concrete regression was identified.

Root reports the final run passed eight FoundationIT integration tests and one FHIR runtime unit test, following an actual startup-smoke failure and correction of the native option quoting. Those are root-executed results, not additional execution by this reviewer. No new blocking issue was found in the requested final modifications. The earlier deferred Group4/body-size and later clinical-path obligations remain deferred; this source review does not close those tasks.
