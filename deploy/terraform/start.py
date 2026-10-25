#!/usr/bin/env python3
"""Start exactly one split deployment role; never echo resolved configuration."""
import argparse
import os
from pathlib import Path
import subprocess
import shutil
import urllib.request

ROLES = {
    "app": {"JWT_SECRET", "APP_SECRETS_ENCRYPTION_KEY", "POSTGRES_PASSWORD", "SUPERSET_ADMIN_PASSWORD", "APP_DOCUMENT_RENDER_TOKEN", "APP_PLUGIN_RUNNER_TOKEN", "APP_BOOTSTRAP_USERS_JSON"},
    "services": {"POSTGRES_ADMIN_PASSWORD", "POSTGRES_PASSWORD", "SUPERSET_DATABASE_PASSWORD", "SUPERSET_SECRET_KEY", "GUEST_TOKEN_JWT_SECRET", "SUPERSET_ADMIN_PASSWORD"},
    "runtime": {"APP_DOCUMENT_RENDER_TOKEN", "APP_PLUGIN_RUNNER_TOKEN"},
}


def compose_environment(values: dict[str, str], role: str) -> dict[str, str]:
    required = ROLES[role] | {"APP_DOMAIN", "ACME_EMAIL", "HOST_PRIVATE_IP", "APP_PRIVATE_IP", "SERVICES_PRIVATE_IP", "RUNTIME_PRIVATE_IP", "POSTGRES_HOST", "POSTGRES_ADMIN_USER"}
    missing = sorted(key for key in required if not values.get(key))
    if missing:
        raise ValueError("Missing role settings: " + ", ".join(missing))
    # Compose interpolates inactive profiles too. Give them inert placeholders;
    # the required set above prevents their use by any active service.
    result = dict(values)
    for key in set().union(*ROLES.values()):
        result.setdefault(key, "inactive-profile")
    result["COMPOSE_PROFILES"] = role
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--role", choices=ROLES, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    # Docker loads the literal, single-quoted env file. Obtain parsed values
    # through its own parser; capture stdout so secrets never reach logs.
    import json
    base = ["docker", "compose", "--env-file", str(root / ".env.production")]
    interpolation = subprocess.run(base + ["-f", str(root / "deploy/terraform/environment.yml"), "config", "--format", "json"], check=True, capture_output=True, text=True, env={**os.environ, "SPLIT_ENV_FILE": str(root / ".env.production")})
    values = json.loads(interpolation.stdout)["services"]["environment"]["environment"]
    # Rendered Compose models double literal dollars for reuse as YAML input.
    env = compose_environment({key: str(value).replace("$$", "$") for key, value in values.items() if value is not None}, args.role)
    if args.role == "runtime":
        env["DOCKER_GID"] = str(Path("/var/run/docker.sock").stat().st_gid)
    if args.role == "services" and not os.path.ismount("/srv/meshable-data"):
        raise ValueError("Services data disk is not mounted; refusing to write history onto the system disk")
    ca_path = Path("/etc/meshable-ops/postgres-ca.pem")
    if args.role != "runtime" and not ca_path.exists():
        if env.get("POSTGRES_CA_SOURCE") == "SYSTEM":
            shutil.copyfile("/etc/ssl/certs/ca-certificates.crt", ca_path)
        else:
            ca_url = env.get("POSTGRES_CA_URL", "")
            if ca_url != "https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem":
                raise ValueError("PostgreSQL CA file is missing")
            with urllib.request.urlopen(ca_url, timeout=60) as response:
                ca_path.write_bytes(response.read(1_048_576))
        ca_path.chmod(0o644)
    command = base + ["--project-name", f"meshable-{args.role}", "--profile", args.role,
                      "-f", str(root / "docker-compose.yml"), "-f", str(root / "docker-compose.prod.yml"),
                      "-f", str(root / "deploy/terraform/compose.split.yml"), "up", "--build", "-d", "--wait", "--wait-timeout", "900"]
    subprocess.run(command, check=True, cwd=root, env={**os.environ, **env})


if __name__ == "__main__":
    main()
