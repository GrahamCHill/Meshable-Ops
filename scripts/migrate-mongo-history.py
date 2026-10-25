#!/usr/bin/env python3
"""Atomically import legacy admin-export history into PostgreSQL; never contact/delete MongoDB."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path


def timestamp(value):
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("History timestamps must include a timezone")
    return parsed


def history_rows(export):
    if export.get("format") != "superset-docker-app-backup-v2":
        raise ValueError("Use a complete legacy admin JSON export v2")
    data = export["data"]
    events, logs = [], []
    for key, output in (("events", events), ("activityLogs", logs)):
        records = data[key]
        if not isinstance(records, list):
            raise ValueError("History collections must be arrays")
        seen = set()
        for record in records:
            identity = record["id"]
            if not isinstance(identity, str) or not 1 <= len(identity) <= 512 or identity in seen:
                raise ValueError("Missing, duplicate, or invalid history identifier")
            seen.add(identity)
            if key == "events":
                if not isinstance(record["payload"], dict):
                    raise ValueError("Event payloads must be objects")
                output.append((identity, record["userId"], record["type"], record["payload"], timestamp(record["createdAt"])))
            else:
                output.append((identity, record["userId"], record["action"], record.get("resourceType"),
                               record.get("resourceId"), record.get("ipAddress"), record.get("userAgent"), timestamp(record["timestamp"])))
    return events, logs


def migrate(connection, events, logs, verify_only=False):
    from psycopg.types.json import Jsonb
    # The caller owns one transaction spanning both collections. Conflicts roll everything back.
    with connection.cursor() as cursor:
        for identity, user, kind, payload, created in events:
            if not verify_only:
                cursor.execute("""INSERT INTO events(id,user_id,type,payload,created_at)
                    VALUES(%s,%s,%s,%s,%s) ON CONFLICT(id) DO NOTHING""",
                    (identity, user, kind, Jsonb(payload), created))
            cursor.execute("SELECT user_id,type,payload,created_at FROM events WHERE id=%s", (identity,))
            if cursor.fetchone() != (user, kind, payload, created):
                raise ValueError("Event verification failed; transaction will be rolled back")
        for row in logs:
            if not verify_only:
                cursor.execute("""INSERT INTO activity_logs(id,user_id,action,resource_type,resource_id,ip_address,user_agent,timestamp)
                    VALUES(%s,%s,%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO NOTHING""", row)
            cursor.execute("SELECT id,user_id,action,resource_type,resource_id,ip_address,user_agent,timestamp FROM activity_logs WHERE id=%s", (row[0],))
            if cursor.fetchone() != row:
                raise ValueError("Audit verification failed; transaction will be rolled back")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    events, logs = history_rows(json.loads(args.export.read_text(encoding="utf-8-sig")))
    import psycopg
    with psycopg.connect(os.environ.get("HISTORY_POSTGRES_DSN", "")) as connection:
        migrate(connection, events, logs, args.verify_only)
    print(f"Verified {len(events)} events and {len(logs)} audit records.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Driver errors can contain host/account details; never echo credentials or record content.
        raise SystemExit("History migration failed. Check the export, database connection, and target schema; no partial import was committed.") from None
