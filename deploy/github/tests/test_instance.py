import base64
import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from instance import environment, local_environment, hostname, password, write_new
from preflight import validate


class InstanceTests(unittest.TestCase):
    def test_hostname_rejects_paths_commands_and_invalid_dns(self):
        for value in ("https://example.com", "example.com/path", "example.com:443", "x;echo hello", "bad..example.com", "-bad.example.com"):
            with self.assertRaises(ValueError):
                hostname(value)
        self.assertEqual(hostname("Docs.Example.com"), "docs.example.com")

    def test_password_byte_limit_and_controls(self):
        for value in ("short", "x" * 73, "é" * 37, "long-password-with\nnewline"):
            with self.assertRaises(ValueError):
                password(value)
        self.assertEqual(password("long-and-strong-password"), "long-and-strong-password")

    def test_setup_uses_independent_random_keys_and_refuses_overwrite(self):
        first = environment("app.example.com", "owner@example.com", "ops@example.com", "private-password-1234")
        second = environment("app.example.com", "owner@example.com", "ops@example.com", "private-password-1234")
        self.assertNotEqual(first, second)
        self.assertNotIn("admin@app.local", first)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "settings"
            write_new(path, first)
            with self.assertRaises(FileExistsError):
                write_new(path, second)
            self.assertEqual(path.read_text(), first)
            if os.name == "posix":
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_local_setup_uses_selected_admin_and_generated_service_credentials(self):
        content = local_environment("owner@example.com", "private-password-1234")
        self.assertIn("APP_PUBLIC_BASE_URL='http://localhost:3000'", content)
        self.assertIn('owner@example.com', content)
        self.assertIn('MINIO_ROOT_PASSWORD=', content)
        self.assertIn('AZURITE_ACCOUNT_KEY=', content)
        self.assertNotIn('admin@app.local', content)

    def config(self):
        pg, mongo = "a" * 64, "b" * 64
        env = {"APP_PUBLIC_BASE_URL": "https://app.example.com", "SPRING_PROFILES_ACTIVE": "docker,prod",
               "APP_AUTH_COOKIE_SECURE": "true", "APP_SECRETS_ENCRYPTION_KEY": base64.b64encode(b"k" * 32).decode(),
               "APP_BOOTSTRAP_USERS_JSON": json.dumps([{"email": "owner@example.com", "password": "private-password-1234", "role": "ADMIN"}]),
               "JWT_SECRET": "c" * 64, "APP_DOCUMENT_RENDER_TOKEN": "d" * 64, "SUPERSET_ADMIN_PASSWORD": "e" * 48,
               "POSTGRES_PASSWORD": pg, "MONGO_URI": f"mongodb://app_user:{mongo}@mongodb:27017/app_db?authSource=admin"}
        services = {name: {"environment": {"DATABASE_PASSWORD": pg, "SUPERSET_SECRET_KEY": "f" * 64, "GUEST_TOKEN_JWT_SECRET": "9" * 64}} for name in ("superset", "superset-init", "superset-worker", "superset-beat")}
        services.update({"backend": {"environment": env}, "caddy": {"environment": {"APP_DOMAIN": "app.example.com", "ACME_EMAIL": "ops@example.com"}},
                         "postgres": {"environment": {"POSTGRES_PASSWORD": pg}, "volumes": [{"target": "/docker-entrypoint-initdb.d/init.sql", "source": "/repo/docker/postgres/init.sql"}]},
                         "mongodb": {"environment": {"MONGO_INITDB_ROOT_PASSWORD": mongo}}, "frontend": {}})
        return {"services": services}

    def test_preflight_blocks_exposed_services_example_secrets_and_wrong_connections(self):
        good = self.config()
        self.assertEqual(validate(good), "app.example.com")
        mutations = [
            lambda s: s["frontend"].update(ports=[{"published": "3000"}]),
            lambda s: s["backend"]["environment"].update(APP_AUTH_COOKIE_SECURE="false"),
            lambda s: s["superset"]["environment"].update(SUPERSET_SECRET_KEY="replace-with-a-strong-secret-key-1234"),
            lambda s: s["backend"]["environment"].update(POSTGRES_PASSWORD="wrong"),
            lambda s: s["backend"]["environment"].update(APP_BOOTSTRAP_USERS_JSON="[]"),
            lambda s: s["postgres"].update(volumes=[]),
            lambda s: s["backend"]["environment"].update(APP_SECRETS_ENCRYPTION_KEY=base64.b64encode(b"short").decode()),
        ]
        for mutate in mutations:
            config = copy.deepcopy(good)
            mutate(config["services"])
            with self.assertRaises(ValueError):
                validate(config)


if __name__ == "__main__":
    unittest.main()
