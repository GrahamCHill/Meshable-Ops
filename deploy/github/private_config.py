"""Open only named environment files, without traversing symlinks or following file aliases."""
import os
from pathlib import Path
import stat


def open_private(path: Path, *, create: bool = False) -> int:
    roots = (Path.cwd(),)
    deployment_path = os.environ.get('DEPLOY_PATH')
    if deployment_path is not None or os.name == 'posix':
        deployment = Path(deployment_path if deployment_path is not None else '/srv/weaveops')
        if not deployment.is_absolute() or '..' in deployment.parts or deployment == Path(deployment.anchor):
            raise ValueError('DEPLOY_PATH must be an absolute deployment directory without parent traversal')
        roots += (deployment / 'shared',)
    return _open_private(path, create=create, names={'.env', '.env.production'},
                         roots=roots)


def open_terraform_secrets(path: Path) -> int:
    return _open_private(path, create=True, names={'bootstrap.auto.tfvars.json'}, roots=(Path.cwd(),))


def _open_private(path: Path, *, create: bool, names: set[str], roots: tuple[Path, ...]) -> int:
    if (path.name not in names or '..' in path.parts or any(ord(char) < 32 for char in str(path))
            or any(part.endswith((' ', '.')) for part in path.parts)):
        raise ValueError('Use an allowed private configuration filename without parent traversal')
    target = Path(os.path.abspath(path))
    # The CLI chooses a file within an independently trusted root, never the root itself.
    # Keep this lexical: the handle walk below must see and reject symlink ancestors.
    if not any(target.is_relative_to(root) for root in roots):
        raise ValueError('Keep private configuration inside the working directory or deployment shared directory')
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
    try:
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            raise ValueError('Use a regular, unlinked environment file')
    except Exception:
        os.close(descriptor)
        raise
    return descriptor
