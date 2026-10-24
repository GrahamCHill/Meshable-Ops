#!/usr/bin/env python3
"""Run once on a Linux deployment host, before enabling GitHub deployments."""
import argparse
import getpass
from pathlib import Path
from instance import environment, local_environment, write_new


def main() -> None:
    parser = argparse.ArgumentParser(description="Create private Meshable Ops settings without development accounts")
    parser.add_argument("--local", action="store_true", help="Generate .env for local Compose development")
    parser.add_argument("--domain")
    parser.add_argument("--admin-email", required=True)
    parser.add_argument("--acme-email")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    args.output = args.output or (Path(".env") if args.local else Path("/srv/weaveops/shared/.env.production"))
    if not args.local and (not args.domain or not args.acme_email):
        parser.error("Production setup requires --domain and --acme-email")
    if args.output.exists() or args.output.is_symlink():
        parser.error("The settings file already exists; keep its wrapping keys and update it deliberately")
    first = getpass.getpass("Initial application admin password: ")
    if first != getpass.getpass("Confirm password: "):
        parser.error("Passwords do not match")
    try:
        content = local_environment(args.admin_email, first) if args.local else environment(args.domain, args.admin_email, args.acme_email, first)
        write_new(args.output, content)
    except (ValueError, FileExistsError) as error:
        parser.error(str(error))
    print("Private settings created. Retain this file with recovery backups. No secrets were printed.")


if __name__ == "__main__":
    main()
