import importlib.util
import base64
from contextlib import chdir
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location("terraform_secrets", Path(__file__).resolve().parents[1] / "setup-terraform-secrets.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TerraformSecretsTests(unittest.TestCase):
    def test_independent_secrets_and_explicit_super_admin_preserve_password(self):
        secret_password = "a-strong-$pass'word-for-tests"
        first = module.configuration("Owner@Example.com", secret_password)
        second = module.configuration("Owner@Example.com", secret_password)
        self.assertNotEqual(first["jwt_secret"], second["jwt_secret"])
        service_secrets = [first[key] for key in first if key not in {"bootstrap_users_json", "secrets_encryption_key"}]
        self.assertEqual(len(set(service_secrets)), 9)
        self.assertTrue(all(len(value) >= 48 for value in service_secrets))
        self.assertEqual(len(base64.b64decode(first["secrets_encryption_key"])), 32)
        master = first["postgres_admin_password"]
        self.assertTrue(any(char.isupper() for char in master))
        self.assertTrue(any(char.islower() for char in master))
        self.assertTrue(any(char.isdigit() for char in master))
        self.assertTrue(any(not char.isalnum() for char in master))
        self.assertEqual(json.loads(first["bootstrap_users_json"]), [{
            "email": "owner@example.com", "username": "admin", "password": secret_password, "role": "SUPER_ADMIN",
        }])

    def test_private_file_is_valid_json_and_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            path = Path(directory) / "bootstrap.auto.tfvars.json"
            values = module.configuration("owner@example.com", "private-password-1234")
            module.write_new(path, values)
            self.assertEqual(json.loads(path.read_text()), values)
            with self.assertRaises(FileExistsError):
                module.write_new(path, module.configuration("other@example.com", "private-password-5678"))
            self.assertEqual(json.loads(path.read_text()), values)
            if os.name == "posix":
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_rejects_weak_password_invalid_email_and_unexpected_output(self):
        for address, value in [("bad-email", "private-password-1234"), ("owner@example.com", "short")]:
            with self.assertRaises(ValueError):
                module.configuration(address, value)
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            with self.assertRaises(ValueError):
                module.write_new(Path(directory) / "settings.json", {})

    @unittest.skipUnless(os.name == "posix", "Production uses POSIX directory handles")
    def test_symlink_parent_cannot_redirect_bootstrap_secrets(self):
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            root = Path(directory)
            (root / "real").mkdir()
            (root / "alias").symlink_to(root / "real", target_is_directory=True)
            with self.assertRaises(OSError):
                module.write_new(root / "alias" / "bootstrap.auto.tfvars.json", {})
