# Meshable Ops documentation

Meshable Ops combines shared storage, document generation, workflow design, search, communication, and analytics in one business workspace. Start with local setup or choose the guide for your task.

The implementation uses PostgreSQL/pgvector for storage metadata, content chunks, search, and configuration; S3/Azure Blob retains original files. MongoDB continues to store events and audit history. MongoDB search and OCR integration are deferred.

## Start here

| Task | Guide |
| --- | --- |
| Understand AGPL, commercial licensing, and upstream notices | [Licensing](../LICENSING.md) |
| License plugins and compatible external-service integration components | [Plugin licensing](plugins/licensing.md) |
| Contribute changes and understand the contributor grant | [Contributing](../CONTRIBUTING.md) · [CLA](../CLA.md) |
| Start the development stack with MinIO or Azurite | [Development setup](getting-started.md) |
| Understand empty databases, initial access, required secrets, and upgrades | [Initialization and security](initialization-and-security.md) |
| Keep the workspace out of compliant search and AI crawlers | [Crawler exclusion](crawler-exclusion.md) |
| Create a versioned template copy and deploy automatically over SSH | [GitHub Actions deployment](github-deployment.md) |
| Set a business name, colours, logos, and sign-in backgrounds | [Branding](branding.md) |
| Set a profile photo, personal settings, and navigation preferences | [User settings](user-settings.md) |
| Add plugin artwork, categories, admin shortcuts, dependencies, and screen panels | [Plugin presentation and lifecycle](plugins/presentation-and-lifecycle.md) |
| Declare compatible plugin versions and preserve extensions across app updates | [Plugin compatibility and updates](plugins/compatibility-and-updates.md) |
| Create plugins with pages, submenus, tables, and search | [Plugin authoring](plugins/README.md) |
| Configure work boards, ticket movement, epics and project visuals | [Kanban boards](plugins/kanban.md) |
| Upload ZIP plugins and configure third-party extension security | [Runtime ZIP packages](plugins/zip-packages.md) |
| Install frontend and/or Python/Kotlin backend code from a ZIP | [Uploaded backend runtime and SDKs](plugins/backend-runtime.md) |
| Link Git repositories/PRs to boards and cards | [Source-control plugins](plugins/source-control.md) |
| Use day/week/month views, private member sync, and board deadlines | [Calendar plugins](plugins/calendar.md) |
| Add dependencies and optional connections between plugins | [Plugin composition](plugins/composition.md) |
| Restrict roles, member views, client directories, and protected files | [Access controls](access-controls.md) |
| Find admin sections and manage users and access | [Administration](administration.md) |
| Connect buckets/containers, select shared folders, and configure uploads | [Storage connections](storage-connections.md) |
| Author reports and manage embedded dashboards | [Dashboards and Superset](dashboards.md) |
| Understand services, access rules, and data flow | [Architecture](architecture.md) |
| Manage clients, projects, team boards, reports, and work digests | [Projects and delivery](projects-and-delivery.md) |
| Add plugin features to clients and projects | [Context extensions](plugins/project-contexts.md) |
| Configure search, its launcher, or indexing schedules | [Search and indexing jobs](search-and-indexing.md) |
| Set up optional AI, protect credentials, or rotate wrapping keys | [AI search and credential security](ai-search-and-secrets.md) |
| Control PDF viewing, downloads, and watermarks | [Document access](document-access.md) |
| Configure SMTP/app passwords or transactional email services | [Email delivery](email-delivery.md) |
| Enroll an authenticator, verify email MFA, or use recovery codes | [Optional MFA](mfa.md) |
| Send or receive authenticated JSON webhooks | [Webhooks](webhooks.md) |
| Email password-protected, watermarked PDF attachments | [Protected PDF sharing](file-sharing.md) |
| Configure notification methods, messages, and member preferences | [Notifications](notifications.md) |
| Design templates, generate PDFs, and build or follow workflows | [Documents and workflows](documents-and-workflows.md) |
| Enable optional Documents/Workflows and preserve existing data | [Document and workflow plugins](plugins/documents-and-workflows.md) |
| Build plugins spanning React, Kotlin and Python | [Full-stack plugin authoring](plugins/full-stack.md) |
| Inspect extraction formats, limits, and processing states | [Document processing](document-processing-extension.md) |
| Understand tables, MongoDB collections, and backup boundaries | [Database layout](database-layout.md) |
| Deploy, upgrade, verify, or troubleshoot the stack | [Deployment](deployment.md) |
| Work on the frontend/backend or run relevant checks | [Code organization](code-organization.md) |
| Integrate with search, storage, or admin APIs | [API reference](api.md) |
| Provision AWS/Azure networking, VM, HTTPS ingress, and object storage | [Terraform deployment](terraform-deployment.md) · [Modules](../infra/README.md) |

## Current operational defaults

- Storage search is enabled. The launcher preference is on, but the chat button stays hidden until AI answers, content indexing, and a chat model are configured.
- PDF viewing and normal downloads are enabled; admins can select watermark or disabled download mode.
- Outbound email is disabled until configured; SMTP and API provider credentials can come from the environment or encrypted admin settings.
- MFA is optional; the primary admin is prompted at first login. Email enrollment needs a received code and configured delivery; recovery codes work during outages.
- Admin PDF sharing sends an encrypted, watermarked attachment and protected online link; expiry/revocation apply only to the link. SMTP/email and a public origin must be configured first.
- Admin webhooks support manual sends and authenticated JSON receipt with encrypted, bounded delivery history. Optional Telegram, Teams, WhatsApp, SharePoint, and OneDrive connections are separate [integration plugins](plugins/integrations.md).
- Local text matching runs inside the backend without an AI provider or ML container.
- Scheduled indexing is disabled until an admin saves a job. Calendar jobs initially use `00:00`, Friday for weekly jobs, and `Africa/Johannesburg`; each value is editable.
- The initial admin becomes `SUPER_ADMIN`; assignable `ADMIN`, `USER`, and read-only `EXTERNAL` roles have configurable view and storage permissions. See [access controls](access-controls.md).
- Internal members retain shared-root access unless restricted. External members need explicit directory/file grants. Upload permission defaults off and is unavailable to external members.
- AI and storage keys saved through the app require a persistent server wrapping key. MinIO/Azurite development seeds also persist credentials, so set the key before starting those profiles.
- MongoDB records, audit logs, and events are not search or AI-answer sources.
- Plugins start disabled on new databases, including Documents and Workflows. Upgrades retain these modules' existing use when saved documents/designs/runs exist; explicit activation settings are preserved. Source modules and runtime ZIPs support React, Kotlin and Python. Super admins can install ZIPs containing frontend code, isolated backend code, or both after enabling third-party extensions in Security. Backend-only packages need no page. ZIPs require an author file and are excluded from search by default.
- Projects support scoped member/team-lead/manager roles and explicit team access. Departments and tags do not share work automatically. Reports and digests stay manual until schedules are configured; no schedule is created at startup.
- Meshable Ops is the product name. Existing Docker project/volume names and the older plugin SDK alias remain compatible; business branding is preserved.

## Upgrading an existing checkout

Preserve the existing wrapping key, follow the [upgrade procedure](deployment.md#upgrading-an-existing-installation), and reindex once after the local matcher changes to `local-feature-hash-v2`. JSON export v2 is not a complete disaster-recovery backup; preserve database backups, original objects, and wrapping keys separately.

These guides describe checked-in behavior. Unit tests and sample UI checks do not establish that a deployment's cloud credentials, provider models, or scheduled jobs have been verified against live services.
