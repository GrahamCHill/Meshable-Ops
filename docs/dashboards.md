# Embedded dashboards and Superset authoring

Meshable Ops displays role-controlled analytics through its own application shell. Superset remains an internal service; admins author reports through the app's Superset workspace.

## Publish and navigate dashboards

Dashboard embeds are runtime-managed from the application.

1. Sign in as an admin.
2. Open `/admin/superset-graph-builder` to author or edit dashboards in Superset.
3. Open `/admin/dashboard-links`.
4. Select a Superset dashboard and configure its label, order, state, and allowed roles.

Each shared dashboard entry stores:

- display label
- description
- the selected numeric Superset dashboard id
- an embed UUID resolved and refreshed internally by Kotlin (not editable in the browser)
- sort order
- enabled state
- allowed app roles

The dashboard viewer keeps its selection in `/dashboards?dashboard=<app-dashboard-id>`
so links and browser refresh preserve the selected report. It includes previous/next,
reload, and expanded-view controls, and keeps the dashboard's own Superset tabs visible.
The admin Superset workspace provides direct Dashboards, Charts, and Datasets navigation,
a return-to-list action, and a shortcut to Dashboard links. Press Escape to leave expanded view.

## Superset login

Sign in to the app as an admin with **Superset authoring** access and open `/admin/superset-graph-builder`. Choose **Set or reset Superset password**, enter and confirm a password of 14–128 characters, then sign in to the Superset workspace using your email address and that password. The same button replaces a forgotten Superset password without changing your application password. The analytics session uses its own HttpOnly cookie.

The existing `admin.superset` feature controls both this button's page and the backend password endpoint. Super admins can grant or narrow it through **Access controls**, including per-member restrictions on admins. Dashboard-link management is a separate permission; no new application role is required.

First use creates a personal Superset account with your email as both username and email and the built-in **Alpha** role. Alpha authors can use all configured data sources and create dashboards, charts, and datasets; they can edit their own objects and cannot administer other users. Subsequent resets preserve the account ID and content ownership. Conflicting usernames/emails, disabled accounts, and accounts with a different role require a Superset administrator's attention and are never overwritten by this control. Passwords and service credentials are never returned to the browser, emailed, or included in audit records. Resets are limited to five attempts per account per five minutes.

The backend uses Superset's Flask-AppBuilder user-management API (`FAB_ADD_SECURITY_API = True`) with internal service credentials. `WorkspaceSecurityManager` keeps FAB user, role, and permission API resources exclusive to Superset Admins when Superset synchronizes author roles. Restart Superset and run its normal `superset init` initialization when applying this configuration to an existing deployment. On first startup, initialization creates only the explicitly configured service admin using `SUPERSET_ADMIN_PASSWORD` in the private server environment; personal accounts are created on demand. Existing accounts and passwords are preserved on restarts. No default datasets, charts, dashboard links, or sample dashboards are created. Rotate the service admin password through Superset's security tools and update the backend's API credentials together; the personal password button does not reset that service account.

`POST /api/superset/account/password` accepts only `{ "password": "..." }` for the authenticated admin and returns `{ "username": "admin@example.com", "loginPath": "/login/", "created": true }`. It accepts no target email, role, or user ID. The endpoint requires an active app-admin session and `admin.superset`; dashboard viewers and admins with only Dashboard links access cannot use it.

Older setup helpers assigned the app owner's email to the separate Superset service admin. On that owner's first personal setup, the backend moves only the configured service account's email to `superset-service-<id>@accounts.invalid`, preserving its username, current service password, role, and content ownership. This frees the owner's email for a personal Alpha account. Other conflicting or elevated accounts remain protected.

## Access boundary

The backend mints guest tokens and synchronizes allowed app origins. The frontend proxy enforces admin access to Superset authoring routes; hiding navigation alone is not authorization. Anonymous browsers have no Superset permissions and are redirected to its login form before authoring. Existing Public permissions copied from Gamma are cleared by initialization; guest JWTs use a separate read-only EmbeddedGuest role with dashboard resource claims. Authoring API requests also require an app-admin session. Guest-token API requests strip ambient analytics cookies and bearer credentials before Superset validates the guest token. The proxy preserves native `/superset/dashboard/...` paths rather than stripping that namespace. Keep Superset's own port private. See [architecture](architecture.md) and [deployment](deployment.md).
