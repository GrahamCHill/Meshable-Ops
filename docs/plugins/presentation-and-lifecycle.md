# Plugin presentation, placement, and lifecycle

Artwork appears in the catalogue and management view. Workspace page banners are optional: `showPageBanner` defaults to `false`; authors may explicitly enable it. Kanban leaves it off. Catalogue activation checkboxes are staged until **Save activation changes** applies them together; see [bulk activation](kanban.md#bulk-plugin-activation).

Plugin API version 1 now supports artwork, categories, explicit dependencies, optional relationships, admin placement, and screen contributions. Old manifests receive defaults; bundled modules get category/artwork defaults. ZIP metadata still needs the required `author.json` fields, even when blank.

```json
{
  "id": "client-notes",
  "name": "Client notes",
  "version": "1.0.0",
  "compatibleVersions": ["1.0.0", ">=1.1.0 <2.0.0"],
  "description": "Notes beside client work",
  "extensions": ["PAGE", "DATABASE", "SETTINGS"],
  "categories": ["Clients", "Operations"],
  "icon": "art/icon.webp",
  "banner": "art/banner.webp",
  "requires": [],
  "extends": ["kanban"],
  "incompatibleWith": {"legacy-notes": "Both modules own the same note workflow"},
  "hasAdminSettings": true,
  "adminMenu": {"category": "client-tools", "categoryLabel": "Client tools", "label": "Notes connection"},
  "settingsFields": [{"key": "heading", "label": "Heading", "type": "TEXT"}],
  "placements": [{"slot": "page.projects.after", "label": "Client notes", "section": "", "mode": "PANEL"}]
}
```

This is a manifest fragment. Declare owned tables and package entrypoints/pages as described in [ZIP packages](zip-packages.md).

## Artwork and categories

Artwork paths refer to PNG, JPEG, or WebP files inside the ZIP. Images are decoded with dimension/size limits and re-encoded before serving; SVG, external URLs, absolute paths, and traversal paths are rejected. Recommend a square icon of at least **64 × 64 px**, and a **1200 × 240 px** banner with important content near the center. Package limits are 12 MiB per entry, 16 MiB compressed and 32 MiB expanded; artwork also has its own bounded image decoder. Icons are normalized to square PNG; banners to bounded JPEG. Gallery images are presentation assets; packaged page resources follow the [frontend asset contract](zip-packages.md).

Bundled authors can place artwork under `backend/src/main/resources/plugins/<id>/<path>` and reference it in the manifest. Missing artwork can use the generated `builtin` fallback. Uploaded packages must supply referenced artwork themselves.

The plugin catalogue can filter up to five short categories per plugin. The icon and banner appear in the catalogue/settings. Workspace pages omit banners by default, including Calendar; authors can opt in with `showPageBanner: true`. Artwork endpoints enforce member plugin access or administrator plugin-management permission.

## Requires, extends, and incompatibility

Application release compatibility is separate: [`compatibleVersions`](compatibility-and-updates.md) accepts exact versions and ranges and produces advisory warnings. Missing or mismatched release declarations do not block use or remove installed state after an update.

- `requires` lists plugin IDs that must be installed, enabled, compatible, and available to the role before this plugin becomes active. Cycles cannot activate themselves.
- `extends` lists optional integrations. Missing these leaves the base plugin usable. It describes a relationship; it does not automatically implement an adapter or enable another plugin.
- `requiresCapabilities` and `features` remain available for executable capability-based composition. See [composition](composition.md).
- `incompatibleWith` maps plugin IDs to author-supplied reasons. If both are enabled, both are blocked and the reason is shown, even if only one author declares the conflict. Unsupported bundled host API versions are shown as incompatible rather than crashing startup. ZIP installation rejects unsupported host API versions.

Dependency and conflict checks also protect direct APIs. Disabling/removing a required plugin blocks its consumers without deleting their data. Optional connections retain their previous permission checks.

## Admin categories and optional settings

`adminMenu.category` can use an existing admin group (`workspace`, `people-security`, `storage-sharing`, `communications`, `documents-workflows`, `analytics-search`, `operations`) or a custom slug with `categoryLabel`. The host adds a direct settings card there, including while a connection is disabled and needs configuration. Existing group names cannot be replaced by a plugin.

`adminMenu.icon` selects a default built-in section icon. Administrators can override category artwork with a built-in icon or uploaded WebP in **Workspace → Navigation & section icons**. See [navigation and artwork](../navigation-and-icons.md) for icon names, limits, permissions, and ordering.

`hasAdminSettings: false` removes its configuration tab and settings shortcuts and blocks configuration writes. Empty settings schemas also omit the Settings tab. Host controls for activation, roles, search, data retention, and removal remain under **Manage** in the plugin catalogue; these are distinct from the plugin's own settings. Bundled calendar providers appear in a Calendars admin group, Git providers in Development, chat providers in Communications, and Microsoft file providers in Storage & sharing.

## Screen contributions

The host renders named slots, not arbitrary DOM injection. Current slots include `header.actions`, `navigation.links`, `account.menu`, `calendar.event.details`, and `page.<area>.before` / `page.<area>.after` for Home, Storage, Projects, Documents, Workflows, Dashboards, Search, Events, Admin, Account, and plugin pages. Slots have lowercase route-derived names, for example `page.projects.after` and `page.account.after`.

`LINK` is the default contribution mode. `PANEL` embeds a ZIP page in the existing sandbox or loads a reviewed bundled frontend component. Compact header/navigation/account slots accept links only. Submenu contributions must reference a declared section and respect its roles. Only enabled, compatible, permitted plugins contribute.

For bundled code, add a loader to `WorkspacePluginModule.loadExtensions[slot]`. The component receives `{plugin, slot, context}`; the context contains routing information and any explicit data supplied by that slot (event ID/source for the event inspector). Its APIs must authorize IDs again. ZIP panels receive their normal scoped SDK and managed data access; `context().placement` names the slot and `hostContext` contains its explicit routing/event hints. The SDK's `placement`/`returnTo` values are routing hints, never proof of authorization; verified client/project contexts retain their dedicated checks.

These mechanisms extend the app across screens. Uploaded ZIPs can also provide [Python/Kotlin backend code](backend-runtime.md) in isolated workers with declared routes and grants. Trusted source modules can additionally register in-process APIs, notification adapters, calendar providers, and client/project panels. ZIP code cannot replace arbitrary core business logic, modify another plugin's tables or bypass RBAC. A new inline location or typed business hook needs an explicit host slot/SPI. See [context extensions](project-contexts.md) and [notification adapters](notifications.md).

## Removal and restoration

Super admins can remove a plugin after typing its ID. Removing a ZIP deletes the installed package; re-upload it to reinstall. Removing a bundled module disables/hides it and marks it removed; its compiled code remains in the image and **Show removed bundled plugins → Restore** reinstates it disabled.

Removal retains business tables and encrypted configuration. **Clear data** remains a separate explicit operation to empty declared tables before removal. Reinstalling a ZIP can reuse retained fixed-schema tables; settings are left disabled until configured. Provider-side messages/files/calendar copies are unaffected. Back up PostgreSQL and the wrapping key before deleting business data.
