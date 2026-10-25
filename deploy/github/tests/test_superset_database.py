import ast
from pathlib import Path
import types
import unittest
from urllib.parse import parse_qs, quote_plus, unquote_plus, urlencode, urlsplit


class SupersetDatabaseTests(unittest.TestCase):
    def uri(self, environment):
        source = Path(__file__).resolve().parents[3] / "superset/superset_config.py"
        tree = ast.parse(source.read_text(encoding="utf-8"))
        function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "workspace_database_uri")
        namespace = {"os": types.SimpleNamespace(environ=environment), "quote_plus": quote_plus, "urlencode": urlencode}
        exec(compile(ast.Module(body=[function], type_ignores=[]), str(source), "exec"), namespace)
        return namespace["workspace_database_uri"]()

    def test_local_connection_keeps_its_existing_defaults(self):
        self.assertEqual(self.uri({"DATABASE_PASSWORD": "test-only"}), "postgresql+psycopg2://superset_user:test-only@postgres:5432/superset")

    def test_managed_connections_verify_ca_and_preserve_literal_passwords(self):
        for mode in ("verify-full", "verify-ca"):
            uri = urlsplit(self.uri({
                "DATABASE_HOST": "postgres.internal.example.com", "DATABASE_USER": "reporting_user",
                "DATABASE_PASSWORD": "test-only-@'$/+pass", "DATABASE_SSL_MODE": mode,
                "DATABASE_SSL_ROOT_CERT": "/run/secrets/postgres-ca.pem",
            }))
            self.assertEqual(unquote_plus(uri.password), "test-only-@'$/+pass")
            self.assertEqual(uri.hostname, "postgres.internal.example.com")
            self.assertEqual(parse_qs(uri.query), {"sslmode": [mode], "sslrootcert": ["/run/secrets/postgres-ca.pem"]})

    def test_managed_tls_without_a_trust_file_is_rejected(self):
        with self.assertRaises(KeyError):
            self.uri({"DATABASE_PASSWORD": "test-only", "DATABASE_SSL_MODE": "verify-full"})
