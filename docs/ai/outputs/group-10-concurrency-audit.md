# Group10 concurrency AI source contribution

The continuing read reviewer (thread01a17c28-82d1-7861-be76-af23874491ef) supplied this source diagnosis and a frozen test copy. Root reviewed the exact copy, integrated it and executed the real checks. Original scratch SHA-256:7d5c95abe3c88712590f1bff8e7ef764d44f122980073119caaf726e2f7b4ce9. This is AI test-harness work, not a candidate finding or a production root-cause claim. The source-review output below retains its pre-execution gate; the actual execution is in AI_WORKING_LOG G10-001.

# Concurrent identical-request test: scoped synchronization draft

Static source findings only. Parent reported the full backend run had 163 passing unit tests and one failure among 74 integration tests: the existing concurrent-identical-request method did not observe two advisory waiters within two seconds. This worker did not run, compile or query the database.

## What the source establishes

The existing two-second helper starts immediately after submitting both HTTP calls. `IngestionService.ingest` now performs real local HAPI prevalidation before creating a database transaction; only afterwards does the action execute the stored-roster query and `lockKey`. The old observation window therefore includes HTTP startup and prevalidation, which do not yet hold or wait for the advisory lock. Its SQL also counts any advisory waiters in the database, rather than only the two requests and exact key under test.

These are definite harness couplings. They are plausible explanations for the reported observation failure, not a proven root cause or proof of a production idempotency defect. The final HTTP responses, database state and detailed failing run are not independently inspected here.

## Scratch patch

`src/test/java/com/aldercrest/atlas/IngestionIT.java` is a copy of the current canonical test with only the targeted test, its synchronization helper, one dedicated HTTP helper and the necessary planner spy/import changed. It proposes:

1. Scope the planner spy to the exact unique Bundle body. Call actual `prevalidate`, require available/no-invalid-entry checks, assert no active transaction, and rendezvous both completed real validations before allowing transaction entry. This holds no database connection or snapshot while waiting for the other HAPI invocation.
2. Scope the repository spy to this clinician/role/key. Only the initial two `lockKey` invocations rendezvous. They run after the actual roster query, record two distinct transaction-bound PostgreSQL PIDs, verify Repeatable Read and record actual snapshot text before calling the real lock. The test's owner advisory lock is already held, so neither ingestion can commit before both snapshots are ready. Later retry invocations immediately call the real method rather than deadlocking on a reused barrier.
3. Start the two-second lock observation only after the initial transactions are ready. Count exactly those two PIDs, as atlas_runtime in the current database, waiting in ExclusiveLock mode on the exact database/classid/objid/objsubid of the owner's held advisory lock. Unrelated waiters cannot satisfy the assertion. The [PostgreSQL16 pg_locks definition](https://www.postgresql.org/docs/16/view-pg-locks.html) establishes this object identity and granted/waiting representation. The owner connection takes only this one advisory lock.
4. Unlock in finally before waiting on HTTP futures. Preserve both200 statuses, identical response bodies, one new patient, one actor/role/key receipt and two successful audits. Verify actual prevalidation ran exactly twice. The real database lock, uniqueness conflict/transaction rollback and fresh-snapshot retry remain exercised; no successful result is mocked.
5. Bound the outside-transaction gate, transaction gate, observation, futures, owner proof queries, owner lock/unlock statements and executor shutdown. Both gates are always released during cleanup; unfinished owned futures are cancelled; owned daemon HTTP workers are shut down, with a bounded termination assertion. Both scoped spies are restored. The dedicated45-second HTTP timeout belongs only to this orchestrated test; production transaction and PostgreSQL settings remain unchanged.

The runtime role has a three-second lock timeout and ten-second statement timeout; `RequestTransaction` has a15-second action timeout. The coordinated two-second advisory observation stays below the existing lock timeout. Finite scheduling/observation budgets still exist: this is a coordinated, specifically attributed concurrency proof, not a claim that no machine load can make a test time out.

## Root verification gates

Root must review/integrate the scoped test, compile it, run the targeted actual HTTP/PostgreSQL case and then rerun the required backend suite. If a real validation, HTTP, snapshot, lock, response or final-effect assertion fails, diagnose it rather than bypassing the check or simply extending timeouts. Preserve the original failed full-run evidence and distinguish a source-supported test correction from an established runtime root cause.
