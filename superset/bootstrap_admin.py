"""Create the explicitly configured authoring admin once; preserve existing accounts."""
import os


def ensure_admin(manager, environment):
    username = environment.get("SUPERSET_ADMIN_USER", "admin")
    if manager.find_user(username=username):
        return False
    password = environment.get("SUPERSET_ADMIN_PASSWORD", "")
    if len(password) < 14:
        raise ValueError("Configure a Superset admin password of at least 14 characters")
    role = manager.find_role(manager.auth_role_admin) or manager.add_role(manager.auth_role_admin)
    user = manager.add_user(username, environment.get("SUPERSET_ADMIN_FIRSTNAME", "Admin"),
                            environment.get("SUPERSET_ADMIN_LASTNAME", "User"),
                            environment.get("SUPERSET_ADMIN_EMAIL", "admin@example.com"), role, password)
    if not user:
        raise RuntimeError("Unable to create the configured Superset admin")
    return True


def restrict_public_role(manager):
    """Revoke permissions copied by older PUBLIC_ROLE_LIKE=Gamma installs."""
    role = manager.find_role("Public")
    if role and role.permissions:
        role.permissions = []
        manager.get_session.commit()


def ensure_guest_role(manager):
    """Keep guest-token readers separate from both public and authoring roles."""
    allowed = {("can_read", name) for name in
               ("Chart", "Dashboard", "Dataset", "EmbeddedDashboard", "Explore")}
    allowed.update((permission, "Superset") for permission in
                   ("can_dashboard", "can_explore_json", "can_slice", "can_log", "can_fetch_datasource_metadata"))
    gamma = manager.find_role("Gamma")
    role = manager.find_role("EmbeddedGuest") or manager.add_role("EmbeddedGuest")
    role.permissions = [p for p in gamma.permissions
                        if (p.permission.name, p.view_menu.name) in allowed]
    manager.get_session.commit()


if __name__ == "__main__":
    from superset.app import create_app
    from superset import security_manager
    with create_app().app_context():
        ensure_admin(security_manager, os.environ)
        restrict_public_role(security_manager)
        ensure_guest_role(security_manager)
    print("Superset admin account ready; existing credentials were preserved")
