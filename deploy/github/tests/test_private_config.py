import os
from contextlib import chdir
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from private_config import open_private, open_terraform_secrets


class PrivateConfigTests(unittest.TestCase):
    def test_only_named_environment_files_without_parent_traversal_are_allowed(self):
        for path in (Path('/tmp/shadow'), Path('/tmp/../etc/.env'), Path('/tmp/settings.json')):
            with self.assertRaises(ValueError): open_private(path, create=True)

    @unittest.skipUnless(os.name == 'posix', 'Production directory-handle safety uses POSIX APIs')
    def test_symlink_ancestors_and_hardlinked_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            root=Path(directory); (root/'real').mkdir(); (root/'alias').symlink_to(root/'real', target_is_directory=True)
            with self.assertRaises(OSError): open_private(root/'alias/.env', create=True)
            (root/'.env').write_text('private')
            os.link(root/'.env', root/'real/.env')
            with self.assertRaises(ValueError): open_private(root/'real/.env')

    def test_first_create_cannot_overwrite_an_existing_environment(self):
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            target=Path(directory)/'.env'
            os.close(open_private(target, create=True))
            with self.assertRaises(FileExistsError): open_private(target, create=True)

    def test_absolute_paths_and_prefix_siblings_outside_trusted_roots_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'workspace'
            root.mkdir()
            sibling = Path(directory) / 'workspace-other'
            with chdir(root), patch.dict(os.environ, {'DEPLOY_PATH': str(Path(directory) / 'deployment')}):
                for filename in ('.env', '.env.production', 'bootstrap.auto.tfvars.json'):
                    opener = open_terraform_secrets if filename.endswith('.json') else open_private
                    for parent in (Path(directory), sibling):
                        with self.subTest(filename=filename, parent=parent), self.assertRaises(ValueError):
                            opener(parent / filename)
                self.assertFalse(sibling.exists())
                self.assertFalse((Path(directory) / '.env').exists())

    def test_local_nested_files_and_separate_deployment_root_are_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'workspace'
            root.mkdir()
            deployment = Path(directory) / 'deployment'
            with chdir(root), patch.dict(os.environ, {'DEPLOY_PATH': str(deployment)}):
                for path in (Path('nested/.env'), deployment / 'shared/.env.production'):
                    os.close(open_private(path, create=True))
                    self.assertTrue(path.is_file())
                    if os.name == 'posix':
                        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
                with self.assertRaises(ValueError):
                    open_terraform_secrets(deployment / 'shared/bootstrap.auto.tfvars.json')

    def test_windows_parent_aliases_and_invalid_deployment_roots_are_rejected(self):
        for path in (Path('.. /.env'), Path('folder./.env'), Path('folder\x00/.env')):
            with self.assertRaises(ValueError):
                open_private(path, create=True)
        for deployment in ('', 'relative', str(Path.cwd().anchor), str(Path.cwd() / '../other')):
            with patch.dict(os.environ, {'DEPLOY_PATH': deployment}), self.assertRaises(ValueError):
                open_private(Path('.env'), create=True)
