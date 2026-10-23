# Superset Docker App

A production-oriented Docker Compose application that combines:

- a React frontend
- a Spring Boot backend
- Apache Superset for embedded analytics and admin authoring
- PostgreSQL with pgvector, plus MongoDB, for application data
- a small Python helper for optional document text extraction

The application is designed so users work through the app shell, not through a directly exposed Superset instance. Shared dashboards are embedded inside the product, while Superset authoring is available only to admins through `Superset Graph Builder`.

## What This Repository Provides

- App-managed authentication and role-based access control
- Embedded Superset dashboards through the app domain
- Admin-only Superset authoring at `/admin/superset-graph-builder`
- Card-based admin workspaces for users, storage, AI/search, system health, audit/backup, and dashboard links
- Multiple named S3-compatible buckets or Azure Blob containers with explicit shared-folder selections
- Vector search over folder/document metadata and indexed content, with sourced AI answers
- A bottom-right search/chat launcher with admin controls and indexing schedules; search also works without AI
- Production deployment with Docker Compose and Caddy-based HTTPS

## Product Model

This repository is best understood as:

- a relatively simple CRUD-style internal application
- with embedded analytics layered in through Superset
- with admin operations surfaced through a real app page rather than a separate analytics portal

The admin area is split by responsibility so each page remains focused:

- `/admin`
  overview and shortcuts
- `/admin/users`
  application user and role management
- `/admin/storage`
  S3/Azure Blob scope, credentials, and interval/calendar/change-triggered indexing jobs
- `/admin/ai`
  search/launcher/AI switches and optional provider configuration
- `/admin/system`
  health, public entrypoints, private services, and runtime details
- `/admin/audit`
  audit history and application backup export
- `/admin/dashboard-links`
  app-to-Superset dashboard linking managed through Kotlin
- `/admin/superset-graph-builder`
  embedded Superset authoring workspace for admins

## Architecture Summary

The intended public surface is the frontend application.

- `frontend`
  main user-facing app
- `backend`
  auth, APIs, guest-token minting, admin logic, and vector-search orchestration
- `superset`
  internal analytics dependency, proxied through the frontend
- `ml-backend`
  optional document extraction and legacy vectorization helper; default local search runs inside the backend
- `postgres` with pgvector, `mongodb`
  application data stores
- `redis`
  currently a Superset-specific runtime dependency

Important access rules:

- all authenticated users can view shared document folders
- all authenticated users can download individual documents or folder ZIP archives
- all authenticated users can view allowed embedded dashboards
- only users explicitly granted the upload permission can upload documents or folder trees; it is off by default for USER and ADMIN accounts
- only admins can manage users and upload permissions, settings, backups, reindexing, dashboard embed configs, and Superset authoring routes

Important routing rule:

- frontend route hiding is not the security boundary for Superset authoring
- the frontend proxy is the real enforcement point for non-embed Superset route access

Additional architecture detail:

- [Architecture Guide](docs/architecture.md)
- [Documentation Index](docs/README.md)
- [Code Organization](docs/code-organization.md)
- [AI Search and Credential Security](docs/ai-search-and-secrets.md)
- [Search Controls and Indexing Jobs](docs/search-and-indexing.md)
- [Database Layout](docs/database-layout.md)
- [Deployment Guide](docs/deployment.md)
- [API Reference](docs/api.md)

## Service Map

| Service | Default access | Purpose |
|---|---|---|
| Frontend | `http://localhost:3000` | Main application UI |
| Backend API | internal only | Application APIs, reached through the frontend proxy |
| Superset | internal only | Embedded dashboards and admin authoring |
| ML backend | internal only | Optional text/PDF/DOCX extraction; legacy vectorization endpoint |
| PostgreSQL + pgvector | internal only | Application relational data, Superset metadata, and vector search storage |
| MongoDB | internal only | Events and audit-style documents |
| Redis | internal only | Superset cache and task coordination |
| Caddy | `https://your-domain` | Production HTTPS entrypoint |

## Quick Start

First-time development setup (also safe to rerun after code changes):

