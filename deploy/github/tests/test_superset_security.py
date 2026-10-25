"""Validate account API configuration without needing Superset in the test runner."""
import importlib.util
import os
from pathlib import Path
import sys
from types import ModuleType
import unittest
from unittest.mock import patch


class SupersetSecurityTests(unittest.TestCase):
    def test_account_apis_remain_admin_only_when_superset_syncs_author_roles(self):
        class BaseSecurityManager:
            ADMIN_ONLY_VIEW_MENUS = {"ExistingAdminResource"}

        security = ModuleType("superset.security")
        security.SupersetSecurityManager = BaseSecurityManager
        cache = ModuleType("flask_caching.backends.rediscache")
        cache.RedisCache = object
        path = Path(__file__).resolve().parents[3] / "superset/superset_config.py"
        spec = importlib.util.spec_from_file_location("superset_account_config", path)
        config = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {"superset.security": security, "flask_caching.backends.rediscache": cache}), patch.dict(os.environ, {
            "SUPERSET_SECRET_KEY": "test-only-key", "DATABASE_PASSWORD": "test-only-password", "GUEST_TOKEN_JWT_SECRET": "test-only-guest-key",
        }):
            spec.loader.exec_module(config)
        self.assertTrue(config.FAB_ADD_SECURITY_API)
        self.assertTrue(issubclass(config.CUSTOM_SECURITY_MANAGER, BaseSecurityManager))
        self.assertTrue({"User", "Role", "Permission", "ViewMenu", "PermissionViewMenu", "ExistingAdminResource"}.issubset(
            config.CUSTOM_SECURITY_MANAGER.ADMIN_ONLY_VIEW_MENUS))
        self.assertEqual(BaseSecurityManager.ADMIN_ONLY_VIEW_MENUS, {"ExistingAdminResource"})


if __name__ == "__main__":
    unittest.main()
