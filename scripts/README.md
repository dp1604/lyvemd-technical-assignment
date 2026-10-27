# Reproducible local commands

The tested automation targets Windows with PowerShell7. JDK21+, Maven3.9+ and PostgreSQL16 binaries are prerequisites. The isolated tool copies under ignored work/tools are optional conveniences, not submission artifacts. Otherwise use JAVA_HOME/PATH and ATLAS_PG_BIN for PostgreSQL's bin directory. Never put credentials in command arguments, Git or shell history.

| Command from repository root | Effects |
|---|---|
| pwsh -NoProfile -File ./scripts/start.ps1 | Preserve/prepare the local assessment and test databases; Maven package with unit tests; migrate atlas in a short-lived process; remove migration credentials; serve on loopback18081 in the foreground. Ctrl+C stops the app. |
| pwsh -NoProfile -File ./scripts/verify.ps1 | Prepare databases without resetting data; run unit and real-PostgreSQL integration tests against atlas_test only. Integration tests refuse another URL. |
| pwsh -NoProfile -File ./scripts/prepare-local-database.ps1 | Prepare/start the local cluster and roles without building or migrating app tables. Existing data and credentials are preserved. |
| pwsh -NoProfile -File ./scripts/stop-local-database.ps1 | Fast orderly stop of only work/local/postgres-data; never deletes data. Stop the app first. |
| pwsh -NoProfile -File ./scripts/checkpoint-history.ps1 | Verify/recover committed Git history; see docs/HISTORY.md. Does not preserve uncommitted work, ignored data or the database. |

## Local database and failure recovery

Loopback cluster: 127.0.0.1:55433, separate from the old environment probe on55432. Databases atlas and atlas_test, schema atlas. Credential material is generated once under ignored work/local/secrets with restricted Windows ACLs. The bootstrap administrator only provisions the cluster/roles/databases. atlas_migrate owns schema/migrations; atlas_runtime has explicit data privileges and no DDL or clinical/audit UPDATE/DELETE. Application startup checks version/privileges before serving. See docs/DATABASE.md for added columns/constraints and limitations.

No command resets/drops/recreates an existing database, rotates its passwords or kills an unrelated process. If startup fails, inspect the ignored work/local logs and actual service/port state before retrying; do not delete a cluster/credential file to fix a mismatch. A port collision fails visibly. The start wait is bounded; preserve diagnostic files. Startup/build commands fail when migrations or tests fail, before serving application content. Do not run load, migrations, benchmarks or restore work concurrently on the same database.

## External configuration (manual path; not yet independently clean-machine tested)

The packaged Java application reads ATLAS_DB_URL, ATLAS_DB_USER and ATLAS_DB_PASSWORD. An administrator must provision schema atlas, an owning migration role and runtime role atlas_runtime with V1's grants; schema public must not be writable by runtime. Run the same JAR once with --migrate and ATLAS_MIGRATION_USER/PASSWORD (and DB URL), then remove those migration secrets and start it normally with runtime credentials. Boot's automatic migration/SQL initialization is disabled, so omitting this step must not silently create a schema. The source migration names atlas_runtime explicitly; a differently named deployment role requires a reviewed new provisioning/migration design, not an undocumented substitution. No production deployment is included.

The required deterministic generator/loader is pending Group6. Restore/clean-run and full command acceptance remain Group12. These commands establishing a runnable foundation do not complete those later tasks.