Before starting, copy `.env.example` to the untracked `.env` and set
`APP_SECRETS_ENCRYPTION_KEY` once. Generate a 32-byte key with `openssl rand -base64 32`
and retain it securely across restarts. It is required to save provider credentials or
upgrade a database containing existing storage keys. See the [setup guide](docs/ai-search-and-secrets.md).

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d --remove-orphans
```

That command uses the local S3-compatible MinIO provider. To run the same development
stack against Azure Blob-compatible Azurite instead, use:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.azurite.yml up --build -d --remove-orphans
```

Use one development override at a time. Both profiles provide development storage seed
values. Persisted settings take precedence unless `APP_SEED_STORAGE_FORCE=true` is explicitly
set for that startup; the overrides leave it disabled by default.

The development override starts a local S3-compatible MinIO instance and creates the
`starter-dev` bucket automatically. Its S3 API is at `http://localhost:9000` and its
browser console is at `http://localhost:9001`.

Development object-storage settings:

- provider: `S3`
- bucket: `starter-dev`
- region: `us-east-1`
- backend/container endpoint: `http://minio:9000`
- host endpoint: `http://localhost:9000`
- access key: `minioadmin`
- secret key: `minioadmin123`

Override the credentials or bucket with `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, and
`MINIO_BUCKET`. These defaults are development-only.

Azurite exposes Blob, Queue, and Table endpoints at ports `10000`, `10001`, and `10002`.
The backend uses the Blob endpoint `http://azurite:10000/devstoreaccount1` and container
`starter-dev`; override the container with `AZURITE_CONTAINER`. Its well-known development
account and key are for local testing only.

### Shared cloud locations

The existing `ADMIN` role acts as super admin for storage configuration. From
**Administration > Storage connections > Shared locations**, admins can:

1. Add independent S3 buckets or Azure Blob containers, each with an in-app nickname.
2. Set an optional start-folder boundary and test the connection.
3. Browse the provider's folders and select the folders to expose, assigning each a nickname.
4. Save a folder's **Accessible** setting or disable a whole connection to revoke shared access.

Storage displays each enabled connection as a named folder containing its accessible selections.
Selections include all descendants. Parent/child selections within a connection cannot overlap.
Saving a new connection does not share any folders automatically. Regular authenticated users
see only shared locations; credentials and admin folder discovery remain restricted to `ADMIN`.
Hidden selections are blocked for browsing, downloads, uploads, and vector search. Uploads still
require the separate upload permission. Removing a selection removes its application metadata,
not the objects in cloud storage. Existing application-managed folders retain their visibility
and can be renamed or hidden in the same admin workspace.

Connection nicknames, selected-folder nicknames, and visibility are stored in PostgreSQL.
Nicknames do not rename buckets, containers, or cloud object keys. The backend schema update
adds connection references and defaults existing folders to accessible; prefix uniqueness is
scoped per connection so matching folder paths can be shared from different buckets.

### Default storage view path

The default connection exposes registered, application-managed storage roots beneath a
configured prefix such as `contract-management/documents`. Named connections expose only
the folders explicitly selected by an admin, within their optional start-folder boundary.
Configure this persistently from **Administration > Storage connections > Default connection > Storage view path**.
The value is stored in PostgreSQL. Do not use `/`, `.` or `..` as path segments.

For either local provider, set the prefix before startup:

```bash
APP_STORAGE_ROOT_PATH=contract-management/documents docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

PowerShell:

```powershell
$env:APP_STORAGE_ROOT_PATH = "contract-management/documents"
docker compose -f docker-compose.yml -f docker-compose.dev.azurite.yml up --build -d
```

For a deployed S3 or Azure Blob service, set these runtime variables in `.env.production`:

```dotenv
APP_SEED_STORAGE_PROVIDER=S3
APP_SEED_STORAGE_BUCKET=my-bucket
APP_SEED_STORAGE_REGION=us-east-1
APP_SEED_STORAGE_ENDPOINT=https://s3.example.com
APP_SEED_STORAGE_ACCESS_KEY=...
APP_SEED_STORAGE_SECRET_KEY=...
APP_SEED_STORAGE_ROOT_PATH=contract-management/documents
```

For Azure Blob, use `AZURE_BLOB`, the container as the bucket value, the Blob service
endpoint (for example `https://account.blob.core.windows.net`), and the storage account
name/key as access/secret values. Startup values seed PostgreSQL only when settings are
unconfigured. Set `APP_SEED_STORAGE_FORCE=true` to intentionally replace persisted settings.
Keep credentials as runtime secrets; do not bake them into the backend image.

