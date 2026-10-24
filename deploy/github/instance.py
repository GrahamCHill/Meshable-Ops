"""Shared production configuration helpers; never print generated secrets."""
import base64
import json
import re
import secrets
from pathlib import Path


def hostname(value: str) -> str:
    if len(value) > 253 or "." not in value or not all(
        re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", part)
        for part in value.split(".")
    ):
        raise ValueError("Use a DNS hostname, without a scheme, port, or path")
    return value.lower()


def email(value: str) -> str:
    if not re.fullmatch(r"[^\s@'\"\\]+@[^\s@'\"\\]+\.[^\s@'\"\\]+", value) or any(ord(c) < 32 for c in value):
        raise ValueError("Enter a valid email address")
    return value.lower()


def password(value: str) -> str:
    if len(value) < 14 or len(value.encode("utf-8")) > 72 or any(ord(c) < 32 for c in value):
        raise ValueError("Use an admin password of at least 14 characters and at most 72 UTF-8 bytes")
    return value


def environment(domain: str, admin_email: str, acme_email: str, admin_password: str) -> str:
    domain, admin_email, acme_email = hostname(domain), email(admin_email), email(acme_email)
    users = [{"email": admin_email, "username": "admin", "password": password(admin_password), "role": "ADMIN"}]
    values = {
        "APP_DOMAIN": domain, "ACME_EMAIL": acme_email,
        "JWT_SECRET": secrets.token_hex(48),
        "APP_SECRETS_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "SUPERSET_SECRET_KEY": secrets.token_hex(48), "GUEST_TOKEN_JWT_SECRET": secrets.token_hex(48),
        "SUPERSET_ADMIN_USER": "admin", "SUPERSET_ADMIN_PASSWORD": secrets.token_hex(24),
        "SUPERSET_ADMIN_EMAIL": admin_email, "POSTGRES_PASSWORD": secrets.token_hex(32),
        "MONGO_PASSWORD": secrets.token_hex(32), "APP_DOCUMENT_RENDER_TOKEN": secrets.token_hex(32),
        "APP_PUBLIC_BASE_URL": f"https://{domain}", "APP_MFA_PRIMARY_ADMIN_EMAIL": admin_email,
        "APP_BOOTSTRAP_USERS_JSON": json.dumps(users, separators=(",", ":")),
        "APP_EMAIL_ENABLED": "false", "AI_ENABLED": "false", "APP_SEARCH_ENABLED": "true",
        "APP_SEARCH_LAUNCHER_ENABLED": "true", "AI_INDEX_CONTENT": "false",
        "APP_SEED_STORAGE_PROVIDER": "NONE", "APP_SEED_STORAGE_FORCE": "false",
        "MINIO_ROOT_USER": "weaveopsdev", "MINIO_ROOT_PASSWORD": secrets.token_hex(24),
        "AZURITE_ACCOUNT_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
    }
    # Compose single-quoted values are literal; escape embedded single quotes.
    return "# Keep this file private and retain it with recovery backups.\n" + "\n".join(
        key + "='" + value.replace("'", "\\'") + "'" for key, value in values.items()
    ) + "\n"


def local_environment(admin_email: str, admin_password: str) -> str:
    content = environment("local.weaveops.test", admin_email, admin_email, admin_password)
    return content.replace("APP_DOMAIN='local.weaveops.test'", "APP_DOMAIN='localhost'").replace(
        "APP_PUBLIC_BASE_URL='https://local.weaveops.test'", "APP_PUBLIC_BASE_URL='http://localhost:3000'")


def write_new(path: Path, content: str) -> None:
    import os
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    # O_EXCL refuses existing files and symlinks; rerunning cannot rotate keys accidentally.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)
