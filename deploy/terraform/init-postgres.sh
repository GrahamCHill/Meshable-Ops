#!/bin/bash
set -euo pipefail
: "${PGHOST:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${POSTGRES_PASSWORD:?}" "${SUPERSET_DATABASE_PASSWORD:?}"
export PGCONNECT_TIMEOUT=10
for attempt in $(seq 1 60); do
  if pg_isready -q; then break; fi
  if [ "$attempt" -eq 60 ]; then echo 'Managed PostgreSQL did not become available' >&2; exit 1; fi
  sleep 10
done
# psql reads secrets from environment, not command-line arguments or echoed SQL.
psql --no-psqlrc --set=ON_ERROR_STOP=1 --file=/bootstrap/init-roles.sql
PGDATABASE=app_db psql --no-psqlrc --set=ON_ERROR_STOP=1 --file=/bootstrap/init-app.sql
