# Setup, commands and troubleshooting

Run these commands from the repository root in **PowerShell 7.2+ on Windows**. The scripts create a separate local assessment cluster and retain its data between runs. They do not require an existing PostgreSQL service.

## 1. Check the prerequisites

Install or extract PowerShell 7, JDK 21+, Maven 3.9+ and PostgreSQL 16 binaries. Keep the PostgreSQL distribution intact, including its supporting libraries. Its `bin` directory must contain `initdb.exe`, `pg_ctl.exe`, `psql.exe`, `createdb.exe`, `pg_dump.exe` and `pg_restore.exe`.

If needed, set the following variables in **each terminal** used for the commands. Replace the example paths with your actual installations:

```powershell
$env:JAVA_HOME = 'C:\tools\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;C:\tools\apache-maven\bin;$env:PATH"
$env:ATLAS_PG_BIN = 'C:\tools\postgresql-16\pgsql\bin'
```

Check the tools before starting:

```powershell
$PSVersionTable.PSVersion
java -version
mvn -version
& "$env:ATLAS_PG_BIN\pg_ctl.exe" --version
```

PowerShell must be version 7.2 or newer, Java at least 21, Maven at least 3.9, and PostgreSQL version 16. `mvn -version` also shows which Java Maven uses. `ATLAS_PG_BIN` is optional if `pg_ctl` is on `PATH`; the scripts locate the other PostgreSQL executables alongside it. Exact-version tool folders under ignored `work/tools` are also detected, but those tools are not shipped. First-time dependency downloads require Maven Central access. The recorded clean-start check used a populated dependency cache.

## 2. Build and start

```powershell
pwsh -NoProfile -File ./scripts/start.ps1
```

This prepares or starts PostgreSQL, preserves existing databases, runs Maven `package` with unit tests, migrates `atlas` in a short-lived process, removes migration secrets from the serving environment, and starts the application in the foreground. Open **http://127.0.0.1:18081/** when startup has completed. No client build is required.

The script uses these local resources:

| Resource | Location |
|---|---|
| Browser/API service | `127.0.0.1:18081` |
| PostgreSQL | `127.0.0.1:55433` |
| Application and integration-test databases | `atlas` and `atlas_test` |
| Schema | `atlas` |
| Cluster data and PostgreSQL logs | `work/local/postgres-data`, `work/local/postgres.log`, `work/local/initdb.log` |
| Generated credentials | `work/local/secrets/database.json` — ignored and access-restricted |

The bootstrap administrator provisions roles and databases. `atlas_migrate` owns migrations; `atlas_runtime` serves the application without DDL or clinical/audit UPDATE/DELETE privileges. Credentials are generated once and retained. Keep the cluster and its matching credential file together; do not delete either to repair a connection failure.

To use a different **application HTTP port**, set `$env:ATLAS_HTTP_PORT = '18082'` before the start command, then open that port instead. The standard local PostgreSQL port and dataset-helper port are fixed in the scripts; there is no documented port switch for them.

## 3. Create and load the dataset

Keep the application terminal open. In a second terminal at the repository root, with the same tool configuration:

```powershell
pwsh -NoProfile -File ./scripts/load-dataset.ps1
```

The command prepares/builds/migrates, generates the deterministic inputs, starts its own helper on `127.0.0.1:18091`, submits real HTTP transactions, applies local review/release and proxy actions, and verifies all eight required cases. It stops its own helper afterwards and preserves the database and separately running application.

Progress appears as `ATLAS_DATASET_STAGE ...`. **Completion is the final `ATLAS_DATASET 200 LOAD_AND_VERIFICATION_COMPLETED` message**, not an intermediate counter. Build, startup, load and verification phases have separate bounded waits; a cold dependency download can take longer than a warm run.

Find synthetic actor, patient and resource IDs in `work/dataset/v1/load-manifest.json`. Use them in the client's test-context controls. The recipe creates 100 patients, 106 encounters and 10,000 source Observations; the [dataset guide](../docs/DATASET.md) explains the fixtures, receipts and replay behavior. Generated files and stage logs are under `work/dataset/v1/`; do not include them in the submission.

An ordinary rerun resumes/replays the same recipe using stored receipts rather than adding a second cohort. Preserve its local journal and inspect any failure first. `-RevalidateRejected` is an exceptional recovery option for an investigated eligible structural rejection, allowing one new key while retaining the original receipt. It is **not** a general retry, reset or duplicate-load option.

## 4. Verify or stop

Run unit and integration tests against the separate designated test database:

```powershell
pwsh -NoProfile -File ./scripts/verify.ps1
```

Integration checks refuse a database URL other than the designated `atlas_test` target. Do not run multiple test suites together or combine a load, restore or benchmark against the same database.

Stop the foreground app with **Ctrl+C**. Then stop the local PostgreSQL cluster if desired:

```powershell
pwsh -NoProfile -File ./scripts/stop-local-database.ps1
```

This stops only `work/local/postgres-data` and deletes no records. Restart with the normal start command. To prepare the database without building/migrating the application, use `pwsh -NoProfile -File ./scripts/prepare-local-database.ps1`.

## Troubleshooting

