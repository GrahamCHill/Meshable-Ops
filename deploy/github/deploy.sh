#!/usr/bin/env bash
set -euo pipefail
umask 077

root="${1:?Deployment root required}"
release_id="${2:?Release ID required}"
[[ "$root" =~ ^/[a-zA-Z0-9_/-]+$ && "$root" != / && "$root" != */../* && "$root" != */.. ]] || { echo 'Invalid deployment path'; exit 1; }
[[ "$release_id" =~ ^[a-f0-9]{40}-[0-9]+-[0-9]+$ ]] || { echo 'Invalid release ID'; exit 1; }
release="$root/releases/$release_id"
[[ -d "$release" && "$(realpath "$release")" == "$release" ]] || { echo 'Invalid release directory'; exit 1; }
env_file="$root/shared/.env.production"
[[ -f "$env_file" && ! -L "$env_file" ]] || { echo 'Run setup-instance.py on the server first'; exit 1; }
[[ "$(stat -c %a "$env_file")" == 600 ]] || { echo 'Set the environment file permissions to 600'; exit 1; }
command -v flock >/dev/null
exec 9>"$root/.deploy.lock"
flock -w 1800 9
cd "$release"

python3 - <<'PY'
import re, subprocess
version = subprocess.check_output(['docker', 'compose', 'version', '--short'], text=True)
parts = re.search(r'(\d+)\.(\d+)\.(\d+)', version)
if not parts or tuple(map(int, parts.groups())) < (2, 24, 4):
    raise SystemExit('Docker Compose 2.24.4 or later is required')
PY
domain="$(python3 deploy/github/preflight.py "$env_file" --domain)"
compose=(docker compose --project-name weaveops --env-file "$env_file" -f docker-compose.yml -f docker-compose.prod.yml)

echo "Building release $release_id"
"${compose[@]}" build --pull
"${compose[@]}" pull --ignore-buildable
"${compose[@]}" up -d --no-build --remove-orphans --wait --wait-timeout 600
curl --fail --silent --show-error --retry 12 --retry-delay 5 --retry-all-errors "https://$domain/api/health" >/dev/null

if [[ -L "$root/current" ]]; then
  ln -sfn "$(readlink "$root/current")" "$root/previous"
fi
ln -s "$release" "$root/.current-$release_id"
mv -Tf "$root/.current-$release_id" "$root/current"
printf '%s\n' "$release_id" > "$root/deployed-release"
echo "Deployed $release_id at https://$domain"
# Keep prior releases, the environment, and named volumes for deliberate recovery.
