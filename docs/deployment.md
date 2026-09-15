# Deployment Guide

This guide explains the practical deployment shapes supported by this repository and how the built-in reverse-proxy setup is intended to be used.

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

Stateful dependencies can be moved out to managed services:

- PostgreSQL
- MongoDB
- pgvector-capable PostgreSQL
- object storage

The current Redis instance is intentionally not listed there as a core app dependency because today it is only supporting Superset. If a broader app-level Redis stack is introduced later, that should be documented as a separate concern.

Use this when:

- you want a smaller app VM
- backups, storage, and service durability should be handled elsewhere
- the app is mostly behaving like a CRUD-style internal system with analytics features layered on top

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
- `SUPERSET_SECRET_KEY`
- `GUEST_TOKEN_JWT_SECRET`
- `SUPERSET_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_USERS_JSON`

If you offload services, also override any relevant connection settings for:

- PostgreSQL
- MongoDB
- pgvector data as part of PostgreSQL
- object storage

For the current stack, Redis usually stays grouped with Superset unless you are deliberately externalizing Superset's own runtime dependencies too.

## User Bootstrap Behavior

Production bootstrap behavior is intentionally stricter than local development.

- local development can fall back to convenience seed users
- production should rely on `APP_BOOTSTRAP_USERS_JSON`
- if the `prod` profile is active and that value is empty, the app skips convenience user creation

This keeps production access explicit and environment-driven.

## Recommended Rollout Steps

1. Provision the VM.
2. Install Docker Engine and the Docker Compose plugin.
3. Clone the repository.
4. Copy `.env.production.example` to `.env.production`.
5. Replace every placeholder secret.
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

12. Verify login, `/admin`, and `/admin/superset-graph-builder` on the real domain.

## Public and Private Ports

Normal production posture:

- public:
  `80`, `443`
- private or tightly restricted:
  `22`
- not publicly exposed:
  `3000`, `8080`, `5432`, `27017`, `6379`

The production override already removes direct host publishing for backend and data services. Keep it that way unless you have a specific secured admin-access reason.

## Sizing Guidance

Sizing depends on whether the VM is carrying only app logic or the full supporting stack.

- app host with stateful services offloaded:
  often reasonable around `2-4 vCPU` and `4-8 GB RAM`
- full all-in-one stack for a very small team:
  `2 vCPU / 4 GB RAM` can be a lean starting point
- full all-in-one stack with more buffer:
  `4 vCPU / 8-16 GB RAM` is a safer comfort range

Because Redis is currently only there for Superset, it normally makes sense to keep it on the VM beside Superset rather than treating it as a separate externalization requirement by default.

These are planning ranges, not benchmark guarantees. Real usage depends on dashboard concurrency, Superset query mix, vector-search frequency, and background task load.

## Verification Checklist

After deployment, verify:

- only `80/443` are publicly reachable
- login works on the real hostname
- `/dashboards` loads shared embeds
- `/admin` loads for admins
- `/admin/superset-graph-builder` loads for admins
- non-admin users cannot access direct Superset authoring pages
- health checks respond as expected

## Troubleshooting

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
