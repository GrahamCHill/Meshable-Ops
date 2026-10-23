# Repository documentation

The implementation uses PostgreSQL/pgvector for storage metadata, content chunks, search, and configuration; S3/Azure Blob retains original files. MongoDB continues to store events and audit history. MongoDB search and OCR integration are deferred.

## Start here

| Task | Guide |
| --- | --- |
| Start the development stack, connect storage, or manage dashboards | [Project README](../README.md) |
| Understand services, access rules, and data flow | [Architecture](architecture.md) |
| Configure search, its launcher, or indexing schedules | [Search and indexing jobs](search-and-indexing.md) |
| Set up optional AI, protect credentials, or rotate wrapping keys | [AI search and credential security](ai-search-and-secrets.md) |
| Inspect extraction formats, limits, and processing states | [Document processing](document-processing-extension.md) |
| Understand tables, MongoDB collections, and backup boundaries | [Database layout](database-layout.md) |
| Deploy, upgrade, verify, or troubleshoot the stack | [Deployment](deployment.md) |
| Work on the frontend/backend or run relevant checks | [Code organization](code-organization.md) |
| Integrate with search, storage, or admin APIs | [API reference](api.md) |
| Provision an AWS or Azure VM and object storage | [Terraform examples](../infra/README.md) |

## Current operational defaults

- Search and its bottom-right launcher are enabled; AI answers and content indexing are disabled.
- Local text matching runs inside the backend without an AI provider or ML container.
- Scheduled indexing is disabled until an admin saves a job. Calendar jobs initially use `00:00`, Friday for weekly jobs, and `Africa/Johannesburg`; each value is editable.
- `ADMIN` is the existing super-admin role for connection, folder-access, feature, and job settings. There is no separate `SUPER_ADMIN` role.
- Explicitly shared folders are available to authenticated users. Upload permission is independent of role and defaults off.
- AI and storage keys saved through the app require a persistent server wrapping key. MinIO/Azurite development seeds also persist credentials, so set the key before starting those profiles.
- MongoDB records, audit logs, and events are not search or AI-answer sources.

## Upgrading an existing checkout

Preserve the existing wrapping key, follow the [upgrade procedure](deployment.md#upgrading-an-existing-installation), and reindex once after the local matcher changes to `local-feature-hash-v2`. JSON export v2 is not a complete disaster-recovery backup; preserve database backups, original objects, and wrapping keys separately.

These guides describe checked-in behavior. Unit tests and sample UI checks do not establish that a deployment's cloud credentials, provider models, or scheduled jobs have been verified against live services.