| Symptom | Check and remedy |
|---|---|
| `pwsh`, `java` or `mvn` is not found | Install the required tool or correct its `PATH`; reopen PowerShell after changing persistent environment settings. Set `JAVA_HOME` to the JDK root, not its `bin` directory. |
| Java version or Maven compilation error | Check both `java -version` and `mvn -version`. Point `JAVA_HOME` at JDK 21+ and prepend its `bin` directory to this terminal's `PATH`. |
| `pg_ctl` or another PostgreSQL executable is missing | Set `ATLAS_PG_BIN` to the actual PostgreSQL 16 `bin` directory. Use a complete binary distribution, including `createdb`, dump and restore tools. |
| Maven cannot resolve dependencies | Check the actual Maven error, network/proxy access and configured repository. Retry the same command after restoring access. Do not skip failing tests or disable certificate validation to force a build. |
| Port already occupied | Inspect its owner with the command below. Close only a process you recognize and own. The loader refuses an occupied `18091`; it does not take over that process. Use `ATLAS_HTTP_PORT` for an application-port collision. A database-port collision must be resolved before retrying. |
| PostgreSQL will not initialize/start | Read `work/local/initdb.log` or `work/local/postgres.log`. Confirm PostgreSQL 16, a writable workspace and a free `55433`. Preserve the existing cluster and credentials while diagnosing. |
| Invalid local credential file or password failure | Preserve `work/local/secrets/database.json` and the matching cluster. Check whether files were copied from different workspaces or credentials changed externally. Restore the matching pair or reconcile credentials through an authorized database administrator; the scripts do not rotate them automatically. Never paste the credential file into a report or commit it. |
| Database migration or application privilege check fails | Read the actual error before retrying. Start through the public script so migrations run with the correct role. Do not bypass startup checks, modify old migrations or grant runtime superuser privileges. |
| Dataset load fails or times out | Keep the receipt/journal and inspect the corresponding ignored `work/dataset/v1/logs` files. Correct the specific cause, ensure no other loader is active, then use the ordinary load command. Do not delete receipts or use `-RevalidateRejected` without investigating the rejection. |
| `LOAD_ALREADY_RUNNING` or helper-port failure | Check whether a prior loader is still active and wait for its completion/cleanup. Inspect the owning process before any intervention. Do not start competing loaders. |
| Browser cannot connect | Check whether the foreground app exited, whether you changed `ATLAS_HTTP_PORT`, and whether startup completed. Read the actual terminal error. Use `127.0.0.1`, the documented port and plain HTTP for this local demonstration. |
| Browser says resource unavailable | Apply the patient ID, actor ID and role from the manifest. The role must match the stored roster; staff may read logs but not clinical results, for example. Denied and missing resources intentionally share safe wording. |
| Browser reports an unavailable capability | Use a current browser supporting `JSON.parse` reviver source context. This check protects exact numerical handling; it is not a reason to alter the dataset. |

Inspect the three standard ports without stopping anything:

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object LocalPort -in 18081,18091,55433 |
    Select-Object LocalAddress,LocalPort,OwningProcess
```

Use `Get-Process -Id <OwningProcess>` with the actual number to identify a process. Do not stop an unrelated PostgreSQL service or delete a database directory as a troubleshooting shortcut. Keep diagnostic logs local; review them before sharing because generated data and operational details do not belong in the submission.

## Other scripts

- `node scripts/client/client-model.test.mjs`: eight focused client-model checks. Node is optional; the browser client itself has no Node build step.
- `node scripts/verify-clinical-oracle.mjs`: independent piecewise equation checks against saved reference values and published rounded examples; no fixture rewrite. [Sources and limits](../docs/evidence/clinical-oracle.md).
- `scripts/workflow.ps1`: explicit local review, critical attestation, release and individual proxy revocation. It requires a built/migrated application and real synthetic IDs. [Arguments and examples](../docs/WORKFLOW.md).
- `scripts/checkpoint-history.ps1`: committed-history checkpoint/recovery. It does not back up uncommitted work, ignored files or PostgreSQL. [History instructions](../docs/HISTORY.md).

The [performance procedure](../docs/PERFORMANCE.md) and [restore evidence](../docs/VERIFICATION.md) state their prerequisites and scope. A clean checkout/empty-cluster exercise is recorded [here](../docs/VERIFICATION.md), with the later verification-wrapper failure distinguished from the completed build/load commands.

## External database setup

The automatic bootstrap is Windows-specific. A manually provisioned environment must supply `ATLAS_DB_URL`, `ATLAS_DB_USER` and `ATLAS_DB_PASSWORD`, schema `atlas`, an owning migration role, and runtime role `atlas_runtime` with the versioned grants. Run the packaged JAR once with `--migrate` and `ATLAS_MIGRATION_USER`/`ATLAS_MIGRATION_PASSWORD`, then remove migration secrets before serving with runtime credentials. Automatic Spring SQL initialization and Flyway startup are disabled. The migrations name `atlas_runtime` explicitly; substituting another deployment role needs a reviewed provisioning change.

This manual path is documented for clarity; it has not been independently tested on a clean non-Windows machine. No production deployment is included. Keep secrets outside command arguments, source control and shared logs.
