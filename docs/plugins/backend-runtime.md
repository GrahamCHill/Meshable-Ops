# Uploaded backend code

A ZIP may provide a frontend, a backend, or both. Backend-only packages need neither HTML nor a navigation item. The host loads backend declarations from PostgreSQL at request time; adding a package does not require an app rebuild, source edit, or restart. React code must be bundled by its author and Kotlin compiled by its author before packaging. The host does not run npm, pip or Maven on uploads.

## Descriptor

`plugin.json` keeps the normal `manifest` and adds `backend`. For example:

```json
{
  "manifest": {
    "id": "zip-calculator", "name": "Calculator", "version": "1.0.0",
    "description": "Custom server calculation", "compatibleVersions": ["1.0.0"],
    "extensions": ["API"], "runtimes": ["PYTHON"]
  },
  "backend": {
    "modules": {"main": {"runtime": "PYTHON", "entrypoint": "backend/calculator.py"}},
    "routes": [{"path": "/calculate", "module": "main", "methods": ["POST"], "roles": ["ADMIN", "USER"]}],
    "allowedOrigins": []
  }
}
```

`author.json` is still required. Use `PAGE` plus `entrypoint` for a frontend, `API` plus `backend` for a backend, or both. The [full stack example](../../examples/plugins/full-stack/) has a React page, a Python module and a Kotlin module in one ZIP; the [backend-only example](../../examples/plugins/backend-only/) has only Python code.

Declare 1–4 named modules and 1–40 routes. `PYTHON` entrypoints are packaged `backend/*.py`; `KOTLIN` entrypoints are self-contained executable `backend/*.jar` files targeting Java 21 or earlier. A package can mix runtimes. Python supplies the standard library and `meshable` SDK; vendor any other pure Python dependencies under `backend/` and import them from the entrypoint's directory. Native packages and arbitrary dependency installation are not provided. JVM dependencies must be included in the JAR. The bootstrap also makes other `backend/` artifacts available inside the job; there is no persistent local filesystem.

Routes use exact relative lowercase paths of 1–4 segments, such as `/notes/summary`. Variable segments are not supported: send record IDs in query parameters or JSON. Supported methods are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`. Roles must be included in the manifest. `ADMIN` includes super admins; `USER` covers internal members. External accounts cannot call uploaded APIs. GET routes cannot declare database writes or send non-GET outbound requests.

## Handler and host SDK

```python
from meshable import serve, Response

def calculate(request, host):
    values = (request.get('body') or {}).get('values', [])
    if not isinstance(values, list):
        return Response({'error': 'Provide a list'}, 422)
    return {'count': len(values)}

serve(calculate)
```

Handlers receive `plugin {id, version}`, `actor {id, role}`, `path`, `method`, `query` (lists of strings), `body`, and `configuration` containing public settings only. Ordinary results become HTTP 200 JSON; `Response(body, status)` controls 2xx or 4xx/5xx status codes. Redirects, custom response headers, streaming responses and file downloads are not exposed. The host marks plugin responses separately, so a plugin's own 401 does not clear the member's application session; actual app authentication failures retain normal sign-out behavior. Unexpected failures return a generic 503 without source, stderr, credentials or request payloads.

For Kotlin, copy the [SDK](../../plugin-runtime/sdk/kotlin/PluginSdk.kt) into the plugin build, include Jackson and Kotlin dependencies in an executable JAR, and call `PluginWorker.serve { request, host -> ... }`. Return a JSON-compatible object or `BackendResponse(body, status)`. The [example Maven build](../../examples/plugins/full-stack/pom.xml) packages both SDK and dependencies. stdout is reserved for the JSON-line protocol; diagnostic stderr is bounded and discarded.

Backend code has no database connection, application JWT, raw setting secrets, Docker socket or network interface. Use the host SDK for these explicitly declared operations:

| Route permission | SDK operations | Additional checks |
| --- | --- | --- |
| `data.read` | `host.list(table, offset)`, `host.get(table, id)` | Only this plugin's declared managed tables; current table roles and owner scope |
| `data.write` | `host.create`, `host.update`, `host.remove` | Table write roles, ownership, bounded JSON and optimistic revisions |
| `http` | `host.http(method, url, headers=..., body=..., credential=...)` | Exact declared HTTPS origins, public DNS, no redirects, bounded responses and safe headers |
| `settings.read` | Python `host.shared_settings(reference)`, Kotlin `host.sharedSettings(reference)` | Explicitly bound shared profile, declared field allowlist, public values only |

Without a route permission, the corresponding operation is denied even if the current user could otherwise access it. The host does not expose arbitrary SQL, shell commands, filesystem access or arbitrary application service calls. The same tables can be included/excluded from search using normal plugin administration. Business code can define validation, transformations, calculations and external-service integrations without being hardcoded into core modules.

Host writes commit individually; a later worker failure does not roll back earlier operations. Use optimistic revisions and business-level idempotency where needed. Execution and provider requests are not automatically retried.

For a provider key, declare a `SECRET` setting and an `allowedOrigins` entry such as `https://api.example.com`. Then request host-side credential injection:

