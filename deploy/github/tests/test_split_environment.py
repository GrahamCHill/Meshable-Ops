import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("split_start", Path(__file__).resolve().parents[2] / "terraform/start.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SplitEnvironmentTests(unittest.TestCase):
    def settings(self, role):
        return {key: "test-only-value" for key in module.ROLES[role] | {
            "APP_DOMAIN", "ACME_EMAIL", "HOST_PRIVATE_IP", "APP_PRIVATE_IP", "SERVICES_PRIVATE_IP", "RUNTIME_PRIVATE_IP", "POSTGRES_HOST", "POSTGRES_ADMIN_USER",
        }}

    def test_missing_active_service_credentials_fail_closed(self):
        for role in module.ROLES:
            values = self.settings(role)
            module.compose_environment(values, role)
            for key in module.ROLES[role]:
                with self.assertRaisesRegex(ValueError, key):
                    module.compose_environment({name: value for name, value in values.items() if name != key}, role)

    def test_inactive_placeholders_do_not_replace_real_settings(self):
        values = self.settings("runtime")
        values["APP_PLUGIN_RUNNER_TOKEN"] = "a-real-token"
        result = module.compose_environment(values, "runtime")
        self.assertEqual(result["APP_PLUGIN_RUNNER_TOKEN"], "a-real-token")
        self.assertEqual(result["POSTGRES_PASSWORD"], "inactive-profile")
        self.assertEqual(result["JWT_SECRET"], "inactive-profile")
        values["COMPOSE_PROFILES"] = "local-database,app,services,runtime"
        self.assertEqual(module.compose_environment(values, "runtime")["COMPOSE_PROFILES"], "runtime")
