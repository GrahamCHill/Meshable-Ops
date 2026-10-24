"""Bootstrap artifacts inside a disposable job's private tmpfs. Never runs in the control-plane service."""
import base64
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys

MAX_BOOTSTRAP = 48 * 1024 * 1024


def prepare(value, root):
    files = value.get('files')
    entry = value.get('entrypoint', '')
    runtime = value.get('runtime')
    if runtime not in ('PYTHON', 'KOTLIN') or not isinstance(files, dict) or len(files) > 200:
        raise ValueError('Invalid module')
    if entry not in files or not entry.startswith('backend/') or not entry.endswith('.py' if runtime == 'PYTHON' else '.jar'):
        raise ValueError('Invalid entrypoint')
    total = 0
    for name, source in files.items():
        if not isinstance(source, str) or not re.fullmatch(r'backend/[A-Za-z0-9][A-Za-z0-9_./-]{0,111}', name) or any(part in ('', '.', '..') for part in name.split('/')):
            raise ValueError('Invalid artifact path')
        path = root.joinpath(*PurePosixPath(name).parts)
        raw = base64.b64decode(source, validate=True) if name.rsplit('.', 1)[-1].lower() in {'jar', 'png', 'jpg', 'jpeg', 'webp', 'woff', 'woff2'} else source.encode('utf-8')
        total += len(raw)
        if len(raw) > 12 * 1024 * 1024 or total > 32 * 1024 * 1024:
            raise ValueError('Module exceeds limits')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    request = root / 'request.json'
    request.write_text(json.dumps(value['request']), encoding='utf-8')
    environment = {'PATH': os.environ.get('PATH', '/usr/local/bin:/usr/bin:/bin'), 'HOME': '/tmp',
                   'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1', 'PYTHONUNBUFFERED': '1',
                   'PYTHONPATH': '/worker/sdk', 'MESHABLE_REQUEST_FILE': str(request)}
    return runtime, str(root / entry), environment


if __name__ == '__main__':
    try:
        line = sys.stdin.buffer.readline(MAX_BOOTSTRAP + 1)
        if len(line) > MAX_BOOTSTRAP:
            raise ValueError('Module exceeds limits')
        root = Path('/tmp/plugin')
        root.mkdir(mode=0o700)
        runtime, entry, environment = prepare(json.loads(line), root)
        os.chdir(root)
        command = ['python', entry] if runtime == 'PYTHON' else ['java', '-Xmx192m', '-XX:ActiveProcessorCount=1', '-jar', entry]
        os.execvpe(command[0], command, environment)
    except Exception:
        sys.exit(2)  # Never print source, request bodies, credentials or native errors.
