#!/usr/bin/env python3
"""Fill missing runtime secrets on a freshly provisioned host, retaining existing values."""
import base64
import os
import re
import secrets
import sys
from pathlib import Path


def complete(path: Path) -> None:
    if path.is_symlink():
        raise ValueError("Use a regular private environment file")
    content = path.read_text(encoding="utf-8")
    additions = {
        "POSTGRES_PASSWORD": secrets.token_hex(32), "MONGO_PASSWORD": secrets.token_hex(32),
        "APP_SECRETS_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "APP_DOCUMENT_RENDER_TOKEN": secrets.token_hex(32),
        "APP_PLUGIN_RUNNER_TOKEN": secrets.token_hex(32),
    }
    # Existing values (including blank assignments) require deliberate operator action.
    missing = [f"{key}='{value}'" for key, value in additions.items() if not re.search(rf"(?m)^\s*{key}\s*=", content)]
    os.chmod(path, 0o600)
    if missing:
        with path.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\n" + "\n".join(missing) + "\n")


if __name__ == "__main__":
    complete(Path(sys.argv[1]))
    print("Runtime secret fields prepared; existing values retained")
