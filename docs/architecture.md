# Architecture

This document describes the current runtime architecture of the application as it exists in this repository.

## Overview

- End users access the React frontend.
- The frontend proxies backend API calls, embedded Superset dashboards, and admin-only Superset authoring routes.
- The backend owns application auth, app APIs, guest-token minting, and embed configuration management.
- Superset remains internal to the stack and is not intended to be exposed directly to users.

## Mermaid Diagram

```mermaid
flowchart LR
    user["User Browser"]
    admin["Admin Browser"]

    caddy["Caddy\nproduction HTTPS entrypoint"]
    frontend["Frontend\nReact + nginx proxy"]
    backend["Backend\nSpring Boot API"]
    ml["ML Backend\nFastAPI helper"]

    superset["Apache Superset\nweb app"]
    supersetInit["Superset Init Job"]
    supersetEmbedInit["Superset Embed Init Job"]
    supersetWorker["Superset Celery Worker"]
    supersetBeat["Superset Celery Beat"]

    pg["PostgreSQL cluster"]
    mongo["MongoDB"]
    qdrant["Qdrant"]
    redis["Redis"]

    user -->|"HTTPS / HTTP"| caddy
    admin -->|"HTTPS / HTTP"| caddy
    caddy --> frontend

    frontend -->|"/api/*"| backend
    frontend -->|"/embedded/*"| superset
    frontend -->|"/superset/* admin authoring"| superset

    backend -->|JPA app data| pg
    backend -->|event/activity docs| mongo
    backend -->|vector search| qdrant
    backend -->|ML inference / embeddings| ml
    backend -->|Superset admin API + guest tokens| superset

    superset -->|metadata DB| pg
    superset -->|cache / async state| redis
    supersetWorker --> redis
    supersetBeat --> redis
    supersetWorker --> superset
    supersetBeat --> superset

    supersetInit --> superset
    supersetInit --> pg
    supersetInit --> redis

    supersetEmbedInit --> superset
    supersetEmbedInit --> backend
    supersetEmbedInit --> pg
```

## Request Flow Notes

### Standard app requests

1. Browser requests hit Caddy in production.
2. Caddy forwards traffic to the `frontend` container.
3. The frontend serves the SPA and proxies `/api/*` to the backend.
4. The backend reads and writes app data in PostgreSQL, MongoDB, and Qdrant.

### Embedded dashboards

1. The frontend loads embedded dashboards through `/embedded/...`.
2. The backend mints Superset guest tokens.
3. The backend also syncs the active app origin into Superset embedded allowlists.
4. Superset serves the embedded dashboard without being exposed as a separate public host.

### Admin Superset authoring

1. Admins use `/admin/superset-graph-builder`.
2. The frontend proxies approved Superset authoring routes.
3. The app's auth guard decides whether the user can access those routes.
4. Superset still runs internally, but the admin experience is surfaced through the app shell.
