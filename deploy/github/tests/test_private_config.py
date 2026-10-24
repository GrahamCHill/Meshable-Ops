import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from private_config import open_private


class PrivateConfigTests(unittest.TestCase):
    def test_only_named_environment_files_without_parent_traversal_are_allowed(self):
        for path in (Path('/tmp/shadow'), Path('/tmp/../etc/.env'), Path('/tmp/settings.json')):
            with self.assertRaises(ValueError): open_private(path, create=True)

    @unittest.skipUnless(os.name == 'posix', 'Production directory-handle safety uses POSIX APIs')
    def test_symlink_ancestors_and_hardlinked_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); (root/'real').mkdir(); (root/'alias').symlink_to(root/'real', target_is_directory=True)
            with self.assertRaises(OSError): open_private(root/'alias/.env', create=True)
            (root/'.env').write_text('private')
            os.link(root/'.env', root/'real/.env')
            with self.assertRaises(ValueError): open_private(root/'real/.env')

    def test_first_create_cannot_overwrite_an_existing_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'.env'
            os.close(open_private(target, create=True))
            with self.assertRaises(FileExistsError): open_private(target, create=True)
