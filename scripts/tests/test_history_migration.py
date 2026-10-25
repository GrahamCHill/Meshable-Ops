import copy
from datetime import datetime, timezone
import importlib.util
import os
from pathlib import Path
import unittest
import uuid

spec = importlib.util.spec_from_file_location("history_migration", Path(__file__).resolve().parents[1] / "migrate-mongo-history.py")
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


def export():
    return {"format": "superset-docker-app-backup-v2", "data": {
        "events": [{"id": "legacy-object-id", "userId": "system", "type": "FOLDER_CREATED",
                    "payload": {"title": "O'Brien", "nested": {"amount": 12.5}}, "createdAt": "2026-10-24T10:00:00Z"}],
        "activityLogs": [{"id": "legacy-audit-id", "userId": "system", "action": "FOLDER_CREATED",
                          "resourceType": "FOLDER", "resourceId": "folder-id", "ipAddress": "::1",
                          "userAgent": "migration-test", "timestamp": "2026-10-24T10:00:00Z"}]}}


class HistoryValidationTests(unittest.TestCase):
    def test_identifiers_payloads_and_timezone_are_preserved(self):
        events, logs = migration.history_rows(export())
        self.assertEqual(events[0][0], "legacy-object-id")
        self.assertEqual(events[0][-1], datetime(2026, 10, 24, 10, tzinfo=timezone.utc))
        self.assertEqual(logs[0][5], "::1")

    def test_missing_collections_duplicate_ids_and_naive_timestamps_are_rejected(self):
        cases = []
        missing = export(); del missing["data"]["activityLogs"]; cases.append(missing)
        duplicate = export(); duplicate["data"]["events"] *= 2; cases.append(duplicate)
        naive = export(); naive["data"]["events"][0]["createdAt"] = "2026-10-24T10:00:00"; cases.append(naive)
        for value in cases:
            with self.assertRaises((ValueError, KeyError)):
                migration.history_rows(value)


@unittest.skipUnless(os.environ.get("HISTORY_TEST_DSN"), "Requires an isolated test PostgreSQL database")
class HistoryDatabaseTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        self.schema = "history_test_" + uuid.uuid4().hex
        self.connection = psycopg.connect(os.environ["HISTORY_TEST_DSN"], autocommit=True)
        self.connection.execute(f"CREATE SCHEMA {self.schema}")
        self.connection.execute(f"SET search_path TO {self.schema}")
        self.connection.execute("CREATE TABLE events(id VARCHAR(512) PRIMARY KEY,user_id TEXT,type TEXT,payload JSONB,created_at TIMESTAMPTZ)")
        self.connection.execute("CREATE TABLE activity_logs(id VARCHAR(512) PRIMARY KEY,user_id TEXT,action TEXT,resource_type TEXT,resource_id TEXT,ip_address TEXT,user_agent TEXT,timestamp TIMESTAMPTZ)")

    def tearDown(self):
        self.connection.execute("SET search_path TO public")
        self.connection.execute(f"DROP SCHEMA {self.schema} CASCADE")
        self.connection.close()

    def test_repeated_import_and_verification_preserve_exact_history(self):
        events, logs = migration.history_rows(export())
        for verify in (False, False, True):
            with self.connection.transaction():
                migration.migrate(self.connection, events, logs, verify)
        self.assertEqual(self.connection.execute("SELECT count(*) FROM events").fetchone()[0], 1)
        self.assertEqual(self.connection.execute("SELECT count(*) FROM activity_logs").fetchone()[0], 1)

    def test_conflicting_audit_rolls_back_preceding_event_inserts(self):
        events, logs = migration.history_rows(export())
        with self.connection.transaction():
            migration.migrate(self.connection, events, logs)
        changed = copy.deepcopy(export())
        changed["data"]["events"][0]["id"] = "new-event"
        changed["data"]["activityLogs"][0]["action"] = "CONFLICT"
        changed_events, changed_logs = migration.history_rows(changed)
        with self.assertRaises(ValueError):
            with self.connection.transaction():
                migration.migrate(self.connection, changed_events, changed_logs)
        self.assertEqual(self.connection.execute("SELECT count(*) FROM events WHERE id='new-event'").fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
