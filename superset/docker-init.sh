#!/bin/bash
set -e

echo "Superset init: running DB upgrade..."
superset db upgrade

echo "Superset init: creating admin user..."
superset fab create-admin \
  --username admin \
  --firstname Admin \
  --lastname User \
  --email admin@superset.local \
  --password admin \
  2>/dev/null || echo "Admin already exists"

echo "Superset init: initialising Superset roles & permissions..."
superset init

echo "Superset init: done ✓"
