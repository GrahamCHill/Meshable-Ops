import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("provision_secrets", Path(__file__).resolve().parents[1] / "provision-secrets.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ProvisionSecretsTests(unittest.TestCase):
    def test_missing_runtime_fields_are_generated_once(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "settings"
            path.write_text("APP_DOMAIN=example.com\n")
            module.complete(path)
            first = path.read_text()
            self.assertIn('POSTGRES_PASSWORD=', first)
            self.assertIn('APP_SECRETS_ENCRYPTION_KEY=', first)
            module.complete(path)
            self.assertEqual(path.read_text(), first)

    def test_existing_values_including_blank_keys_are_never_rotated(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "settings"
            path.write_text("APP_SECRETS_ENCRYPTION_KEY=''\nPOSTGRES_PASSWORD='retained-value'\n")
            module.complete(path)
            content = path.read_text()
            self.assertEqual(content.count('APP_SECRETS_ENCRYPTION_KEY='), 1)
            self.assertIn("APP_SECRETS_ENCRYPTION_KEY=''", content)
            self.assertIn("POSTGRES_PASSWORD='retained-value'", content)
