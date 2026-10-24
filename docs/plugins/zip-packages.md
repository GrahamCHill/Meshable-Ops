# Installing and creating ZIP plugins

ZIP plugins install at runtime: no image rebuild or application restart is needed. They supply self-contained HTML/JavaScript pages, submenu items, settings fields, and optional host-managed PostgreSQL tables and search. Existing compiled plugins continue to support server integrations and capabilities.

Packages, artwork, settings, and records survive application container updates in PostgreSQL. Declare `compatibleVersions` in the manifest; omitted/mismatched releases warn with backup/caution advice and do not block use. See [compatibility and updates](compatibility-and-updates.md).

## Install and control access

1. As a `SUPER_ADMIN`, open **Administration → People & security → Security** and enable **Allow third-party extensions**. It defaults off. The switch is not delegable to an `ADMIN`.
2. Open **Administration → Workspace → Plugins**, select a ZIP, and choose **Install ZIP**. Only a super admin can upload packages. Treat every package as reviewed application functionality: author information is self-declared, not a signature or identity verification.
3. Open the installed plugin's **Manage** view. New packages start disabled and excluded from search. Choose roles, table policies, configuration, and search inclusion, then enable the plugin.

Turning the security switch off immediately rejects uploaded plugin APIs and search, and removes their pages and menus when the catalogue refreshes (within 30 seconds in an already open browser). An open frame rechecks its page within 15 seconds. Saved activation choices, records, package files, and encrypted configuration remain; bundled plugins are unaffected. **Clear plugin data** still lets an authorized plugin administrator empty declared tables after typing the plugin ID; it does not remove the package, schema, or settings.

Packages are stored in `workspace_plugin_packages`, so replicas and restarts see the same installation. Back up this table, `workspace_extension_security`, plugin settings/configuration, and owned data tables. A package cannot overwrite an installed ID. A super admin can remove a package after typing its ID; this deletes installed page/artwork code and disables access, while retaining business tables and encrypted configuration. Reinstalling the same ID reuses its managed tables and starts disabled. Clear data separately before removal if records should be emptied. See [presentation and lifecycle](presentation-and-lifecycle.md).

## Package layout

Put these files at the ZIP root, without a containing directory:

```text
plugin.json
author.json
index.html
guide.html
icon.webp       # optional
banner.jpg      # optional
```

`author.json` is required and must contain exactly these four string fields. Every value may be empty:

```json
{"name": "", "organization": "", "email": "", "website": ""}
```

`plugin.json` wraps the existing host manifest with the entry page and submenu-page mapping:

```json
{
  "manifest": {
    "id": "zip-notes",
    "name": "Notes",
    "version": "1.0.0",
    "description": "Private notes",
    "apiVersion": 1,
    "compatibleVersions": ["1.0.0"],
    "extensions": ["PAGE", "DATABASE", "SEARCH", "SETTINGS"],
    "menuItems": [{"path": "guide", "label": "Guide", "roles": ["ADMIN", "USER"]}],
    "tables": [{"name": "plugin_zip_notes_notes", "label": "Notes"}],
    "settingsFields": [{"key": "heading", "label": "Page heading", "maxLength": 100}]
  },
  "entrypoint": "index.html",
  "pages": {"guide": "guide.html"}
}
```

The manifest uses the same ID/version, role, setting, submenu, and table-namespace validation as bundled modules. `ADMIN` represents administrators including super admins; `USER` represents internal members. External accounts are excluded. Every declared submenu must map to a page. ZIP pages support exact single-segment submenu paths, not arbitrary nested routes. The package must declare `PAGE`; optional extensions are `DATABASE`, `SEARCH`, and `SETTINGS`. `SEARCH` requires tables. Server `API`, notification adapters, capability declarations, and raw SQL are not supported by ZIP packages; implement those as reviewed [source modules](README.md).

Limits: 5 MB compressed, 10 MB expanded, 2 MB per file, 50 ZIP entries, 30 installed ZIP packages, and 8 managed tables per package. Only UTF-8 `.html` pages, the two root JSON metadata files, and PNG/JPEG/WebP artwork are accepted. Declare relative `icon` and `banner` paths in the manifest; the host validates and normalizes them. Scripts and styles must be bundled inline in the HTML; page images/fonts must use data URLs. Categories, relationships, admin placement, optional settings, and screen contributions use the [presentation contract](presentation-and-lifecycle.md). Absolute/traversing paths, duplicate filenames, unsupported API versions, JARs, SQL, and other executables are rejected. Nothing is extracted to the server filesystem.

