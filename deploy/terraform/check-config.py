#!/usr/bin/env python3
"""Resolve split Compose roles with temporary test values; start no containers."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

from start import compose_environment

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = {
    "app": {"caddy", "frontend", "backend"},
    "services": {"postgres-init", "mongodb", "redis", "superset-init", "superset", "superset-worker", "superset-beat"},
    "runtime": {"plugin-python-worker", "plugin-kotlin-worker", "plugin-runner", "weasy-renderer", "ml-backend"},
}
values = {
    "APP_DOMAIN": "workspace.example.com", "ACME_EMAIL": "ops@example.com",
    "APP_PRIVATE_IP": "10.44.1.10", "SERVICES_PRIVATE_IP": "10.44.2.10", "RUNTIME_PRIVATE_IP": "10.44.2.11",
    "POSTGRES_HOST": "postgres.internal.example.com", "POSTGRES_ADMIN_USER": "meshable_admin",
    "JWT_SECRET": "test-only-jwt-secret", "POSTGRES_PASSWORD": "test-only-app-password", "MONGO_PASSWORD": "test-only-history-password",
    "APP_SECRETS_ENCRYPTION_KEY": "test-only-wrapping-key", "SUPERSET_ADMIN_PASSWORD": "test-only-admin-password",
    "APP_DOCUMENT_RENDER_TOKEN": "test-only-render-token", "APP_PLUGIN_RUNNER_TOKEN": "test-only-plugin-token",
    "POSTGRES_ADMIN_PASSWORD": "test-only-database-admin-password", "SUPERSET_DATABASE_PASSWORD": "test-only-superset-password",
    "SUPERSET_SECRET_KEY": "test-only-superset-key", "GUEST_TOKEN_JWT_SECRET": "test-only-guest-key",
    "APP_BOOTSTRAP_USERS_JSON": json.dumps([{"email": "owner@example.com", "password": "test-'$literal-password"}]),
}
with tempfile.TemporaryDirectory() as directory:
    env_file = Path(directory) / ".env.production"
    env_file.write_text("# Test configuration only\n")
    for role, expected in EXPECTED.items():
        private_ip = values[{"app": "APP_PRIVATE_IP", "services": "SERVICES_PRIVATE_IP", "runtime": "RUNTIME_PRIVATE_IP"}[role]]
        env = compose_environment({**values, "HOST_PRIVATE_IP": private_ip}, role)
        command = ["docker", "compose", "--env-file", str(env_file), "--profile", role,
                   "-f", str(ROOT / "docker-compose.yml"), "-f", str(ROOT / "docker-compose.prod.yml"),
                   "-f", str(ROOT / "deploy/terraform/compose.split.yml"), "config", "--format", "json"]
        result = subprocess.run(command, check=True, capture_output=True, text=True, env={**os.environ, **env})
        services = json.loads(result.stdout)["services"]
        for service in services.values():
            service["environment"] = {key: value.replace("$$", "$") if isinstance(value, str) else value for key, value in service.get("environment", {}).items()}
        assert set(services) == expected, f"Unexpected services for {role}: {set(services)}"
        for name, service in services.items():
            for port in service.get("ports", []):
                if name == "caddy":
                    assert port["target"] in (80, 443)
                else:
                    assert port.get("host_ip") == private_ip, f"Public service port: {role}/{name}"
            assert set(service.get("depends_on", {})) <= expected, f"Cross-host Compose dependency: {name}"
        if role == "app":
            assert services["backend"]["environment"]["POSTGRES_USER"] == "app_user"
            assert "sslmode=verify-full" in services["backend"]["environment"]["POSTGRES_JDBC_PARAMETERS"]
            assert json.loads(services["backend"]["environment"]["APP_BOOTSTRAP_USERS_JSON"])[0]["password"] == "test-'$literal-password"
        if role == "services":
            assert services["superset"]["environment"]["DATABASE_PASSWORD"] == values["SUPERSET_DATABASE_PASSWORD"]
            assert not services["redis"].get("ports")
        if role == "runtime":
            assert services["plugin-runner"]["environment"]["PLUGIN_HOST_CALLBACK"] == "http://10.44.1.10:8080/internal/plugins/host-call"
        print(f"Split {role} role: service selection, dependencies and private bindings checked.")
