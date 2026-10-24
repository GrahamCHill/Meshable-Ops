"""Open only named environment files, without traversing symlinks or following file aliases."""
import os
from pathlib import Path
import stat


def open_private(path: Path, *, create: bool = False) -> int:
    if path.name not in {'.env', '.env.production'} or '..' in path.parts or any(ord(char) < 32 for char in str(path)):
        raise ValueError('Use a .env or .env.production file without parent traversal')
    target = Path(os.path.abspath(path))
    flags = os.O_RDWR | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_CLOEXEC', 0)
    if create:
        flags |= os.O_CREAT | os.O_EXCL
    if os.open in os.supports_dir_fd:
        # Directory handles prevent an ancestor being swapped for a symlink between checks and I/O.
        directory = os.open(target.anchor, os.O_RDONLY | os.O_DIRECTORY)
        try:
            for part in target.parent.parts[1:]:
                if create:
                    try: os.mkdir(part, mode=0o700, dir_fd=directory)
                    except FileExistsError: pass
                next_directory = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
                os.close(directory); directory = next_directory
            descriptor = os.open(target.name, flags, 0o600, dir_fd=directory)
        finally:
            os.close(directory)
    else:
        # Native Windows setup also refuses junctions; production provisioning uses the handle-based path above.
        for node in [target, *target.parents]:
            if node.is_symlink() or getattr(node, 'is_junction', lambda: False)():
                raise ValueError('Use a regular environment file without symlink or junction ancestors')
        if create: target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        descriptor = os.open(target, flags, 0o600)
    info = os.fstat(descriptor)
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        os.close(descriptor)
        raise ValueError('Use a regular, unlinked environment file')
    return descriptor