The development overrides also leave `APP_SEED_STORAGE_FORCE` disabled by default, so an
administrator's saved provider and storage view path survive container restarts. When switching
an existing development database between MinIO and Azurite, run that one startup with
`APP_SEED_STORAGE_FORCE=true`, then remove or unset it again.

Changing the storage view path repoints application roots into the new namespace on the
next listing/reindex. It does not copy existing objects from the old prefix.

The connection screen shows fields for the selected provider and supports either explicit
keys or the identity configured on the application server. Use **Test connection** before
saving to check read access against the draft bucket/container and prefix. This check does
not save settings, create a container, or upload objects; write permissions must be granted
separately for uploads. Automatic vector recalculation is configured on the **Search index** tab.

Useful checks:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f backend frontend
```

Stop the development stack without deleting its data volumes:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
```

Open:

- `http://localhost:3000`
- `http://localhost:3000/dashboards`
- `http://localhost:3000/admin`
- `http://localhost:3000/admin/system`
- `http://localhost:3000/admin/superset-graph-builder`

Default local accounts:

- Admin: `admin@app.local` / `admin123`
- User: `user@app.local` / `user123`

These defaults are for local development only.

The **Storage** page stores searchable root metadata in PostgreSQL and creates a matching
`{configured-storage-view-path}/{folder-id}/` prefix in MinIO/S3 or Azure Blob/Azurite.
Opening a root explores
its child virtual folders and objects directly from the configured provider. Root metadata and
each discovered document's filename/path/content type are vectorized separately in pgvector.
Content extraction for supported text, PDF, and DOCX files can be enabled from **Administration > AI & search**.
Chat and embedding providers can use environment settings or encrypted admin settings. OCR and image classification remain extension points. See
[Document Folder and Processing Extension](docs/document-processing-extension.md).

Every authenticated account can download documents and folder ZIPs from accessible selections. Uploading
files or a folder tree requires the independent **Allow document and folder uploads** permission.
Admins grant or revoke it from **Administration > Users & access**; it defaults to disabled even for administrators.
Upload paths are constrained to the selected storage root and uploaded
document metadata is synchronized and vectorized immediately.

Default transfer limits are 50 MB per file and 250 MB per request. Override them at backend
startup when needed:

```dotenv
APP_UPLOAD_MAX_FILE_SIZE=100MB
APP_UPLOAD_MAX_REQUEST_SIZE=500MB
```

The frontend nginx request limit is currently `250m`; update that proxy limit as well when
increasing the backend request size. Upload limits are separate from the 5 MiB content-indexing limit.

### Superset Login

The default Superset authoring account is separate from the application accounts:

- username: `admin`
- password: `admin`

These defaults apply only when the corresponding variables are absent. If the root
`.env` already defines `SUPERSET_ADMIN_USER` or `SUPERSET_ADMIN_PASSWORD`, log in with
those values instead.

Sign into the application as its admin first, then open
`http://localhost:3000/admin/superset-graph-builder`. If Superset presents its own
login screen, use the credentials above.

Override the Superset administrator by adding these values to the root `.env` file
before running the development setup command:

```dotenv
SUPERSET_ADMIN_USER=admin
SUPERSET_ADMIN_PASSWORD=choose-a-different-password
SUPERSET_ADMIN_EMAIL=admin@example.com
SUPERSET_ADMIN_FIRSTNAME=Admin
SUPERSET_ADMIN_LASTNAME=User
```

