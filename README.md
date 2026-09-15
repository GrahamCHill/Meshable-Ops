# Superset Docker App

A production-oriented Docker Compose application that combines:

- a React frontend
- a Spring Boot backend
- Apache Superset for embedded analytics and admin authoring
- PostgreSQL with pgvector, plus MongoDB, for application data
- a small Python ML helper for vectorization

The application is designed so users work through the app shell, not through a directly exposed Superset instance. Shared dashboards are embedded inside the product, while Superset authoring is available only to admins through `Superset Graph Builder`.

## What This Repository Provides

- App-managed authentication and role-based access control
- Embedded Superset dashboards through the app domain
- Admin-only Superset authoring at `/admin/superset-graph-builder`
- User lifecycle management from `/admin`
- Shared CRUD-style application records
- Vector search over application content
- Production deployment with Docker Compose and Caddy-based HTTPS

## Product Model

This repository is best understood as:

- a relatively simple CRUD-style internal application
- with embedded analytics layered in through Superset
- with admin operations surfaced through a real app page rather than a separate analytics portal

The current admin information architecture is intentional:

- `/admin`
  application access management, audit, backup, settings, and reindex controls
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
  internal helper for vectorization
- `postgres` with pgvector, `mongodb`
  application data stores
- `redis`
  currently a Superset-specific runtime dependency

Important access rules:

- all authenticated users can view shared items
- all authenticated users can view allowed embedded dashboards
- only admins can manage users, settings, backups, reindexing, dashboard embed configs, and Superset authoring routes

Important routing rule:

- frontend route hiding is not the security boundary for Superset authoring
- the frontend proxy is the real enforcement point for non-embed Superset route access

Additional architecture detail:

- [Architecture Guide](docs/architecture.md)
- [Database Layout](docs/database-layout.md)
- [Deployment Guide](docs/deployment.md)

## Service Map

| Service | Default access | Purpose |
|---|---|---|
| Frontend | `http://localhost:3000` | Main application UI |
| Backend API | internal only | Application APIs, reached through the frontend proxy |
| Superset | internal only | Embedded dashboards and admin authoring |
| ML backend | internal only | Vectorization helper |
| PostgreSQL + pgvector | internal only | Application relational data, Superset metadata, and vector search storage |
| MongoDB | internal only | Events and audit-style documents |
| Redis | internal only | Superset cache and task coordination |
| Caddy | `https://your-domain` | Production HTTPS entrypoint |

## Quick Start

First-time development setup (also safe to rerun after code changes):

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d --remove-orphans
```

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
- `http://localhost:3000/admin/superset-graph-builder`

Default local accounts:

- Admin: `admin@app.local` / `admin123`
- User: `user@app.local` / `user123`

These defaults are for local development only.

## Core Workflows

### Shared Dashboards

Dashboard embeds are runtime-managed from the application.

1. Sign in as an admin.
2. Open `/admin/superset-graph-builder` to author or edit dashboards in Superset.
3. Open `/dashboards`.
4. Use the admin controls there to create or edit shared dashboard embed entries.

Each shared dashboard entry stores:

- display label
- description
- embed UUID
- optional numeric Superset dashboard id
- sort order
- enabled state
- allowed app roles

### User Access Management

Admins manage access from `/admin`.

Current admin capabilities include:

- create users
- review the user roster
- deactivate users
- review live stack and network-boundary status
- review audit activity
- manage operational settings
- trigger vector reindexing
- export application backups

User lifecycle behavior:

- self-service registration is disabled
- production bootstrap users come from `APP_BOOTSTRAP_USERS_JSON`
- deleting a user from the admin UI is a deactivation flow, not a hard delete
- inactive users can no longer authenticate

### Vector Search

The repository includes seeded data so vector search is easy to validate locally.

Example search queries:

- `refund policy and billing correction`
- `employee onboarding and benefits`
- `release checklist for deployment`
- `incident response for phishing`
- `campaign performance and conversions`

On an existing environment, you can create new items and then reindex them.

## Security Model

The repository is configured so embedded analytics work through the application domain instead of through a separately exposed Superset site.

- Superset is not intended to be publicly exposed
- embedded dashboards load through `/embedded/...`
- guest tokens are minted by the backend
- the backend syncs allowed app origins into Superset embedded allowlists
- admin-only Superset routes are proxied through the frontend and protected by app auth

This keeps the user-facing analytics experience inside the application while preserving a separate admin authoring path.

## Production Readiness Notes

### Production Bootstrap Users

Production access should be explicit and environment-driven.

- local development can use convenience seed users
- production should use `APP_BOOTSTRAP_USERS_JSON`
- if the `prod` profile is active and `APP_BOOTSTRAP_USERS_JSON` is empty, convenience fallback users are not created

### Secrets and Configuration

Review and replace at minimum:

- `JWT_SECRET`
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
