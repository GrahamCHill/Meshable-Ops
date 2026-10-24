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

Superset authoring uses an account separate from the application's admin. The setup helper generates its password as `SUPERSET_ADMIN_PASSWORD` in the private server environment. Sign in to the app as an admin, open `/admin/superset-graph-builder`, and sign in with that authoring account before creating or editing reports. The analytics session uses its own HttpOnly cookie.

On first startup, Superset creates only the explicitly configured admin. Existing accounts and changed passwords are preserved on restarts. No default datasets, charts, dashboard links, or sample dashboards are created. Author your reports and publish app links through the controls above. To rotate an existing Superset password, use its account/security tools and update the backend's API credentials together.

## Access boundary

The backend mints guest tokens and synchronizes allowed app origins. The frontend proxy enforces admin access to Superset authoring routes; hiding navigation alone is not authorization. Anonymous browsers have no Superset permissions and are redirected to its login form before authoring. Existing Public permissions copied from Gamma are cleared by initialization; guest JWTs use a separate read-only EmbeddedGuest role with dashboard resource claims. Authoring API requests also require an app-admin session. Guest-token API requests strip ambient analytics cookies and bearer credentials before Superset validates the guest token. The proxy preserves native `/superset/dashboard/...` paths rather than stripping that namespace. Keep Superset's own port private. See [architecture](architecture.md) and [deployment](deployment.md).
