"""Bootstrap artifacts inside a disposable job's private tmpfs. Never runs in the control-plane service."""
import base64
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import tempfile

MAX_BOOTSTRAP = 48 * 1024 * 1024


def artifact_path(root, name):
    if (not isinstance(name, str)
            or not re.fullmatch(r'backend/[A-Za-z0-9][A-Za-z0-9_./-]{0,111}', name)
            or any(part in ('', '.', '..') or part.endswith('.') for part in name.split('/'))):
        raise ValueError('Invalid artifact path')
    path = root.joinpath(*PurePosixPath(name).parts).resolve()
    if not path.is_relative_to(root):
        raise ValueError('Artifact escapes the private job directory')
    return path


def write_private(path, raw):
    # Exclusive creation also refuses existing symlinks and hardlinks.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0), 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(raw)


def prepare(value, root):
    info = root.lstat()
    if (not stat.S_ISDIR(info.st_mode) or root.is_symlink()
            or getattr(root, 'is_junction', lambda: False)()
            or (os.name == 'posix' and (info.st_uid != os.getuid() or info.st_mode & 0o077))):
        raise ValueError('Use a private job directory')
    root = root.resolve(strict=True)
    files = value.get('files')
    entry = value.get('entrypoint', '')
    runtime = value.get('runtime')
    if runtime not in ('PYTHON', 'KOTLIN') or not isinstance(files, dict) or len(files) > 200:
        raise ValueError('Invalid module')
    if not isinstance(entry, str) or entry not in files or not entry.endswith('.py' if runtime == 'PYTHON' else '.jar'):
        raise ValueError('Invalid entrypoint')
    paths = {name: artifact_path(root, name) for name in files}
    total = 0
    for name, source in files.items():
        if not isinstance(source, str):
            raise ValueError('Invalid artifact source')
        path = paths[name]
        raw = base64.b64decode(source, validate=True) if name.rsplit('.', 1)[-1].lower() in {'jar', 'png', 'jpg', 'jpeg', 'webp', 'woff', 'woff2'} else source.encode('utf-8')
        total += len(raw)
        if len(raw) > 12 * 1024 * 1024 or total > 32 * 1024 * 1024:
            raise ValueError('Module exceeds limits')
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        write_private(path, raw)
    request = root / 'request.json'
    write_private(request, json.dumps(value['request']).encode('utf-8'))
    environment = {'PATH': os.environ.get('PATH', '/usr/local/bin:/usr/bin:/bin'), 'HOME': str(root), 'TMPDIR': str(root),
                   'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1', 'PYTHONUNBUFFERED': '1',
                   'PYTHONPATH': '/worker/sdk', 'MESHABLE_REQUEST_FILE': str(request)}
    return runtime, str(paths[entry]), environment


if __name__ == '__main__':
    try:
        line = sys.stdin.buffer.readline(MAX_BOOTSTRAP + 1)
        if len(line) > MAX_BOOTSTRAP:
            raise ValueError('Module exceeds limits')
        # Atomic random creation prevents pre-created paths in the temporary filesystem.
        root = Path(tempfile.mkdtemp(prefix='plugin-'))
        runtime, entry, environment = prepare(json.loads(line), root)
        os.chdir(root)
        command = ['python', entry] if runtime == 'PYTHON' else ['java', '-Xmx192m', '-XX:ActiveProcessorCount=1', '-jar', entry]
        os.execvpe(command[0], command, environment)
    except Exception:
        sys.exit(2)  # Never print source, request bodies, credentials or native errors.