`SUPERSET_ADMIN_USER` and `SUPERSET_ADMIN_PASSWORD` are also used by the backend for
Superset API access, so keep one set of values for the whole Compose stack. On startup,
the Superset initialization job creates the selected user if necessary and refreshes
that user's password when it already exists. Changing the username creates an additional
Superset administrator; it does not delete an existing administrator.

## Core Workflows

### Shared Dashboards

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

### User Access Management

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

### Search, Optional AI, and Indexing Jobs

Use the bottom-right **Search & AI** button. The left navigation has no search submenu.
The dedicated `/search` page remains available when search is enabled. Ordinary search uses
the backend's deterministic `local-feature-hash-v2` matcher by default: no model, AI key,
or running ML helper is needed. Fresh PostgreSQL volume initialization can create sample
folder metadata; it does not populate original document content. Reindex metadata to test
local matching, and add/share actual documents before testing sourced answers.

**Administration > AI & search** independently controls search, launcher visibility,
AI answers, and content indexing. Turning search off also rejects search/chat API requests.
Hiding just the launcher keeps `/search` and the API available. AI answers are off by default.
To enable them, configure chat and optional external embeddings, enable content indexing,
and reindex storage. Chat and embeddings can use different providers. Saved admin keys are
encrypted; environment settings and admin settings are separate configuration sources.

If external embeddings are unavailable, search falls back to access-checked keyword matches.
Missing extraction keeps metadata indexing available; unavailable chat returns guidance rather
than an invented answer. MongoDB, events, and audit logs are excluded from search.

Configure jobs under **Administration > Storage connections > Search index**:
daily, weekly, fixed interval, or the next selected time after file changes are detected.
For example, Friday `00:00` or the next midnight after a change. Select the timezone explicitly;
the initial value is `Africa/Johannesburg`. Pending work and the next run persist in PostgreSQL.
The backend must be running to detect changes and execute jobs; one backend instance is assumed.

See [search controls and jobs](docs/search-and-indexing.md),
[provider setup and credential security](docs/ai-search-and-secrets.md), and
[document processing limits](docs/document-processing-extension.md).

## Security Model

The repository is configured so embedded analytics work through the application domain instead of through a separately exposed Superset site.

- Superset is not intended to be publicly exposed
- embedded dashboards load through `/embedded/...`
- guest tokens are minted by the backend
- the backend syncs allowed app origins into Superset embedded allowlists
- admin-only Superset routes are proxied through the frontend and protected by app auth
- application passwords use salted bcrypt at cost 12, and provider credentials are encrypted at rest
- AI and storage settings never return saved keys to the browser; application backups omit credentials

This keeps the user-facing analytics experience inside the application while preserving a separate admin authoring path.

## Production Readiness Notes

### Production Bootstrap Users

Production access should be explicit and environment-driven.

- local development can use convenience seed users
- production should use `APP_BOOTSTRAP_USERS_JSON`
- if the `prod` profile is active and `APP_BOOTSTRAP_USERS_JSON` is empty, convenience fallback users are not created