## Page API

The host injects `window.weaveops` before the page's scripts. Methods return promises and reject with an `Error` on denial, stale revisions, or network failure:

```js
const context = await weaveops.context();
// context: plugin {id, name}, section, configuration (public fields only),
// tables, tableAccess, role, and optional record/table search-link parameters.
const rows = await weaveops.data.list('plugin_zip_notes_notes');
const created = await weaveops.data.create('plugin_zip_notes_notes', {title: 'Review', body: 'Notes'});
const record = await weaveops.data.get('plugin_zip_notes_notes', created.id);
await weaveops.data.update('plugin_zip_notes_notes', record.id, {...record.data, title: 'Reviewed'}, record.revision);
await weaveops.data.remove('plugin_zip_notes_notes', record.id, record.revision + 1);
await weaveops.navigate('guide'); // Empty string returns to the root page.
```

Records are JSON objects up to 32 KB. The host creates UUIDs, owner IDs, timestamps, revisions, and local 384-dimensional vectors. `list(table, offset)` returns at most 100 rows in deterministic recent-update order. Members can only read/write their own records; administrators can manage all rows, subject to plugin feature and table access. Optimistic revision checks prevent lost updates/deletes (`409`). Table names are checked against the plugin's manifest, and queries use parameterized values. Do not render stored text with `innerHTML`.

Search is opt-in per plugin. Host-managed tables participate in keyword/local similarity search without an AI provider. Ownership and table policies apply before the result limit and permissions are rechecked before returning results. These records are not automatically added to the storage-only AI document retrieval context. Secret settings are encrypted by the host and never supplied to page code; public configuration fields are available through `context()`.

## Isolation and trust

Pages run in an `iframe` with `sandbox="allow-scripts allow-forms"`, without `allow-same-origin`, popups, downloads, or top-level navigation permissions. Form permission lets client-side submit handlers run; CSP `form-action 'none'` still blocks actual form submissions. CSP also restricts resource loading and blocks direct API connections. Refresh metadata, base tags, and embedded frames/objects are removed. A per-frame message channel exposes only this plugin's declared data operations and submenu navigation; no JWTs, session cookies, arbitrary host requests, or configuration secrets are passed to code. Requests are bounded to eight concurrent calls and 120 calls per minute per frame.

This isolation limits host privileges; it is not a code review or a guarantee against malicious UI, data misuse, or browser exploits. A plugin can act on all records its current member may access in that plugin. Review packages before enabling them. Integrations requiring outbound credentials or privileged server logic belong in the compiled plugin model.

## Working example

The [Notes example](../../examples/plugins/notes/) includes settings, owner-scoped CRUD, a Guide submenu, and optional search. From that directory, build a ZIP with Python:

```bash
python -m zipfile -c notes.zip plugin.json author.json index.html guide.html
icon.webp       # optional
banner.jpg      # optional
```

Upload `notes.zip`, configure its heading, and enable it. No example data is seeded. The [project licensing](../../LICENSING.md) still applies to combined works and dependencies; ZIP packaging does not automatically create a licensing exception.

## HTTP endpoints

| Endpoint | Authorization / purpose |
| --- | --- |
| `GET /api/admin/security/extensions` | Super admin; global policy and optimistic revision |
| `PUT /api/admin/security/extensions` | Super admin; `{allowThirdPartyExtensions, revision}` |
| `DELETE /api/admin/plugins/{id}` | Super admin; `{confirmation: "plugin-id"}`, removes installed code, retains business data |
| `POST /api/admin/plugins/{id}/restore` | Super admin; restore a bundled plugin, still disabled |
| `POST /api/admin/plugins/install` | Super admin; multipart `file`, global policy must allow installation |
| `GET /api/plugins/{id}/runtime?section=guide` | Active permitted member; sandbox page and redacted configuration |
| `GET /api/plugins/{id}/data/{table}?offset=0` | Table reader; owner scope for members |
| `GET /api/plugins/{id}/data/{table}/{record}` | Table reader; owner/admin |
| `POST /api/plugins/{id}/data/{table}` | Table writer; `{data}` |
| `PUT /api/plugins/{id}/data/{table}/{record}` | Table writer; `{data, revision}` and owner/admin |
| `DELETE /api/plugins/{id}/data/{table}/{record}?revision=0` | Table writer and owner/admin; revision required |

The standard plugin settings, encrypted configuration, and clear-data endpoints also apply.
