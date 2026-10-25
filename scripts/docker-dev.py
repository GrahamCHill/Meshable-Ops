#!/usr/bin/env python3
"""Build and run the local Docker stack with frontend hot module replacement."""
import argparse
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", nargs="?", default="up", choices=("up", "down", "logs", "ps"))
    parser.add_argument("--storage", choices=("minio", "azurite"), default="minio",
                        help="local storage emulator (default: minio)")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    docker = shutil.which("docker")
    if not docker:
        parser.error("Install Docker Engine/Desktop with Docker Compose first.")
    if not (root / ".env").is_file():
        parser.error("Create .env first: python deploy/github/setup-instance.py --local --admin-email owner@example.com")

    storage = "docker-compose.dev.azurite.yml" if args.storage == "azurite" else "docker-compose.dev.yml"
    command = [docker, "compose", "-f", str(root / "docker-compose.yml"),
               "-f", str(root / storage), "-f", str(root / "docker-compose.local.yml")]
    if args.action == "up":
        # Refresh anonymous node_modules from the rebuilt image after dependency changes.
        command += ["up", "--build", "--detach", "--renew-anon-volumes"]
    elif args.action == "logs":
        command += ["logs", "--follow", "frontend", "frontend-dev"]
    else:
        command += [args.action]

    try:
        result = subprocess.run(command, cwd=root, check=False)
    except KeyboardInterrupt:
        return 130
    if result.returncode == 0 and args.action == "up":
        print("Local stack started at http://localhost:3000. Frontend edits reload automatically.")
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
