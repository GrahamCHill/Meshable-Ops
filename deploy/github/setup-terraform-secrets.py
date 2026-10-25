#!/usr/bin/env python3
"""Create fresh-instance Terraform bootstrap inputs without echoing secrets."""
import argparse
import base64
import getpass
import json
import os
from pathlib import Path
import secrets

from instance import email, password
from private_config import open_terraform_secrets


def configuration(admin_email: str, admin_password: str) -> dict[str, str]:
    users = [{"email": email(admin_email), "username": "admin",
              "password": password(admin_password), "role": "SUPER_ADMIN"}]
    return {
        "jwt_secret": secrets.token_hex(48),
        "superset_secret_key": secrets.token_hex(48),
        "guest_token_jwt_secret": secrets.token_hex(48),
        "superset_admin_password": secrets.token_hex(24),
        "bootstrap_users_json": json.dumps(users, separators=(",", ":")),
        "postgres_admin_password": secrets.token_hex(32),
        "postgres_password": secrets.token_hex(32),
        "superset_database_password": secrets.token_hex(32),
        "mongo_password": secrets.token_hex(32),
        "secrets_encryption_key": base64.b64encode(secrets.token_bytes(32)).decode(),
        "document_render_token": secrets.token_hex(32),
        "plugin_runner_token": secrets.token_hex(32),
    }


def write_new(path: Path, values: dict[str, str]) -> None:
    descriptor = open_terraform_secrets(path)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        json.dump(values, stream, indent=2)
        stream.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--admin-email", required=True)
    parser.add_argument("--output", type=Path, default=Path("bootstrap.auto.tfvars.json"))
    args = parser.parse_args()
    admin_email = email(args.admin_email)
    if args.output.exists() or args.output.is_symlink():
        parser.error("Bootstrap inputs already exist; retain them for updates. This helper is for fresh instances.")
    admin_password = getpass.getpass("Initial Meshable Ops admin password: ")
    if admin_password != getpass.getpass("Confirm password: "):
        parser.error("Passwords do not match")
    try:
        write_new(args.output, configuration(admin_email, admin_password))
    except (ValueError, OSError) as error:
        parser.error(str(error))
    print(f"Created {args.output}; keep it private and back it up. No infrastructure has been deployed.")


if __name__ == "__main__":
    main()
