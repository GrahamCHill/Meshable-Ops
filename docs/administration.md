# Administration and access

Plugin connection settings can appear as direct cards within existing admin categories or plugin-defined categories; the main Plugins catalogue retains activation/data/removal controls. Calendar and Git providers add Calendars/Development groups. Plugin-specific Settings tabs are omitted when no editable schema is supplied. See [plugin presentation](plugins/presentation-and-lifecycle.md). Personal security and notifications are grouped under the header account menu's [User settings](user-settings.md).

Open **Administration** (`/admin`) to see cards for each section. Admin sections have return navigation to Administration; they are not nested items in the main sidebar. `SUPER_ADMIN` manages role features, member views, and directory access. Delegated `ADMIN` accounts manage only the areas permitted by a super admin. See [access controls](access-controls.md).

Related settings are consolidated into seven card groups: **Workspace**, **People & security**, **Storage & sharing**, **Communications**, **Documents & workflows**, **Analytics & search**, and **Operations**. Each group opens a compact settings menu at `/admin/sections/<group-id>`. Existing settings URLs still work; breadcrumbs return to either their group or the admin overview. Restricted cards are hidden according to the same feature permissions as their destination pages.

| Area | Route | Purpose |
| --- | --- | --- |
| Users | `/admin/users` | Row-based roster, role assignment, activation, upload permissions, and Add user dialog |
| Project settings | `/admin/projects` | Ordered project sections/default, tags, described team/department groups, scoped project roles, and memberships |
| Access controls | `/admin/access-controls` | Super admin role features, member views, external sharing, and protected cloud paths |
| Security | `/admin/security` | Super admin global third-party extension switch; links to access controls and personal MFA |
| Branding | `/admin/branding` | Business name, sidebar/link colours, logo/favicon, and sign-in backgrounds |
| Plugins | `/admin/plugins` | ZIP installation by super admins; module settings, roles, tables, and search |
| Storage connections | `/admin/storage` | Named S3/Blob locations, shared folders, indexing jobs |
| Document access | `/admin/documents` | PDF viewing, download policies, and watermarks |
| Notifications | `/admin/notifications` | Delivery methods, per-method messages, workflow alerts, and delivery history |
| Email connection | `/admin/email` | SMTP/API configuration, reached from Notifications > Delivery methods |
| Security & MFA | `/account/security` | Personal authenticator/email enrollment and recovery codes |
| File sharing | `/admin/shares` | Protected PDF email attachments and online links |
| Webhooks | `/admin/webhooks` | Authenticated JSON receipt and manual outgoing requests |
| Workflow builder | `/admin/workflows` | Design, validate, and publish guided processes |
| Document templates | `/admin/document-templates` | WeasyCanvas designer, typed/derived fields, publishing |
| AI & search | `/admin/ai` | Search, launcher, indexing switches, optional providers |
| Superset workspace | `/admin/superset-graph-builder` | Dashboard, chart, and dataset authoring |
| Dashboard links | `/admin/dashboard-links` | Select, label, order, and restrict shared dashboards |
| System health | `/admin/system` | Service connectivity and runtime details |
| Event log | `/admin/events` | Administrator-only, scrollable history with progressive loading and event details |
| Audit & backup | `/admin/audit` | Audit history and application metadata export |

Users open shared storage at `/storage`, insights at `/dashboards`, their generated PDF library at `/documents`, and guided workflows at `/workflows`. Search and chat controls follow the admin feature switches. Admins manage workflow designs and generated documents. Each workflow run and its progress are private to the member who started it, including against other admins. Published workflows grant access to their PDFs through the run as document/email steps are reached.

## User access management

Admins manage accounts from `/admin/users`. Super admins configure feature and storage permissions in `/admin/access-controls`; external members start with no file grants. User creation is manual; invitations remain deferred.

Current admin capabilities include:

- create users
- review the user roster
- deactivate users
- grant or revoke the independent document-upload permission
- review live stack and network-boundary status from `/admin/system`
- browse the full event history from **Operations → Event log**; see [event log controls and APIs](event-log.md)
- review audit activity and export backups from `/admin/audit`
- manage object storage and vector reindexing from `/admin/storage`

User lifecycle behavior:

- self-service registration is disabled
- production bootstrap users come from `APP_BOOTSTRAP_USERS_JSON`
- deleting a user from the admin UI is a deactivation flow, not a hard delete
- inactive users can no longer authenticate

## Authentication and credentials

Application passwords use salted bcrypt at cost 12. Authenticator/email MFA is optional; the primary admin receives a setup prompt. Email enrollment requires configured delivery and a received verification code. See [MFA](mfa.md).

Saved provider credentials are encrypted with the persistent server wrapping key and are not returned to the browser. Environment and admin settings are separate configuration sources. See [credential security](ai-search-and-secrets.md). Metadata exports omit credentials and are not complete recovery backups; retain database backups, original files, and wrapping keys separately.

Server-side ownership, role, feature, and storage-access checks enforce permissions. Superset authoring also has an authenticated proxy boundary. Download permission and watermark policies apply independently of upload permission. See [architecture](architecture.md), [document access](document-access.md), and the [documentation index](README.md) for each feature's controls.
