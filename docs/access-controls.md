# Roles, views, and external storage access

The **Event log** feature (`events`) is administrator-only at `/admin/events`, under Administration → Operations. Old member grants cannot expose it. Both `/api/admin/events` and the legacy `/api/events` endpoint require an administrator role plus this feature; super admins retain access. See [event history](event-log.md).

Open **Administration → Access controls** (`/admin/access-controls`). Only super admins can change these policies. Access checks run on the server as well as in frontend navigation; hiding a view also blocks its API. Unknown feature endpoints fail closed for lower roles.

## Roles and user management

| Role | Default access |
| --- | --- |
| `SUPER_ADMIN` | All views, administration features, and selected storage roots; cannot be restricted by these policies |
| `ADMIN` | Administration and workspace views, subject to the super admin's feature and member restrictions |
| `USER` | Workspace views and shared storage, subject to member restrictions; no administration |
| `EXTERNAL` | Storage view only, read only, with no files visible until paths are explicitly granted |

On upgrade, a recorded, one-time migration promotes the earliest active existing admin to super admin. If an active super admin already exists, it records completion without promoting another account. On a new database the initial bootstrap admin receives that role. Restarting never promotes later delegated admins. Bootstrap accepts all five roles and preserves existing account roles and passwords.

The Users page is a scrollable, searchable row table. **Add new user** opens a dialog. Super admins can assign roles from the table, including **Business Manager**; delegated admins can create and manage members/external accounts, but cannot manage administrator accounts or assign administrator roles. New member accounts created by delegated admins inherit the creator's view and directory limits, and external accounts still start with no directory grants. Delegated admins cannot manage an account with broader access or grant uploads they do not possess. Role changes invalidate existing sessions. Self-deactivation and self-demotion are blocked, and the last active super admin is protected. Passwords continue to use salted bcrypt.

## Role features and member views

**Role features** sets the available views and administration areas for each lower role. Member access can narrow those choices further; it cannot override a feature disabled for the role. Access controls itself always belongs to super admins. Plugin access must also satisfy the plugin's enablement, supported-role, and table policies; granting a view never bypasses those policies. Calendar connections remain for internal members.

Only active plugins contribute views to the Role features and Member access lists. Disabled, removed, security-blocked, and dependency-blocked plugins are hidden, including their administration paths. Plugin changes refresh the access-control list. Saved role/member permissions are retained while a plugin is inactive and appear again when it is re-enabled.

Disabled views disappear from navigation and administration cards. Direct links redirect to an available workspace view or personal security settings. Personal MFA and notification preferences remain accessible. Permissions are read from current database state on API requests rather than trusting a role submitted by the browser.

## External sharing and directory grants

1. Create an external account manually under Users. No invitation flow or invitation email is implemented.
2. Under **Access controls → External sharing**, select that account.
3. Add allowed directories or individual files using the storage picker. The picker browses actual S3/Blob prefixes beneath already selected storage roots.
4. Add exclusions for files or directories inside those grants, then save.

Selecting a directory includes descendants and future files. Selecting one file permits navigation through its parent directories without granting sibling files. Paths are case sensitive and directory boundaries are respected (`client/` does not grant `client-other/`). Traversal paths are rejected. External members cannot upload even if an older account previously had upload permission.

Internal accounts can also be limited to selected storage paths under **Member access**. Without that switch, internal members keep the existing shared-root access, minus exclusions and applicable business protections. Disabling a storage root or connection still removes it for everyone.

**Protected files** defines paths excluded from all external members and external PDF shares. A protection can additionally block internal members and delegated admins. Exclusions and business protections always win over grants. They also follow the same bucket/prefix when it is mapped through another connection or root. Super admins retain workspace access. Existing online PDF-share links are rechecked against protections and the sender's current rights; an attachment already delivered by email cannot be recalled.

Storage lists, connection/root counts, browsing, uploads, downloads, PDF previews, and ZIP archives enforce these rules. Archives omit inaccessible entries. Keyword/vector retrieval applies storage rules before ranking limits, and AI excerpts are rechecked before and after answering. Plugin search remains subject to its separate view, role, and row policies. Events, audit logs, and MongoDB remain outside search.

## Persistence and APIs

`access_policy` stores a singleton policy as JSON text with an optimistic revision. Saves require the last loaded revision (`-1` before the first save); reload after a conflict. `account_role_migrations` records completion of the initial role promotion. Preserve these tables in PostgreSQL backups; the metadata export is not a complete policy backup.

| Endpoint | Access |
| --- | --- |
| `GET /api/access/me` | Signed-in account's effective features and feature catalogue |
| `GET /api/admin/access-controls` | Super admin policy editor |
| `PUT /api/admin/access-controls` | Super admin; `{revision, policy}` |
| `GET /api/admin/access-controls/storage-roots` | Super admin picker metadata; no credentials |
| `PATCH /api/admin/users/{id}/role` | Super admin; `{role}` |

Policy members contain optional `features`, `restrictStorage`, `grants`, and `blocks`. A storage rule is `{folderId, path, type}`, where `type` is `FOLDER` or `FILE` and the path is relative to the application storage root. `protectedPaths` contains `{rule, externalOnly}` entries. Directory paths are normalized with a trailing slash. Removing a grant, feature, or role takes effect on subsequent requests; navigation polls current feature permissions every 15 seconds.

Business Manager (`BUSINESS_MANAGER`) is available in user creation and the super-admin role selector. It has its own feature column and inherits existing Member role feature restrictions until explicitly configured. It can create business records but receives no admin authority or membership bypass. Plugins treat it as `USER`; scoped project manager/team-lead assignments remain separate from global account roles. See [creation and client access](projects-and-delivery.md#roles-and-team-boundaries).
