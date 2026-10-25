#!/usr/bin/env python3
"""Fill missing runtime secrets on a freshly provisioned host, retaining existing values."""
import base64
import os
import re
import secrets
import sys
from pathlib import Path
from private_config import open_private


def complete(path: Path) -> None:
    descriptor = open_private(path)
    with os.fdopen(descriptor, "r+", encoding="utf-8", newline="\n") as stream:
        content = stream.read(1_048_577)
        if len(content)>1_048_576: raise ValueError("Environment file exceeds the size limit")
        _complete(stream, content)


def _complete(stream, content: str) -> None:
    additions = {
        "POSTGRES_PASSWORD": secrets.token_hex(32),
        "APP_SECRETS_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "APP_DOCUMENT_RENDER_TOKEN": secrets.token_hex(32),
        "APP_PLUGIN_RUNNER_TOKEN": secrets.token_hex(32),
    }
    # Existing values (including blank assignments) require deliberate operator action.
    missing = [f"{key}='{value}'" for key, value in additions.items() if not re.search(rf"(?m)^\s*{key}\s*=", content)]
    if hasattr(os, 'fchmod'): os.fchmod(stream.fileno(), 0o600)
    if missing:
        stream.seek(0, os.SEEK_END)
        stream.write("\n" + "\n".join(missing) + "\n")
        stream.flush(); os.fsync(stream.fileno())


if __name__ == "__main__":
    complete(Path(sys.argv[1]))
    print("Runtime secret fields prepared; existing values retained")
