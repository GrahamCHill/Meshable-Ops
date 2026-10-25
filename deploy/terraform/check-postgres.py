#!/usr/bin/env python3
"""Exercise database bootstrap in a disposable, unpublished local container."""
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
IMAGE = "pgvector/pgvector:0.8.6-pg18-trixie"
TEST_SECRETS: list[str] = []


def private_file(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def run(command: list[str], *, success: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(command, capture_output=True, text=True)
    if success and result.returncode:
        diagnostic = result.stderr
        for value in TEST_SECRETS:
            diagnostic = diagnostic.replace(value, "[redacted test value]")
        raise RuntimeError("Isolated PostgreSQL bootstrap check failed: " + diagnostic[:1500])
    return result


def main() -> None:
    name = "meshable-bootstrap-check-" + secrets.token_hex(6)
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory)
        master, app, superset = [secrets.token_hex(32) for _ in range(3)]
        TEST_SECRETS.extend([master, app, superset])
        server = path / ".env"
        bootstrap = path / ".env.production"
        private_file(server, f"POSTGRES_USER=meshable_admin\nPOSTGRES_DB=postgres\nPOSTGRES_PASSWORD={master}\n")
        private_file(bootstrap, f"PGHOST=/var/run/postgresql\nPGDATABASE=postgres\nPGUSER=meshable_admin\nPGPASSWORD={master}\nPGSSLMODE=disable\nPOSTGRES_PASSWORD={app}\nSUPERSET_DATABASE_PASSWORD={superset}\n")
        try:
            run(["docker", "run", "--detach", "--rm", "--name", name, "--network", "none", "--env-file", str(server),
                 "--mount", f"type=bind,source={ROOT / 'deploy/terraform'},target=/bootstrap,readonly", IMAGE])
            for _ in range(90):
                if run(["docker", "exec", name, "pg_isready", "-q", "-h", "127.0.0.1", "-U", "meshable_admin", "-d", "postgres"], success=False).returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("Isolated PostgreSQL did not start")
            execute = ["docker", "exec", "--env-file", str(bootstrap), name]
            run(execute + ["bash", "/bootstrap/init-postgres.sh"])
            query = ["docker", "exec", name, "psql", "-U", "meshable_admin", "-d", "app_db", "-At", "-v", "ON_ERROR_STOP=1", "-c"]
            stored = run(query + ["SELECT rolpassword FROM pg_authid WHERE rolname='app_user'"]).stdout
            run(query + ["SET ROLE app_user; CREATE TABLE bootstrap_check(id integer); INSERT INTO bootstrap_check VALUES (1)"])
            assert run(query + ["SET ROLE superset_user; SELECT id FROM bootstrap_check"]).stdout.strip().endswith("1")
            assert run(query + ["SET ROLE superset_user; INSERT INTO bootstrap_check VALUES (2)"], success=False).returncode != 0
            assert run(query + ["SELECT extname FROM pg_extension WHERE extname='vector'"]).stdout.strip() == "vector"
            run(execute + ["bash", "/bootstrap/init-postgres.sh"])
            assert run(query + ["SELECT rolpassword FROM pg_authid WHERE rolname='app_user'"]).stdout == stored
            print("Disposable PostgreSQL bootstrap: databases, pgvector, separate roles, read-only reporting grants and repeat initialization checked.")
        finally:
            run(["docker", "rm", "--force", "--volumes", name], success=False)


if __name__ == "__main__":
    main()