That rule applies to the Spring runner. First-volume PostgreSQL initialization separately
seeds `admin@app.local`; review and replace/deactivate development accounts before production
exposure. See [production bootstrap behavior](docs/deployment.md#user-bootstrap-behavior).

### Secrets and Configuration

Review and replace at minimum:

- `JWT_SECRET`
- `APP_SECRETS_ENCRYPTION_KEY` (retain the same wrapping key across restarts and with database backups)
- `SUPERSET_SECRET_KEY`
- `GUEST_TOKEN_JWT_SECRET`
- `SUPERSET_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_USERS_JSON`
- database passwords

Primary config files:

- [docker-compose.yml](docker-compose.yml)
- [docker-compose.prod.yml](docker-compose.prod.yml)
- [.env.production.example](.env.production.example)
- [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties)
- [superset/superset_config.py](superset/superset_config.py)

### Deployment Shapes

This repository supports three practical production shapes:

- full all-in-one VM
  one VM runs the app, Superset, its supporting Redis, and the data services
- app host plus offloaded services
  the VM mainly runs the app and analytics surfaces while core data services move to managed infrastructure
- existing host-level reverse proxy
  the stack sits behind a host-managed Caddy or Nginx layer

### Redis Positioning

Redis is currently part of the Superset runtime footprint, not a general app dependency.

- the backend does not depend on Redis directly today
- keeping Redis on the same VM as Superset is a sensible default
- if the broader application later needs Redis, that should be treated as a separate stack concern

### VM Sizing Guidance

Sizing depends on what stays on the VM.

- app host with offloaded stateful services
  often reasonable around `2-4 vCPU` and `4-8 GB RAM`
- full all-in-one stack for a very small team
  `2 vCPU / 4 GB RAM` can be a lean starting point
- full all-in-one stack with more buffer
  `4 vCPU / 8-16 GB RAM` is a safer comfort range

These are planning ranges, not benchmark guarantees. Real usage depends on dashboard concurrency, Superset query mix, vector-search frequency, and background activity.

## Production Deployment

The recommended default is:

1. one Linux VM
2. Docker Engine and Docker Compose
3. private Docker networking for internal services
4. in-stack Caddy for HTTPS
5. only `80/443` exposed publicly

Normal public/private posture:

- public:
  `80`, `443`
- private or tightly restricted:
  `22`
- not publicly exposed:
  `3000`, `8080`, `5432`, `27017`, `6379`

High-level rollout:

1. Copy `.env.production.example` to `.env.production`.
2. Replace all placeholder secrets.
3. Set `APP_DOMAIN`, `ACME_EMAIL`, and `APP_BOOTSTRAP_USERS_JSON`.
4. Point DNS at the VM.
5. Make sure `80/443` reach the VM and are not already in use.
6. Start the production stack:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

7. Verify login, `/admin`, and `/admin/superset-graph-builder` on the real domain.

For the full deployment guide, including reverse-proxy options, troubleshooting, and verification:

- [docs/deployment.md](docs/deployment.md)

## Reverse Proxy and HTTPS

The built-in production path uses:

- [docker-compose.prod.yml](docker-compose.prod.yml)
- [deploy/caddy/Caddyfile](deploy/caddy/Caddyfile)

This gives you:

- Caddy inside the Compose stack
- automatic certificate management
- TLS termination at Caddy
- proxying from Caddy to the internal frontend service

Use the in-stack Caddy path when you want the simplest production model.

Alternative examples are also included:

- host-level Caddy:
  [deploy/Caddyfile.example](deploy/Caddyfile.example)
- host-level Nginx:
  [deploy/nginx/app.example.conf](deploy/nginx/app.example.conf)

## Operations

Common commands:

```bash
docker compose ps
docker compose logs -f frontend
docker compose logs -f backend
docker compose logs -f superset
docker compose restart frontend backend superset
docker compose down
```

Rebuild after code or config changes:

```bash
docker compose up --build -d
```

Reindex stored vectors:

```bash
./scripts/reindex-items.sh
```

On Windows PowerShell:

```powershell
./scripts/reindex-items.ps1
```

Both reindex helpers:

- log in as an app admin
- call the existing `/api/items/reindex` endpoint
- support `APP_URL`, `APP_ADMIN_EMAIL`, and `APP_ADMIN_PASSWORD`

## Repository Documentation

Start with the [documentation index](docs/README.md) for operator, developer, and API guides.

- [Terraform deployment examples](infra/README.md) for AWS (EC2/S3) and Azure (VM/Blob)

- [docs/architecture.md](docs/architecture.md)
  runtime services, request flow, role boundaries, and proxy behavior
- [docs/database-layout.md](docs/database-layout.md)
  data stores, entity layout, persistence boundaries, and backup thinking
- [docs/deployment.md](docs/deployment.md)
  production shapes, reverse proxy options, rollout steps, sizing, and troubleshooting

## Notes

- Shared dashboard visibility is role-based inside the app.
- The frontend is the supported way to reach Superset admin pages.
- Superset embed allowlists are updated at runtime as the app requests guest tokens.
- Redis is currently reserved for Superset runtime behavior.
