# Migrating legacy MongoDB history to PostgreSQL

New installations use PostgreSQL for `events` and `activity_logs`. MongoDB is no longer a runtime service, dependency, secret, or private firewall port. Event payloads use JSONB; audit records use typed columns. Existing administrator permissions, event/audit APIs, and JSON export keys remain unchanged.

## Existing installations

1. Before upgrading, save a complete administrator JSON export from the old application (format `superset-docker-app-backup-v2`). Check that both `data.events` and `data.activityLogs` are present. Take independent PostgreSQL, MongoDB, object, and wrapping-key backups as well.
2. Stop writes to the old application and take the final export. Retain the old application release, MongoDB container/volume, and its credentials for rollback. Do not run `docker compose down -v`, prune volumes, or delete the legacy services disk.
3. Start the upgraded backend with public traffic stopped. Its existing Hibernate setup creates the PostgreSQL history tables. The new application starts without MongoDB; its history is initially empty.
4. Install the migration-only Python dependency with `python -m pip install -r scripts/history-migration-requirements.txt`. Supply the destination via `HISTORY_POSTGRES_DSN`, or standard libpq `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, and TLS variables. Use the application database and the same protected connection policy as the backend. Do not commit the export or credentials.
5. Run `python scripts/migrate-mongo-history.py /private/path/final-export.json`. The importer verifies the export shape, preserves IDs/timestamps and all modeled fields, and imports both collections in one transaction. Repeated runs are idempotent. Existing IDs with different data abort and roll back the entire import.
6. Run the same command with `--verify-only`. Compare reported counts with the final export and inspect event/audit pages and the new admin JSON export before reopening writes.
7. Keep the old backup/volume through your recovery retention period. Removing MongoDB from Compose does not migrate or delete its old volume. Retire the legacy container, credentials, and data only after migration and recovery have been verified.

Event history remains ordered by descending timestamp and ID. MongoDB BSON type ordering is not retained for tied timestamps with mixed ID types; IDs themselves are preserved. Refresh open event-history pages after upgrading, as old ObjectId cursors are intentionally invalidated. Administrator endpoints and authorization remain unchanged.

Business records and their event/audit writes can now share a PostgreSQL transaction. External object-storage writes continue to have their existing independent failure boundaries.

## Recovery boundaries

PostgreSQL backups now include event/audit history and document-processing jobs/pages/chunks. Original files remain in object storage. Back up wrapping keys and private service configuration separately. The administrator JSON export remains a metadata/history export with its existing omissions; it is not a complete disaster-recovery backup or a general restore endpoint.
