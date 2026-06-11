# Superset Docker Stack

Apache Superset + Spring Boot backend + React frontend + PostgreSQL + MongoDB + Qdrant in one `docker compose` stack.

## Services

| Service | URL | Credentials |
|---|---|---|
| React frontend | http://localhost:3000 | admin@app.local / admin123 |
| Apache Superset | internal only | embedded admin access only |
| Java backend API | http://localhost:8080/api | JWT via frontend |
| Python ML backend | internal only | reachable only inside Docker |
| Qdrant dashboard | http://localhost:6333/dashboard | n/a |
| PostgreSQL 18 | localhost:5432 | superset_user / superset_pass |
| MongoDB | localhost:27017 | app_user / app_pass |
| Redis | localhost:6379 | n/a |

## Quick start

```bash
docker compose up --build -d
docker compose logs -f superset-init
```

This stack now uses a fresh PostgreSQL 18 volume mounted at `/var/lib/postgresql`, which matches the official Postgres 18 image layout. The old `postgres_data` volume is left untouched and is not automatically migrated into the new 18 instance.

Then open:

- `http://localhost:3000`
- `http://localhost:3000/dashboards` for the shared embedded dashboard view
- `http://localhost:3000/admin/superset` for dashboard authoring as an admin

## Superset exposure model

- Superset is not published on a host port anymore.
- The frontend proxies embedded Superset traffic through `/superset/`.
- Any authenticated app user can view embedded dashboards.
- Dashboard makers and admins use `/admin/superset` to create and manage dashboards.
- `POST /api/superset/guest-token` is authenticated and used for embedded viewing.
- The Python ML backend is internal-only and not exposed to the host.

## Embedding a dashboard in the React app

### 1. Connect Superset to the app database

Open a Superset session from inside the Docker network when you need to configure dashboards, then create a PostgreSQL connection with:

- Host: `postgres`
- Port: `5432`
- Database: `app_db`
- Username: `superset_user`
- Password: `superset_pass`

### 2. Create a dashboard

1. Use SQL Lab against `items` or `users`.
2. Save a chart.
3. Add it to a dashboard.
4. Save the dashboard.

### 3. Enable embedding

1. Open the dashboard in Superset.
2. Choose `Embed dashboard`.
3. Enable embedding and copy the UUID.
4. Note the numeric dashboard id for the same dashboard.

### 4. Configure the frontend

Edit [frontend/src/components/pages/DashboardsPage.tsx](C:\Users\Graham\Documents\GitHub\superset-docker\frontend\src\components\pages\DashboardsPage.tsx):

```ts
const DASHBOARDS = [
  {
    id: 'embedded-dashboard-uuid',
    guestTokenResourceId: '1',
    label: 'My Dashboard',
    description: 'Items overview',
  },
]
```

- `id` is the embedded dashboard UUID.
- `guestTokenResourceId` is the numeric Superset dashboard id used to mint the guest token.

Rebuild the frontend after changing it:

```bash
docker compose up --build -d frontend
```

## Python ML backend

Other containers can reach the internal ML service at:

- `http://ml-backend:8000/health`
- `http://ml-backend:8000/api/v1/ml/runtime`
- `http://ml-backend:8000/api/v1/ml/packages/paddle`
- `http://ml-backend:8000/api/v1/ml/vectorize`

To add extra Python packages such as Paddle:

```bash
ML_EXTRA_PIP_PACKAGES="paddlepaddle" docker compose up --build -d ml-backend
```

Example with multiple packages:

```bash
ML_EXTRA_PIP_PACKAGES="paddlepaddle paddleocr" docker compose up --build -d ml-backend
```

## Guest token flow

The browser never sees Superset admin credentials. The Java backend logs into Superset, mints a guest token, and the frontend uses that token for the embedded iframe.

## Teardown

```bash
docker compose down
docker compose down -v
```
