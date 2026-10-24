# Navigation and section icons

The header keeps the workspace logo in a fixed position. Its account button shows an avatar and dropdown arrow; the open menu shows the member's name and account actions. The sidebar's collapse/expand button floats at its edge on desktop and at the bottom of the screen on mobile. Collapse preference is local to the signed-in member/browser. Administration remains visible beneath the scrollable list of permitted links.

Open **Administration → Workspace → Navigation & section icons** (`/admin/navigation`). The **Menu ordering** tab orders core links, active plugin pages, navigation contributions, and declared plugin submenus using accessible up/down buttons. Save applies the order to the workspace. Permissions continue to filter each member's links; ordering never grants access. Disabled/removed plugin positions are retained for reinstall, and new items follow the saved items. Restore default order prepares a reset for saving. Concurrent changes return `409`; reload before editing again.

The **Section icons** tab supplies artwork for registered administration categories, including plugin categories. Choose a built-in icon or upload WebP, PNG, or JPEG up to 1 MiB and 2048 × 2048 pixels; a square **128 × 128 px** input is recommended. Images are decoded and normalized to a transparent 64 × 64 PNG. Original metadata and animation are discarded; SVG and external URLs are rejected. Saving a preset removes the uploaded image. Built-in categories retain their default labels and icons unless an administrator changes the artwork.

Plugin authors may declare `adminMenu.icon` using a built-in name: `folder`, `briefcase`, `code`, `building`, `layers`, `rocket`, `target`, `chart`, `globe`, `calendar`, `users`, `shield`, `settings`, `plug`, `database`, `mail`, `workflow`, `search`, `file`, `layout`, `cloud`, `lock`, or `activity`. Workspace overrides take priority. Categories sharing an ID share artwork; choose unique category IDs for independent sections.

Changes require the `admin.navigation` feature and an administrator account. Administrators with access to other sections can read section artwork without permission to edit navigation. Ordinary members cannot read admin artwork endpoints. Project artwork has its own project read/manage checks; see [projects](projects-and-delivery.md).

Navigation and image records persist in PostgreSQL and survive image upgrades. Include `workspace_navigation_settings` and `workspace_artwork` in database backups; the metadata JSON export omits them. Images are served through authenticated endpoints with `no-store` and `nosniff` headers. The app checks access again when loading a route or API.

## API

- `GET /api/navigation` returns only the caller's visible items and applicable order.
- `GET /api/admin/navigation` returns the active ordering catalogue (saved hidden positions remain in preferences); `PUT` accepts `{order, submenus, revision}`.
- `GET /api/admin/navigation/sections` lists registered categories, defaults, and saved artwork.
- `PUT /api/admin/navigation/sections/{id}` accepts `{icon, revision}`.
- `POST /api/admin/navigation/sections/{id}/image` accepts multipart `file` and `revision`.
- `GET /api/navigation/admin-sections` returns category artwork for permitted administrators. Versioned image routes append `/{id}/image/{version}`.

Unknown destinations and duplicate IDs are rejected. These APIs configure presentation, not arbitrary routes, CSS, or permission rules.

### Disabled plugins

Menu ordering lists only active plugins and their navigation extensions, including submenu ordering controls. Disabling a plugin hides those entries without discarding its saved position. Re-enabling it restores the position. Saving another item's order keeps hidden positions reserved. Activation/removal updates both member navigation and the ordering editor; open editors also refresh periodically. Restore default order explicitly resets the saved layout.
