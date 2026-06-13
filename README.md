# Superset Docker App

This repository runs a small analytics application as one Docker Compose stack:

- React frontend
- Spring Boot backend
- Apache Superset
- PostgreSQL
- MongoDB
- Qdrant
- Redis
- Python ML helper service

The app is designed so users interact with the frontend, not with Superset directly. Shared dashboards are embedded inside the app, while Superset authoring remains admin-only through the app shell.

## Architecture

The frontend is the only web entrypoint intended for normal use.

- `frontend` serves the app on port `3000`
- `backend` serves the API on port `8080`
- `superset` is internal-only and is proxied through the frontend
- `ml-backend` is internal-only
- `postgres`, `mongodb`, `redis`, and `qdrant` provide storage/runtime services

For end users:

- Dashboard embeds are available inside `/dashboards`
- Superset authoring is available to admins at `/admin/superset-graph-builder`
- Direct non-embed Superset routes are protected and redirected back through the app

## Services

| Service | Default access | Notes |
|---|---|---|
| Frontend | `http://localhost:3000` | Main user-facing app |
| Caddy (production) | `https://your-domain` | Public HTTPS entrypoint in production override |
| Backend API | `http://localhost:8080/api` | Mostly used through frontend proxy |
| Superset | internal only | Reached through frontend proxy/admin page |
| ML backend | internal only | Used by backend/services |
| PostgreSQL | `localhost:5432` | App + Superset metadata/data |
| MongoDB | `localhost:27017` | App document/event storage |
| Qdrant | `localhost:6333` | Vector search |
| Redis | `localhost:6379` | Cache / task coordination |

Default local dev accounts:

- Admin: `admin@app.local` / `admin123`
- User: `user@app.local` / `user123`

In production, do not rely on these defaults. Production bootstrap users now come only from `.env.production`.

## Quick Start

Start the full stack:

```bash
docker compose up --build -d
```

Useful checks:

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f superset
```

Then open:

- `http://localhost:3000`
- `http://localhost:3000/dashboards`
- `http://localhost:3000/admin`
- `http://localhost:3000/admin/superset-graph-builder`

## Dashboard Management

Dashboard embeds are runtime-managed. You do not need to hardcode dashboard definitions in the frontend.

Shared dashboards are configured from the app itself:

1. Log in as an admin.
2. Open `/admin` to manage users and admin access.
3. Open `/admin/superset-graph-builder` to create or edit dashboards in Superset.
4. Open `/dashboards`.
5. Use the admin controls on that page to add/edit enabled shared embeds.

Each shared dashboard record stores:

- Display label
- Description
- Embed UUID
- Optional numeric Superset dashboard id for guest-token minting
- Sort order
- Enabled state
- Allowed app roles

Non-admin users can view enabled shared dashboards that match their role, but cannot manage them.

## User Access Management

Admins manage access from `/admin`.

- Create new application users from the admin form
- Review the current access roster and roles
- Continue into the `Superset Graph Builder` subpage for embedded authoring

Self-service registration remains disabled. In local development, the default admin and user accounts are still seeded for convenience.

## Embed Security Model

This stack is set up so embedded dashboards work through the app domain rather than through a separate exposed Superset host.

- Superset is not published directly to a public host port
- Embedded dashboards load through the frontend proxy at `/embedded/...`
- Guest tokens are minted by the backend, not the browser
- The frontend sends its current `window.location.origin` when requesting a guest token
- The backend syncs that origin into Superset's embedded `allowed_domains` on demand
- Admin-only Superset authoring routes are proxied through the frontend and protected by app auth

This means a deployment can move from `localhost` to a real domain without manually rewriting Superset embed allowlists for each dashboard.

## Environment and Secrets

At minimum, review and change these values before deployment:

- `JWT_SECRET`
- `SUPERSET_SECRET_KEY`
- `GUEST_TOKEN_JWT_SECRET`
- `SUPERSET_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_USERS_JSON`
- database passwords

Current defaults live primarily in:

