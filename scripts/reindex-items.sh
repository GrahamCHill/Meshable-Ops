#!/usr/bin/env sh
set -eu

APP_URL="${APP_URL:-http://localhost:3000}"
APP_ADMIN_EMAIL="${APP_ADMIN_EMAIL:-admin@app.local}"
APP_ADMIN_PASSWORD="${APP_ADMIN_PASSWORD:-admin123}"

cookie_jar="$(mktemp)"
trap 'rm -f "$cookie_jar"' EXIT

echo "Logging into ${APP_URL} as ${APP_ADMIN_EMAIL}..."

curl -fsS \
  -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${APP_ADMIN_EMAIL}\",\"password\":\"${APP_ADMIN_PASSWORD}\"}" \
  "${APP_URL}/api/auth/login" >/dev/null

echo "Triggering item vector reindex..."

curl -fsS \
  -b "$cookie_jar" \
  -X POST \
  "${APP_URL}/api/items/reindex"

echo
echo "Reindex request completed."
