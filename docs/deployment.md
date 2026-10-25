# Meshable Ops deployment guide

The [community license](../LICENSE) permits hosting on AWS, GCP, Azure and other providers, including paid hosting, installation, deployment management and maintenance services. Easy or one-click installers may build/package unchanged code and make configuration, script, manifest and infrastructure changes reasonably necessary to deploy it. Other source changes must independently follow the community modification, same-license source-sharing and upstream grant-back rules. Covered network deployments offer all users complete source, including covered plugins and features. Application paywalls, proprietary/source-sharing exceptions and branding beyond exposed settings require an express written agreement. See [licensing examples](../LICENSING.md).

Uploaded Python/Kotlin backend plugins use the private `plugin-runner` service and fixed `plugin-python-worker`/`plugin-kotlin-worker` images. Fresh setup generates `APP_PLUGIN_RUNNER_TOKEN`; preserve this private value and the existing PostgreSQL/wrapping keys during upgrades. Existing installations can add the token to enable server ZIPs; omitting it leaves core and frontend-only plugins available. The trusted runner mounts the Docker socket and must never expose its port publicly. See [setup, isolation and operational limits](plugins/backend-runtime.md#deployment-and-isolation).

For independent template copies deploying automatically to an existing Linux VM, follow [GitHub Actions deployment](github-deployment.md). It includes fresh-instance credentials, an initializer without development users, verified SSH, and persisted release/data directories.

For a new cloud installation, use the [AWS, Azure or GCP Terraform walkthrough](terraform-deployment.md).
The modules provision managed PostgreSQL and separate application, private analytics/history, and private Python/plugin runtime hosts, with restricted ingress and explicit outbound connectivity.
AWS/Azure also provision private object storage accessed by the app's cloud identity; GCP uses a separately configured S3/Blob connection.
See [`infra/`](../infra/README.md), [environment file locations](deployment-environment.md), or the [manual Linux VM walkthrough](linux-vm-deployment.md).

This guide explains the practical deployment shapes supported by this repository and how the built-in reverse-proxy setup is intended to be used.

Compose containers and volumes use project-scoped names. For a clean installation alongside stopped older resources, choose a new project name and fresh private settings; preserve the old volumes. Keep the same project name and keys when upgrading. Concurrent installations require distinct published ports and separate deployment directories. See [development setup](getting-started.md#operations).

Installed ZIP plugins and their artwork, configuration, access choices, and records persist in PostgreSQL across container updates. Preserve the same database and wrapping key; compatibility is re-evaluated against the packaged app release and missing/mismatched version declarations produce advisory warnings. See [plugin compatibility and backups](plugins/compatibility-and-updates.md).

## Deployment Shapes

The same application can be deployed in a few different ways.

### Full All-In-One VM

One Linux VM runs:

- Caddy
- frontend
- backend
- Superset
- PostgreSQL
- MongoDB
- pgvector inside PostgreSQL
- Redis for Superset
- ML helper
- WeasyCanvas PDF renderer

This is the simplest operational model because everything ships as one Compose-managed stack. It is also the heaviest single-host shape.

Use this when:

- you want the fastest path to a working deployment
- the environment is small
- you are comfortable running stateful services on the same VM

### App Host Plus Offloaded Stateful Services

The VM mainly runs:

- Caddy or another reverse proxy
- frontend
- backend
- Superset
- optionally the ML helper
- the WeasyCanvas renderer when generating documents

Stateful dependencies can be moved out to managed services:

- PostgreSQL
- MongoDB
- pgvector-capable PostgreSQL
- object storage

The current Redis instance is intentionally not listed there as a core app dependency because today it is only supporting Superset. If a broader app-level Redis stack is introduced later, that should be documented as a separate concern.

Use this when:

- you want a smaller app VM
- backups, storage, and service durability should be handled elsewhere
- documents, workflows, and analytics should share an app host while data services have separate operational ownership

### Existing Host-Level Reverse Proxy

The app stack can also sit behind a host-managed Caddy or Nginx setup.

Use this when:

- your VM already has a standardized reverse proxy
- certificate management is already handled on the host
- you want Compose to manage only the application services

## Recommended Default

For most straightforward production deployments, the intended default is:

1. one Linux VM
2. Docker Engine and Docker Compose
3. the application stack on a private Docker network
4. in-stack Caddy from `docker-compose.prod.yml`
5. only `80/443` publicly reachable

That keeps the browser path simple:

- browser -> caddy -> frontend
- frontend -> backend internally
- frontend -> superset internally
- backend -> postgres/mongo/ml internally
- backend -> private WeasyCanvas renderer for document generation
- backend -> selected object-storage prefixes; optional configured chat/embedding APIs
- frontend/superset runtime -> superset/redis internally

## Reverse Proxy Options

### In-Stack Caddy

The main production path in this repository uses:

- [docker-compose.prod.yml](../docker-compose.prod.yml)
- [deploy/caddy/Caddyfile](../deploy/caddy/Caddyfile)

In this mode:

- Caddy runs as a Compose service
- Caddy binds `80` and `443` on the VM
- Caddy obtains and renews certificates
- Caddy reverse-proxies to the internal `frontend` container
- the frontend then proxies API, embed, and admin-authoring traffic to the correct internal services

Use this when you want the least moving parts.

### Host-Level Caddy

The host-level example is:

- [deploy/Caddyfile.example](../deploy/Caddyfile.example)

In this mode:

- Caddy runs on the VM host, not in Compose
- the app is already reachable on `127.0.0.1:3000`
- the host-level Caddy process terminates TLS and forwards traffic into the app

Use this when you want host-managed proxying but still prefer Caddy.

### Host-Level Nginx

The host-level Nginx example is:

- [deploy/nginx/app.example.conf](../deploy/nginx/app.example.conf)

Use this when Nginx is already the platform standard.

## Caddy Prerequisites

The app and supplied proxies send crawler exclusion headers, and the frontend serves a wildcard-disallow robots file. Retain these settings when using another reverse proxy. After deploying, run the [crawler policy smoke check](crawler-exclusion.md#deploy-and-verify) against the public origin.

Before bringing up the in-stack Caddy path:

- set `APP_DOMAIN` to the hostname only
- set `ACME_EMAIL` to a valid email address
- point DNS for that hostname at the VM
- make sure inbound `80` and `443` reach the VM
- make sure no other host service is already binding `80` or `443`

Examples:

- good `APP_DOMAIN`: `app.example.com`
- bad `APP_DOMAIN`: `https://app.example.com`
- bad `APP_DOMAIN`: `app.example.com/superset`

## Production Environment Variables

The production override expects `.env.production` to provide at least:

- `APP_DOMAIN`
- `ACME_EMAIL`
- `JWT_SECRET`
- `POSTGRES_PASSWORD` and `MONGO_PASSWORD`
- `APP_SECRETS_ENCRYPTION_KEY` before storing API/storage credentials or upgrading rows that already contain them
- `SUPERSET_SECRET_KEY`
- `GUEST_TOKEN_JWT_SECRET`
- `SUPERSET_ADMIN_PASSWORD`
- `APP_DOCUMENT_RENDER_TOKEN` and `APP_PLUGIN_RUNNER_TOKEN`
- `APP_BOOTSTRAP_USERS_JSON`

If you offload services, also override any relevant connection settings for:

- PostgreSQL
- MongoDB
- pgvector data as part of PostgreSQL
- object storage

For the current stack, Redis usually stays grouped with Superset unless you are deliberately externalizing Superset's own runtime dependencies too.

Generate private settings with `python deploy/github/setup-instance.py --domain app.example.com --admin-email owner@example.com --acme-email ops@example.com --output .env.production`, or fill the blank [.env.production.example](../.env.production.example) manually. Retain each secret across restarts. `APP_SECRETS_ENCRYPTION_KEY` is a base64-encoded 32-byte wrapping key, separate from JWT/Superset secrets. Generate it once with `openssl rand -base64 32` (or the [PowerShell alternative](ai-search-and-secrets.md#start-with-search-only)); protect a separate recovery copy. Do not replace it during an ordinary rebuild. Workload-identity-only storage with no stored keys does not need it for local search, but adding admin-saved credentials later does.

AI is optional. Defaults are `APP_SEARCH_ENABLED=true`, `APP_SEARCH_LAUNCHER_ENABLED=true`, `AI_ENABLED=false`, `AI_INDEX_CONTENT=false`, and `AI_EMBEDDING_PROVIDER=LOCAL`. The matcher runs inside Kotlin; the ML helper is required only for the implemented document text-extraction flow. When the AI configuration source is Admin settings, changing environment AI variables does not override that saved source. See [AI and credential setup](ai-search-and-secrets.md).

The base Compose file requires explicit database, JWT, and Superset secrets. Generate a private environment with the [setup helper](initialization-and-security.md); blank example files cannot start the stack. Existing database passwords and wrapping keys must be retained or rotated deliberately. Keep private database/network boundaries intact.

## User Bootstrap Behavior

All profiles start without implicit accounts or sample content. `APP_BOOTSTRAP_USERS_JSON` creates only explicitly configured missing accounts; existing passwords, roles, and deactivation state are preserved. The [PostgreSQL initializer](../docker/postgres/init.sql) creates only the database/extensions. Existing seeded records are not deleted automatically; review/deactivate unwanted identities through Administration. See [initialization and security](initialization-and-security.md).

## Recommended Rollout Steps

These are single-host Compose steps. Use the complete [manual Linux walkthrough](linux-vm-deployment.md), [GitHub release guide](github-deployment.md), or [split Terraform deployment](terraform-deployment.md) for the matching layout and exact credential locations.

1. Provision the VM.
2. Install Docker Engine and the Docker Compose plugin.
3. Clone the repository.
4. Run `deploy/github/setup-instance.py` with your hostname/admin/ACME email and `--output .env.production` to generate private fresh-instance settings.
5. Preserve the generated secrets and persistent wrapping key; review database credentials and initial accounts as described above.
6. Set `APP_DOMAIN`, `ACME_EMAIL`, and `APP_BOOTSTRAP_USERS_JSON`.
7. Confirm nothing else is binding `80/443`.
8. Point DNS at the VM.
9. Open `80/443` in the cloud firewall and VM firewall.
10. Start the stack:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

11. If TLS issuance stalls, inspect Caddy:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml logs -f caddy
```

12. Verify login, `/admin`, and `/admin/superset-graph-builder` on the real domain. The production override sets Secure auth cookies, so use HTTPS.
13. Configure named storage connections/accessible folders, feature controls, and optional providers. Reindex after choosing the embedding model or enabling content indexing.
14. Save an indexing job under **Administration > Storage connections > Search index** and check its timezone and next run. Keep the backend running; the in-app scheduler assumes one backend instance.

## Upgrading an existing installation

1. Back up the PostgreSQL databases, MongoDB history, and original object storage as appropriate. The JSON admin export omits credentials/hashes, chunks/vectors, and some account/runtime state; it is not a full recovery backup.
2. Preserve the current wrapping key. For an older installation with plaintext storage credentials and no key yet, configure a newly generated key before the first upgraded startup. For encrypted rows, use the matching current key or follow the explicit [rotation procedure](ai-search-and-secrets.md#stored-credentials-and-passwords).
3. Review the selected environment/admin AI source and the current search/launcher/answer switches. No AI provider is required to upgrade or use the local matcher.
4. Rebuild with the same Compose files and env file used by the deployment. Do not delete its volumes to apply these changes.
5. Inspect backend startup. Hibernate currently updates JPA tables, supplemental SQL creates/updates pgvector objects, then stored credentials are migrated before account/storage bootstrap and jobs. `EmbeddingDimensionMigration` upgrades legacy vector columns to 1536 dimensions and rebuilds local plugin vectors; document metadata and extracted text are retained. Missing/mismatched wrapping keys cause startup to fail closed.
6. Run **Reindex now** once for the 1536-dimensional index (`local-feature-hash-v3` or an external model returning 1536 values), or after any embedding/content setting change. Queries exclude incompatible vector identities; metadata/content keyword fallback keeps ordinary search available while the index awaits rebuilding.
7. Verify folder visibility/revocation, source links, dashboard selection, admin controls, and the job's saved next run. A persisted overdue job runs once after startup; repeated file detections do not move an already queued deadline.

Do not infer a successful database restore from a successful metadata export. Recovery needs the actual databases, original objects, and matching wrapping keys. Current schema updates are startup-driven rather than versioned migration files; take recovery backups before upgrading.

## Public and Private Ports

Normal production posture:

- public:
  `80`, `443`
- private or tightly restricted:
  `22`
- not publicly exposed:
  `3000`, `8080`, `5432`, `27017`, `6379`

The production override already removes direct host publishing for backend and data services. Keep it that way unless you have a specific secured admin-access reason.

For development, layer `docker-compose.dev.yml` over the base Compose file. It adds a
local MinIO S3 API on port `9000`, its console on `9001`, and an initialization job
that creates the configured development bucket. The production override does not
include this service.

## Sizing Guidance

Sizing depends on whether the VM is carrying only app logic or the full supporting stack.

- app host with stateful services offloaded:
  often reasonable around `2-4 vCPU` and `4-8 GB RAM`
- full all-in-one stack for a very small team:
  `2 vCPU / 4 GB RAM` can be a lean starting point
- full all-in-one stack with more buffer:
  `4 vCPU / 8-16 GB RAM` is a safer comfort range

Because Redis is currently only there for Superset, it normally makes sense to keep it on the VM beside Superset rather than treating it as a separate externalization requirement by default.

These are planning ranges, not benchmark guarantees. Real usage depends on dashboard concurrency, Superset query mix, vector-search frequency, PDF rendering, and background task load. The Compose renderer has a 1536 MiB memory limit and up to two bounded render workers; allow room for it when sizing a host that generates documents.

## Verification Checklist

After deployment, verify:

- only `80/443` are publicly reachable
- login works on the real hostname
- `/dashboards` loads shared embeds
- `/admin` loads for admins
- `/admin/superset-graph-builder` loads for admins
- non-admin users cannot access direct Superset authoring pages
- health checks respond as expected
- named storage connections expose only selected accessible folders, and disabled selections deny browse/download/search
- the search launcher can be hidden independently, and disabling search also rejects its API requests
- ordinary search works with AI answers off; a configured provider failure gives keyword fallback or answer guidance
- content indexing/extraction works for a small supported file when enabled; an unavailable extractor retains metadata search
- saved AI/storage settings return configured flags without keys, and credential fields clear after successful saves
- the indexing job has the intended local day/time/timezone, pending state, and next run
- a test scheduled run completes against the deployment's actual storage/provider services
- seeded/development accounts have been reviewed and database secrets match the production configuration

## MFA, webhook, and PDF sharing rollout

Rebuild the backend/frontend after upgrading. Hibernate creates the MFA, webhook, sharing, and rate-limit tables plus user security-version fields. Preserve the wrapping key: authenticator setup secrets, webhook secrets, and retained webhook payloads join the existing startup key rotation. The JSON export omits these tables; retain proper PostgreSQL backups. MongoDB needs no changes.

Set `APP_MFA_PRIMARY_ADMIN_EMAIL` to the primary administrator's saved email (when unset, the oldest active admin is selected). Its first password login prompts optional MFA setup; deploy synchronized server time for authenticator codes. Configure and test email before email enrollment or sharing; enrollment requires receipt of an actual code. Preserve recovery codes before relying on email MFA. Admin delivery changes cannot disable a configuration used as an account's sole factor, but external environment changes/provider outages still need recovery or restored delivery.

Set `APP_PUBLIC_BASE_URL=https://your-app-host` for share links and complete webhook receiver URLs. Sharing can override its origin in Admin → File sharing. Keep the default `APP_WEBHOOK_ALLOW_PRIVATE_TARGETS=false`; only opt in for deliberately trusted internal receivers. Outgoing public webhooks require HTTPS and have DNS/network restrictions. Allow backend egress only to configured services. Incoming HTTPS uses the existing `/api` proxy route. Share links use the SPA `/shared/:token` route.

Email sharing sends a mandatory watermarked, password-encrypted PDF attachment, up to 10 MiB after protection, plus a protected online link. Communicate its password separately. Confirm the configured provider accepts encrypted PDF attachments. Online expiry, revocation, and policy changes cannot recall attachments already sent. Verify the complete flow in staging with a recipient you control; local test transports do not establish inbox delivery. See [MFA](mfa.md), [webhook protocol](webhooks.md), and [protected sharing](file-sharing.md).

## Troubleshooting

### WeasyCanvas documents and workflow diagrams

Rebuild the frontend/backend and build the new `weasy-renderer` service with the normal Compose deployment. The repository contains its source snapshot and Python dependencies; a neighbouring WeasyCanvas checkout is not required. Its container installs Pango/fonts, runs as non-root, and has no published host port. Backend `APP_DOCUMENT_RENDER_URL` defaults to the internal service URL; native development may use `http://127.0.0.1:8720`. Set a shared `APP_DOCUMENT_RENDER_TOKEN` on backend/renderer and keep the renderer on a trusted private network. The Compose stack forwards both settings.

Templates, generated PDF bytes/snapshots, and workflow definitions use new PostgreSQL tables. Back up the database; JSON export does not include these records. No MongoDB changes, AI provider, or ML extractor are required. An absent renderer affects template validation/generation with a safe 503, while ordinary app features remain available. After deploying, create a template with your own sample content, publish it, generate a sample PDF, and verify plain/protected attachments with a recipient you control. Health alone does not confirm native rendering/font behavior.

The workflow builder saves drafts/validated diagrams and attachment links. Ready diagrams do not run automatically; email sends are explicit. [The feature guide](documents-and-workflows.md) describes its current limits, security, and expansion points. Native renderer setup/tests are in [its README](../weasy-renderer/README.md).

### Certificates do not issue

Check:

- DNS really points at the VM
- ports `80/443` are reachable from the internet
- another reverse proxy is not already binding those ports
- Caddy logs for ACME failures

### Admin pages load but Superset authoring does not

Check:

- the user really has `ADMIN`
- the frontend proxy is running the current config
- `/api/auth/superset-admin-access` returns success for the admin session
- Superset is healthy inside the Compose network

### Non-admins can still hit authoring routes

Check:

- the frontend nginx proxy config is current
- the `auth_request` guard routes are present
- the app was rebuilt/restarted after proxy config changes

### Stack runs, but the VM feels small

Look first at:

- RAM pressure
- Superset authoring/query concurrency
- vector reindex activity
- whether stateful services should be offloaded instead of kept on the same host

### Backend cannot unlock credentials

Restore the matching `APP_SECRETS_ENCRYPTION_KEY`; do not generate a replacement over an existing encrypted database. For a deliberate rotation supply the old key as `APP_SECRETS_PREVIOUS_ENCRYPTION_KEY` for the migration, then remove the previous-key setting after success. MinIO/Azurite seeds persist development credentials too, so those profiles also need a wrapping key on first startup.

### Search button is absent or AI answers are unavailable

Check **App Intelligence** and the selected configuration source. The floating assistant requires search, launcher visibility, and permission to use search. It opens with search and setup guidance while AI answers, content indexing, or the chat model are not configured. Ordinary Storage search remains available with AI off; switching search off hides its fields and the launcher. Answers need accessible indexed excerpts. Status reports configuration, not live provider health; use the saved-connection tests. External embeddings must return 1536 dimensions. Basic local matching needs neither an API key nor the ML container; failed extraction keeps metadata search. MongoDB records are intentionally not included.

### Indexing job does not run at the expected time

Check that scheduled indexing was saved/enabled, inspect the saved IANA timezone and next due instant, and ensure the backend is running. Checks occur about once per minute after the previous check finishes. A change-triggered job waits until a file change is detected and queues the next future selected time; it is not an unconditional midnight job. Jobs are synchronous and assume a single backend instance. Review last status for partial runs/provider/storage failures and retry with **Reindex now** when appropriate.

### PDF viewer or watermark downloads fail

Check **Administration > Document access** first: PDF viewing and download mode are independent. Rebuild the backend image after upgrading so PDFBox and rendering fonts are installed. Processing supports unencrypted PDFs up to 25 MB and 200 pages; failed watermarking blocks delivery of that file or ZIP instead of returning an original. Normal downloads retain their previous behavior. PDF viewing does not need an AI provider or the ML extraction container. See [document access](document-access.md).

### Outbound email setup and verification

Rebuild the backend after upgrading to include the mail dependency. Email starts disabled. Use **Administration > Email delivery** or the `APP_EMAIL_*` fields in [`.env.example`](../.env.example), select the corresponding source, and configure a provider-authorized sender. Compose forwards those variables; recreate the backend after environment changes. Allow backend outbound connections to the SMTP submission port (typically 587/465) or email API HTTPS endpoint. For an internal relay, use its reachable service hostname rather than container `localhost`.

Admin SMTP credentials/tokens and API keys require the persistent wrapping key. Existing wrapping-key rotation also covers `email_settings`; the JSON export omits this table, so preserve database backups and wrapping keys. Authenticated SMTP requires STARTTLS/implicit TLS and verified certificates. Supply externally renewed OAuth2 tokens when that authentication method is selected. A test sends a real message to the specified address; success means provider acceptance, with inbox delivery verified separately. No email is required for ordinary startup, and disabled/unavailable email produces an explicit optional result for application callers. See [SMTP/API provider setup and troubleshooting](email-delivery.md).
