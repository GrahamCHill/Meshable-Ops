# Administration and access

Open **Administration** (`/admin`) to see cards for each section. Admin sections have return navigation to Administration; they are not nested items in the main sidebar. The existing `ADMIN` role manages the workspace, including shared storage; there is no separate super-admin role.

| Area | Route | Purpose |
| --- | --- | --- |
| Users & access | `/admin/users` | Accounts, activation, and independent upload permissions |
| Branding | `/admin/branding` | Business name, sidebar/link colours, logo/favicon, and sign-in backgrounds |
| Plugins | `/admin/plugins` | Enable installed modules and choose administrator or all-user access |
| Storage connections | `/admin/storage` | Named S3/Blob locations, shared folders, indexing jobs |
| Document access | `/admin/documents` | PDF viewing, download policies, and watermarks |
| Email delivery | `/admin/email` | SMTP/API services and manual messages |
| Security & MFA | `/account/security` | Personal authenticator/email enrollment and recovery codes |
| File sharing | `/admin/shares` | Protected PDF email attachments and online links |
| Webhooks | `/admin/webhooks` | Authenticated JSON receipt and manual outgoing requests |
| Document templates | `/admin/document-templates` | WeasyCanvas designer, typed/derived fields, publishing |
| AI & search | `/admin/ai` | Search, launcher, indexing switches, optional providers |
| Superset workspace | `/admin/superset-graph-builder` | Dashboard, chart, and dataset authoring |
| Dashboard links | `/admin/dashboard-links` | Select, label, order, and restrict shared dashboards |
| System health | `/admin/system` | Service connectivity and runtime details |
| Audit & backup | `/admin/audit` | Audit history and application metadata export |

Users open shared storage at `/storage`, insights at `/dashboards`, their generated PDF library at `/documents`, and workflow diagrams at `/workflows`. Search and chat controls follow the admin feature switches. Generated documents and workflows are owner-scoped; admins can manage all of them.

## User access management

Admins manage access from `/admin/users`.

Current admin capabilities include:

- create users
- review the user roster
- deactivate users
- grant or revoke the independent document-upload permission
- review live stack and network-boundary status from `/admin/system`
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