- [docker-compose.yml](/C:/Users/Graham/Documents/GitHub/superset-docker/docker-compose.yml)
- [docker-compose.prod.yml](/C:/Users/Graham/Documents/GitHub/superset-docker/docker-compose.prod.yml)
- [.env.production.example](/C:/Users/Graham/Documents/GitHub/superset-docker/.env.production.example)
- [backend/src/main/resources/application.properties](/C:/Users/Graham/Documents/GitHub/superset-docker/backend/src/main/resources/application.properties)
- [superset/superset_config.py](/C:/Users/Graham/Documents/GitHub/superset-docker/superset/superset_config.py)

For production, prefer injecting secrets via environment variables or Docker secrets rather than committing real values into the compose file.

## Recommended Deployment

The simplest reliable deployment is:

1. One Linux VM
2. Docker Engine + Docker Compose plugin
3. This stack running on a private Docker network
4. Caddy in front of it for HTTPS
5. Only HTTP/HTTPS exposed publicly

Recommended layout:

- Public:
  - `80/tcp`
  - `443/tcp`
- Private or admin-only:
  - SSH `22/tcp`
- Not publicly exposed:
  - `3000` from outside the VM
  - `8080`
  - `5432`
  - `27017`
  - `6333`
  - `6334`
  - `6379`

### VM Recommendation

For a modest internal deployment, a good starting point is:

- 4 vCPU
- 8 to 16 GB RAM
- SSD-backed disk

If Superset usage, background tasks, or vector workloads grow, scale up RAM first.

### HTTPS Recommendation

This repo now includes Caddy inside the production Compose setup so HTTPS can be handled directly by Docker Compose on the VM.

Best pattern:

- Public domain: `https://app.example.com`
- Caddy listens on `80/443`
- Caddy proxies to the internal `frontend` service
- TLS terminates at Caddy
- The frontend continues proxying backend and Superset traffic internally

That keeps the architecture simple:

- browser -> caddy -> frontend
- frontend -> backend/superset internally

Only two domain-specific values are required for the HTTPS template:

- `APP_DOMAIN`: the public hostname only, like `app.example.com`
- `ACME_EMAIL`: the email Caddy should use for Let's Encrypt / ACME

Important:

- use `app.example.com`, not `https://app.example.com`
- do not include a path
- point that hostname's DNS record at the VM before first certificate issuance

### Compose Port Advice

For production, I would usually expose only Caddy on the host and keep the rest internal.

A good next step would be to change:

- keep only Caddy public
- remove direct host publishing for `backend`
- remove direct host publishing for `postgres`, `mongodb`, `qdrant`, and `redis`

If you still want admin/debug access to those services, restrict them with:

- VM firewall rules
- private subnet access
- VPN
- SSH tunnel only

### Storage and Persistence

Persist Docker volumes for:

- PostgreSQL
- MongoDB
- Qdrant
- Redis
- Superset home

Also plan for:

- off-box database backups
- VM snapshots only as a secondary recovery method
- log rotation

### Security Notes

Before production, I strongly recommend:

1. Replace all default passwords and secrets.
2. Put the app behind HTTPS only.
3. Restrict SSH by IP or VPN if possible.
4. Avoid exposing database/vector/cache ports publicly.
5. Declare only the production users you actually want in `APP_BOOTSTRAP_USERS_JSON`.
6. Review `TALISMAN_ENABLED` and broader Superset hardening once the deployment domain is stable.

## Example Production Shape

One practical setup:

1. Ubuntu VM
2. Docker Compose stack bound to localhost/private interfaces where possible
3. Caddy in the Compose stack handling TLS for `app.example.com`
4. Public firewall open only for `80/443`
5. Admin SSH via VPN or allowlisted IPs

This repo now includes example deployment artifacts:

- [docker-compose.prod.yml](/C:/Users/Graham/Documents/GitHub/superset-docker/docker-compose.prod.yml)
- [.env.production.example](/C:/Users/Graham/Documents/GitHub/superset-docker/.env.production.example)
- [deploy/caddy/Caddyfile](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/caddy/Caddyfile)
- [deploy/nginx/app.example.conf](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/nginx/app.example.conf)
- [deploy/Caddyfile.example](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/Caddyfile.example)

## Production Deployment Steps

