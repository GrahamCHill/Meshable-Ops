# Full-stack plugins: React, Kotlin and Python

A plugin may contribute frontend pages, backend behavior and private worker services. **Document generator** is the included example spanning React, Kotlin and Python. **Workflows** uses React and Kotlin and composes with Documents when a process needs PDFs. These are optional components with normal workspace routes and admin cards, rather than separate applications.

All runtime contributions follow the [plugin licensing policy](licensing.md): covered plugins inherit the app's community license, independently authored service-integration components may retain compatible licenses, and independent external services keep their own terms. A separate worker/container does not automatically create a licensing exemption.

## Installation models

| Model | React | Kotlin | Python | Installation |
| --- | --- | --- | --- | --- |
| Reviewed source plugin | Shares the host React runtime; lazy pages and extension panels | Spring controllers/services/repositories and registered adapters | Explicit private worker routes and operation handlers, or a separate reviewed worker image | Include reviewed source, test it, rebuild the affected images, deploy, then enable in Admin |
| Runtime ZIP plugin | Can contain a prebuilt, self-contained React page inside its sandboxed HTML entrypoint | No uploaded server execution | No uploaded server execution | Upload in Admin with third-party extensions allowed; starts disabled |

ZIP installation is not a Kotlin/Python package runner. A browser bundle must include its dependencies inside the permitted HTML files; it does not receive the host React runtime. Archives declaring `KOTLIN` or `PYTHON` runtimes are rejected with source deployment guidance. Source plugins support these runtimes through the build/deployment path above. Do not upload JARs, Python modules, shell scripts, or frontend source expecting the web installer to execute/build them.

`PluginManifest.runtimes` documents the contributions used by a plugin, e.g. `setOf(PluginRuntime.REACT, PluginRuntime.KOTLIN, PluginRuntime.PYTHON)`. This metadata is visible in Manage; it describes the shipped implementation and does not execute an entrypoint. Version declarations remain advisory; ordinary API incompatibilities, missing required supporting modules, permissions, or disabled state still gate execution.

## Kotlin contribution

Follow the [source module guide](README.md): implement `WorkspacePlugin` as a Spring bean, declare tables/capabilities/configuration, and keep implementation under `com.starter.plugins.<id>`. Controllers normally use `/api/plugins/<id>`. Services and jobs must recheck `PluginRegistry.requireAvailable/requireTable`, member feature restrictions, and record ownership before work and after slow external operations. Use encrypted server settings for credentials, never browser assets or manifests.

Extracted built-in components may override the compiled definition's `accessViews` and `accessFeature` to retain established permission IDs/routes. The host's `BundledModuleGuard` protects their existing API roots, and `BundledModuleAccess` applies service/table checks. New plugins normally use their own namespace. Retaining an existing physical table requires an explicitly reviewed `retainedTables` declaration on the source definition; ZIPs cannot request this exception.

## React contribution

Add `frontend/src/plugins/modules/<id>/index.ts` implementing `WorkspacePluginModule`. `loadPage` is lazy. Submenus, panels, and named extension slots work as described in [pages and menus](pages-and-menus.md). Reviewed bundled descriptors may additionally declare `navigationPath` and `routes` with `path`, `label`, `feature`, lazy `loadPage`, and optional `adminCategory`/`description`. The host discovers and guards these first-class routes through `BundledPluginRoute`. This is how Documents and Workflows retain their existing locations and appear in admin categories. A ZIP cannot claim arbitrary host routes.

Put feature-only components, API contracts and styles beside the module; keep shared storage/PDF policies, the app shell, user settings and shared transports in the host. Hide optional integration controls and avoid calls to a disabled supporting plugin. Declare dependencies/capabilities, then implement a reviewed adapter or service call; metadata alone does not add behavior.

## Python contribution

Use a private service boundary, with Kotlin handling public authentication/RBAC, activation and persistence. For document-related operations, the included renderer offers:

1. A reviewed package under `weasy-renderer/plugins/<id>/`.
2. An explicit router factory receiving the host's bounded authenticated `payload` reader and `render_payload` worker dispatcher. Register it in `plugins/registry.py` using a reviewed import; do not import a request-supplied module path.
3. An explicit operation name/handler in `render_plugin`. The host launches a bounded child process; existing wall-clock, memory, CPU, output/page limits and embedded-raster resource rules still apply.
4. A Kotlin client calling the private named API after plugin/record checks. Keep the renderer token on the server and expose no worker port publicly.

For a different Python service, add its reviewed image/service and private transport rather than forcing it into the PDF worker. Apply equivalent token authentication, deadlines, input/output bounds, health checks, sanitised errors and activation checks. Persist durable state in declared/owned databases; worker containers must not be the only copy of plugin state.

## Build, update and verify

Run `mvn test` with Java 21 in `backend/`, `npm run build` in `frontend/`, and the Python unit suite in the relevant service environment. The renderer suite needs its native WeasyPrint libraries; it can run inside its image. Rebuild affected services, for example `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build backend frontend weasy-renderer`.

Keep source plugins included in future application images, preserve their schema/migrations, and retain PostgreSQL plus wrapping keys. Disabling/removing a bundled module changes activation, not the image or records. Test disabled direct endpoints, retained permissions/data across updates, missing supporting plugins, per-record ownership, table roles, clear-data references and optional worker outages. Do not send real notifications as part of a test unless authorised.
