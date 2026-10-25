#!/usr/bin/env python3
"""CI check with temporary generated credentials; does not start containers."""
import tempfile
from contextlib import chdir
import subprocess
from pathlib import Path
from instance import environment, write_new
from preflight import ROOT, load, validate

with tempfile.TemporaryDirectory() as directory, chdir(directory):
    env_file = Path(directory) / ".env.production"
    # Exercise Compose's literal quoting, including interpolation-looking password text.
    write_new(env_file, environment("workspace.example.com", "owner@example.com", "ops@example.com", "Test-'$quotes\\-password-123!"))
    config = load(env_file)
    assert validate(config) == "workspace.example.com"
    import json
    users = json.loads(config["services"]["backend"]["environment"]["APP_BOOTSTRAP_USERS_JSON"])
    assert users[0]["password"] == "Test-'$quotes\\-password-123!"
    for override in (None, "docker-compose.dev.yml", "docker-compose.dev.azurite.yml"):
        command = ["docker", "compose", "--env-file", str(env_file), "-f", str(ROOT / "docker-compose.yml")]
        if override:
            command += ["-f", str(ROOT / override)]
        subprocess.run(command + ["config", "--quiet"], capture_output=True, check=True)
        if override:
            subprocess.run(command + ["-f", str(ROOT / "docker-compose.local.yml"), "config", "--quiet"], capture_output=True, check=True)
    assert "superset-embed-init" not in config["services"]
    missing_secret = Path(directory) / "missing-secret.env"
    source = env_file.read_text()
    missing_secret.write_text("\n".join(line for line in source.splitlines() if not line.startswith("JWT_SECRET=")))
    rejected = subprocess.run(["docker", "compose", "--env-file", str(missing_secret), "-f", str(ROOT / "docker-compose.yml"), "config", "--quiet"], capture_output=True)
    assert rejected.returncode != 0, "Compose must reject missing JWT credentials"
    print("Production/local Compose settings, private ports, initialization, and literal secrets checked.")