```python
result = host.http('POST', 'https://api.example.com/jobs', body={'title': 'Review'},
                   credential={'key': 'apiKey', 'header': 'Authorization', 'prefix': 'Bearer '})
```

Keys saved through admin remain encrypted in PostgreSQL under the persistent app wrapping key. The host injects only this plugin's declared secret into its outbound request; it does not give the raw value to frontend or worker code. Review allowed origins before entering credentials. A provider can return sensitive data, so plugins and their configured services must still be trusted. Private-address targets are rejected by default; the existing deployment-wide `APP_PLUGIN_ALLOW_PRIVATE_TARGETS` setting is a deliberate administrator override.

Plugins can also select [shared connection profiles](shared-settings.md). Add `sharedReference` to an HTTP credential request to use a declared field from that selected profile. The host checks both the consumer's allowed origin and the profile's configured HTTP origin; non-HTTP profiles cannot supply HTTP credentials. Shared values fetched through `settings.read` always omit secrets.

## Calling code

Authenticated callers use `/api/plugins/{id}/backend/{declared-path}`. In a ZIP page:

```js
const result = await meshableOps.backend.request('/calculate', {values: [1, 2, 3]}, 'POST');
const notes = await meshableOps.backend.request('/notes', null, 'GET', {offset: 0});
```

The browser bridge is scoped to its own plugin. It receives no token and cannot choose another plugin or a host URL. Calls recheck current account activation, role, feature access, dependencies, plugin activation, package hash and the global extension switch. Host callbacks bind to that exact member, route, method and package, and recheck them. Each callback credential is short lived, stored only as a hash in `workspace_plugin_invocations`, and deleted on completion. Workers never receive it.

## Deployment and isolation

Fresh setup generates `APP_PLUGIN_RUNNER_TOKEN`. Keep it private and identical in `backend` and `plugin-runner`. Existing deployments may add a random secret of at least 32 characters to their private environment file; omitting it leaves uploaded backend endpoints unavailable while core functionality and frontend-only ZIPs continue working. The Compose stack builds the fixed worker images and private runner:

```bash
export DOCKER_GID="$(stat -c %g /var/run/docker.sock)"
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build backend frontend plugin-python-worker plugin-kotlin-worker plugin-runner
```

The runner runs as UID 10001. `DOCKER_GID` grants its supplementary group access to the host Docker socket; export it before manual Compose commands (or save the numeric value in the private environment file). GitHub releases and Terraform runtime startup detect this group automatically. Docker Desktop sockets owned by group 0 use the default. The runner installs only wheels from `plugin-runtime/requirements.lock`, with every resolved version and SHA-256 hash pinned. Install that lockfile with `--require-hashes --only-binary=:all:` for local testing too.

The two worker services build tagged images and exit successfully; the runner creates disposable jobs from those fixed images. No uploaded code executes inside the Kotlin application or runner process. Jobs run as UID 10001, with networking disabled, a read-only root, no mounts, dropped capabilities, no new privileges, 384 MiB RAM, half a CPU, 32 processes and 64 MiB temporary storage. They are killed after 40 seconds and removed after every result/failure. A periodic sweep removes abandoned jobs after runner restarts. A runner permits four concurrent jobs; a backend permits 30 calls per member/plugin per minute, 64 KiB request JSON, 8 KiB query parameters, 64 host operations per job, and approximately 4 MiB results.

**The trusted runner mounts the Docker daemon socket**, which is a powerful host administration capability. Keep it private, never publish port 8090, and run this stack only on a host you control. Uploaded workers have no socket or host mounts. Container isolation is a defense boundary, not a guarantee against malicious code or kernel/runtime vulnerabilities; review packages and maintain Docker/worker images. High-trust-separation deployments should use a dedicated Docker host for the runner and its jobs.

Backend declarations and artifacts live in `workspace_plugin_packages`; state and encrypted settings remain in their existing tables. Preserve PostgreSQL and wrapping keys during updates. Removal disables access and deletes code, while retaining data/settings until explicitly cleared. Reinstallation uses retained managed data and starts disabled. No backend invocation runs automatically on install, upgrade or removal. ZIPs do not dynamically register typed notification adapters, recurring jobs or arbitrary Spring beans; those hooks remain reviewed [source modules](full-stack.md).

## Verify

Run `mvn test` in `backend/`, `npm run build` in `frontend/`, and `python -m unittest discover -s tests -v` inside the `plugin-runtime` environment with its requirements installed. CI builds the React/Kotlin example and tests backend package validation and the Python runner. Check disabled endpoints, missing workers, RBAC, ownership, scoped secrets, invalid routes, timeouts and retention before shipping a plugin. Do not test with real recipients or provider credentials.
