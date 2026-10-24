#!/bin/bash
set -euo pipefail
: "${SUPERSET_ADMIN_PASSWORD:?Configure an explicit Superset admin password}"
echo "Superset init: upgrading metadata..."
superset db upgrade
superset init
python /app/bootstrap_admin.py
echo "Superset initialization complete"