1. Provision a Linux VM.
2. Install Docker Engine and the Docker Compose plugin.
3. Clone this repository onto the VM.
4. Copy `.env.production.example` to `.env.production`.
5. Replace every placeholder secret and password in `.env.production`.
6. Set `APP_DOMAIN`, `ACME_EMAIL`, and `APP_BOOTSTRAP_USERS_JSON` in `.env.production`.
   Example:
   `APP_DOMAIN=app.example.com`
   `ACME_EMAIL=admin@example.com`
   `APP_BOOTSTRAP_USERS_JSON=[{"email":"admin@example.com","username":"admin","password":"...","role":"ADMIN"}]`
7. Point your DNS record, for example `app.example.com`, at the VM.
8. Make sure ports `80` and `443` reach the VM.
9. Start the stack with the production override:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

10. Verify that only `80/443` are reachable publicly.
11. Log in as admin and confirm `/admin` and `/admin/superset-graph-builder` work through the final domain.

## Cloudflare Note

If you put Cloudflare in front of the VM, this setup can still work well.

Recommended approach:

- point your domain at the VM public IP
- let Caddy terminate HTTPS on the VM
- use Cloudflare SSL mode `Full (strict)`

I would avoid `Flexible` mode because that leaves the Cloudflare-to-origin leg on plain HTTP.

If certificate issuance is ever finicky during first setup, a simple pattern is:

1. point DNS at the VM
2. make sure ports `80/443` are reachable
3. bring up Caddy
4. once certificates are issued and the site is reachable, enable or keep Cloudflare proxying as desired

## Deployment Checklist

- Change all default passwords and secrets.
- Keep only `80/443` public.
- Keep SSH restricted by IP, VPN, or bastion host.
- Keep Docker service ports private.
- Confirm Caddy can obtain certificates for the final domain.
- Confirm login works on the real domain.
- Confirm an embedded dashboard loads on the real domain.
- Confirm `/admin` and `/admin/superset-graph-builder` work only for admins.
- Confirm direct Superset authoring routes are not usable outside the app shell.
- Set up backups for PostgreSQL, MongoDB, and Qdrant if the data matters.
- Enable log rotation and basic VM monitoring.
- Review and rotate any bootstrap user passwords stored in `.env.production`.

## Production Files

### `docker-compose.prod.yml`

This override is intended for VM deployments where Caddy terminates HTTPS inside the Compose stack.

It:

- publishes `80/443` from the `caddy` service
- unpublishes backend and data-service ports
- keeps the frontend internal to Docker
- enables the Spring `prod` profile
- expects secrets to come from `.env.production`

Start it with:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

### Nginx Example

The example Nginx config is at:

- [deploy/nginx/app.example.conf](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/nginx/app.example.conf)

It assumes:

- public domain `app.example.com`
- TLS certs already installed
- the app stack is listening behind a host-level reverse proxy model

### Caddy Example

The active in-stack Caddy config is at:

- [deploy/caddy/Caddyfile](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/caddy/Caddyfile)

It uses:

- `APP_DOMAIN`
- `ACME_EMAIL`

That means changing the domain for a new deployment is just:

1. copy `.env.production.example` to `.env.production`
2. set `APP_DOMAIN` to your hostname
3. set `ACME_EMAIL` to your email
4. start the production compose stack

The separate example Caddy config is still at:

- [deploy/Caddyfile.example](/C:/Users/Graham/Documents/GitHub/superset-docker/deploy/Caddyfile.example)

Caddy is now the easiest path if you want Compose-managed automatic HTTPS with minimal setup.

### External Databases

Protecting Superset behind the app does not stop Superset from connecting to external SQL databases.

Superset can still connect to an external Postgres, MySQL, or other supported database as long as:

- the VM and container network can reach that database
- firewalls allow the traffic
- the database allows the VM's source IP or network
- the proper SQLAlchemy driver and connection settings are configured in Superset

This deployment model controls browser access to Superset. It does not block Superset's outbound database connectivity.

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

Rebuild after code/config changes:

```bash
docker compose up --build -d
```

## Notes

- Superset dashboard embeds are seeded with an empty allowlist and are updated at runtime when the app requests guest tokens.
- The frontend is the supported way to reach Superset admin pages.
- Shared dashboard visibility is role-based inside the app.
