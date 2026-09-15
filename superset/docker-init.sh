#!/bin/bash
set -e

echo "Superset init: running DB upgrade..."
superset db upgrade

echo "Superset init: creating admin user..."
if superset fab create-admin \
  --username "${SUPERSET_ADMIN_USER:-admin}" \
  --firstname "${SUPERSET_ADMIN_FIRSTNAME:-Admin}" \
  --lastname "${SUPERSET_ADMIN_LASTNAME:-User}" \
  --email "${SUPERSET_ADMIN_EMAIL:-admin@superset.local}" \
  --password "${SUPERSET_ADMIN_PASSWORD:-admin}" \
  2>/dev/null; then
  echo "Superset admin created"
else
  echo "Superset admin already exists; refreshing its password"
  superset fab reset-password \
    --username "${SUPERSET_ADMIN_USER:-admin}" \
    --password "${SUPERSET_ADMIN_PASSWORD:-admin}"
fi

echo "Superset init: initialising Superset roles & permissions..."
superset init

echo "Superset init: done ✓"
