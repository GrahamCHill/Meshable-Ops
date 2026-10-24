#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${DEPLOY_HOST:?Set the DEPLOY_HOST secret}"
: "${DEPLOY_USER:?Set the DEPLOY_USER secret}"
: "${DEPLOY_SSH_KEY:?Set the DEPLOY_SSH_KEY secret}"
: "${DEPLOY_KNOWN_HOSTS:?Set the verified DEPLOY_KNOWN_HOSTS secret}"
port="${DEPLOY_PORT:-22}"
root="${DEPLOY_PATH:-/srv/weaveops}"
[[ "$DEPLOY_HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || { echo 'Use a DNS name or IPv4 address for DEPLOY_HOST'; exit 1; }
[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo 'Invalid DEPLOY_USER'; exit 1; }
[[ "$port" =~ ^[0-9]+$ && "$port" -ge 1 && "$port" -le 65535 ]] || { echo 'Invalid DEPLOY_PORT'; exit 1; }
[[ "$root" =~ ^/[a-zA-Z0-9_/-]+$ && "$root" != / && "$root" != */../* && "$root" != */.. ]] || { echo 'Invalid DEPLOY_PATH'; exit 1; }
release_id="$GITHUB_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
[[ "$release_id" =~ ^[a-f0-9]{40}-[0-9]+-[0-9]+$ ]] || { echo 'Invalid GitHub release identity'; exit 1; }
key_file="$RUNNER_TEMP/weaveops-deploy-key"
known_hosts="$RUNNER_TEMP/weaveops-known-hosts"
archive="$RUNNER_TEMP/weaveops-$release_id.tar.gz"
trap 'rm -f "$key_file" "$known_hosts" "$archive"' EXIT
printf '%s\n' "$DEPLOY_SSH_KEY" > "$key_file"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$known_hosts"
chmod 600 "$key_file" "$known_hosts"
unset DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS
ssh_options=(-o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$known_hosts" -o ConnectTimeout=20 -o ServerAliveInterval=30 -o ServerAliveCountMax=10 -i "$key_file")
destination="$DEPLOY_USER@$DEPLOY_HOST"

# Only tracked runtime sources are shipped. Local environments, .git, build tools,
# screenshots, and ignored files cannot enter this archive.
git archive --format=tar.gz --output="$archive" "$GITHUB_SHA" docker-compose.yml docker-compose.prod.yml backend frontend ml-backend weasy-renderer superset docker deploy
ssh "${ssh_options[@]}" -p "$port" "$destination" "umask 077; test -f '$root/shared/.env.production' && mkdir -p '$root/incoming' '$root/releases'"
scp "${ssh_options[@]}" -P "$port" "$archive" "$destination:$root/incoming/$release_id.tar.gz"
ssh "${ssh_options[@]}" -p "$port" "$destination" "set -eu; umask 077; mkdir '$root/releases/$release_id'; tar -xzf '$root/incoming/$release_id.tar.gz' --no-same-owner -C '$root/releases/$release_id'; bash '$root/releases/$release_id/deploy/github/deploy.sh' '$root' '$release_id'"
