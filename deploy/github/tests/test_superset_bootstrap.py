import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("authoring_bootstrap", Path(__file__).resolve().parents[3] / "superset/bootstrap_admin.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SupersetBootstrapTests(unittest.TestCase):
    def test_legacy_public_authoring_permissions_are_revoked(self):
        manager = Mock()
        role = Mock(permissions=["can_write_dashboard"])
        manager.find_role.return_value = role
        module.restrict_public_role(manager)
        self.assertEqual(role.permissions, [])
        manager.get_session.commit.assert_called_once()

    def test_guest_role_never_inherits_authoring_permissions(self):
        manager = Mock()
        def permission(action):
            return SimpleNamespace(permission=SimpleNamespace(name=action), view_menu=SimpleNamespace(name="Dashboard"))
        read, write, delete = permission("can_read"), permission("can_write"), permission("can_delete_embedded")
        gamma = SimpleNamespace(permissions=[read, write, delete])
        guest = SimpleNamespace(permissions=[])
        manager.find_role.side_effect = lambda name: gamma if name == "Gamma" else guest
        module.ensure_guest_role(manager)
        self.assertEqual(guest.permissions, [read])

    def test_existing_accounts_are_never_reset(self):
        manager = Mock()
        manager.find_user.return_value = object()
        self.assertFalse(module.ensure_admin(manager, {"SUPERSET_ADMIN_USER": "owner"}))
        manager.add_user.assert_not_called()

    def test_new_admin_requires_explicit_password(self):
        manager = Mock()
        manager.find_user.return_value = None
        with self.assertRaises(ValueError):
            module.ensure_admin(manager, {})
        manager.add_user.assert_not_called()
        self.assertTrue(module.ensure_admin(manager, {"SUPERSET_ADMIN_PASSWORD": "strong-authoring-password"}))
        self.assertEqual(manager.add_user.call_args.args[-1], "strong-authoring-password")
