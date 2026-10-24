#!/usr/bin/env python3
"""Validate effective Compose settings without emitting their contents."""
import base64
import json
import re
import subprocess
import sys
from pathlib import Path
from instance import hostname, email, password

ROOT = Path(__file__).resolve().parents[2]


def compose_args(env_file: Path) -> list[str]:
    return ["docker", "compose", "--project-name", "weaveops", "--env-file", str(env_file.resolve()),
            "-f", str(ROOT / "docker-compose.yml"), "-f", str(ROOT / "docker-compose.prod.yml")]


def validate(config: dict) -> str:
    services = config["services"]
    backend = services["backend"]["environment"]
    caddy = services["caddy"]["environment"]
    domain = hostname(caddy["APP_DOMAIN"])
    email(caddy["ACME_EMAIL"])
    if backend.get("APP_PUBLIC_BASE_URL") != f"https://{domain}" or "prod" not in backend.get("SPRING_PROFILES_ACTIVE", "").split(","):
        raise ValueError("Set the matching HTTPS public origin and production profile")
    if backend.get("APP_AUTH_COOKIE_SECURE") != "true":
        raise ValueError("Production auth cookies must use HTTPS")
    if len(base64.b64decode(backend.get("APP_SECRETS_ENCRYPTION_KEY", ""), validate=True)) != 32:
        raise ValueError("Set a persistent 32-byte base64 wrapping key")
    users = json.loads(backend.get("APP_BOOTSTRAP_USERS_JSON", "[]"))
    if not users or not any(user.get("role") == "ADMIN" for user in users):
        raise ValueError("Configure at least one application admin")
    for user in users:
        email(user["email"]); password(user["password"])
        if user["email"].endswith("@app.local") or "replace-with" in user["password"]:
            raise ValueError("Replace example application accounts")
    for service, keys in {
        "backend": ["JWT_SECRET", "APP_DOCUMENT_RENDER_TOKEN", "SUPERSET_ADMIN_PASSWORD"],
        "superset": ["SUPERSET_SECRET_KEY", "GUEST_TOKEN_JWT_SECRET"],
    }.items():
        for key in keys:
            value = services[service]["environment"].get(key, "")
            if len(value) < 32 or any(marker in value.lower() for marker in ("replace-with", "change", "thisisasecret")):
                raise ValueError(f"Set a strong unique {key}")
    runner_token = backend.get("APP_PLUGIN_RUNNER_TOKEN", "")
    if runner_token and len(runner_token) < 32:
        raise ValueError("The private plugin runner token must have at least 32 characters")
    if services["plugin-runner"]["environment"].get("APP_PLUGIN_RUNNER_TOKEN", "") != runner_token:
        raise ValueError("The private plugin runner token must match the backend")
    pg_password = services["postgres"]["environment"]["POSTGRES_PASSWORD"]
    mongo_password = services["mongodb"]["environment"]["MONGO_INITDB_ROOT_PASSWORD"]
    if not all(re.fullmatch(r"[a-fA-F0-9]{64}", value) for value in (pg_password, mongo_password)):
        raise ValueError("Database passwords must be 64 hex characters; use setup-instance.py")
    for name in ("superset", "superset-init", "superset-worker", "superset-beat"):
        if services[name]["environment"].get("DATABASE_PASSWORD") != pg_password:
            raise ValueError("PostgreSQL connections must use the same password")
    if backend.get("POSTGRES_PASSWORD") != pg_password or f":{mongo_password}@" not in backend.get("MONGO_URI", ""):
        raise ValueError("Backend database credentials must match their services")
    for name, service in services.items():
        if name != "caddy" and service.get("ports"):
            raise ValueError("Only Caddy may publish ports in a GitHub production deployment")
    mounts = services["postgres"].get("volumes", [])
    if not any(m.get("target") == "/docker-entrypoint-initdb.d/init.sql" and m.get("source", "").replace("\\", "/").endswith("docker/postgres/init.sql") for m in mounts):
        raise ValueError("Use the production initializer without development users")
    return domain


def load(env_file: Path) -> dict:
    try:
        result = subprocess.run(compose_args(env_file) + ["config", "--format", "json"], capture_output=True, text=True, check=True)
        config = json.loads(result.stdout)
        # Compose escapes literal dollars in its rendered model so it can be reused
        # as Compose input. Compare the environment values containers will receive.
        for service in config.get("services", {}).values():
            service["environment"] = {key: value.replace("$$", "$") if isinstance(value, str) else value
                                      for key, value in service.get("environment", {}).items()}
        return config
    except (subprocess.CalledProcessError, json.JSONDecodeError):
        raise ValueError("Compose configuration is invalid; check the deployment environment file") from None


if __name__ == "__main__":
    try:
        domain = validate(load(Path(sys.argv[1])))
        print(domain if "--domain" in sys.argv[2:] else "Production configuration checked.")
    except (ValueError, KeyError, TypeError) as error:
        # Do not echo input values or raw Compose output containing credentials.
        print("Production configuration validation failed. Check the setup guide and required settings.", file=sys.stderr)
        sys.exit(1)
