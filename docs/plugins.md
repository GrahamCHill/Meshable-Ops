# Workspace plugins

Plugins add reviewed functionality to the application: pages, submenus, APIs, PostgreSQL tables, and optional workspace search. The included Kanban module is a working example. Start with [Creating a plugin](plugins/README.md).

In **Administration → Plugins**, open a module's **Settings** to enable it, configure its connection, choose who can open it, set table read/write roles, and choose whether it appears in search. New plugins start disabled and excluded from search. Disabling blocks APIs and hides pages while retaining data.

**Clear plugin data** permanently empties only the module's declared tables after typing its ID. It retains schema and settings. Back up PostgreSQL before using it.

## API

| Endpoint | Purpose |
| --- | --- |
| `GET /api/plugins` | Enabled modules allowed for the current active account, including submenu and table policies |
| `GET /api/admin/plugins` | Installed manifests and settings; admin only |
| `PUT /api/admin/plugins/{id}` | Save `enabled`, `allowedRoles`, `searchEnabled`, and `tableAccess`; admin only |
| `POST /api/admin/plugins/{id}/clear-data` | `{ "confirmation": "<id>" }`; admin only, returns `deletedRows` per table |
| `GET /api/plugins/kanban/boards` | Up to 100 most recently updated owner-visible board summaries; admins see all |
| `GET /api/plugins/kanban/boards/{id}` | Full board; table read role and owner/admin |
| `POST /api/plugins/kanban/boards` | Create using `name`, `revision`, and `content`; table write role |
| `PUT /api/plugins/kanban/boards/{id}` | Save with current `revision`; table write role and owner/admin |

Separate connection plugins support Telegram, Teams Workflows, WhatsApp Business, SharePoint, and OneDrive for Business. See [Integration setup](plugins/integrations.md) and the universal [Settings menu](plugins/settings.md).

Additional [GitHub/GitLab/Gitea plugins](plugins/source-control.md) link repositories and PRs/MRs to Kanban boards/cards. [Calendar](plugins/calendar.md) provides local events, board deadlines, ICS export, and optional Microsoft, Google, and CalDAV providers. [Composition](plugins/composition.md) defines required capabilities and optional connections. The user catalogue returns effective active modules; the admin catalogue includes enabled-but-waiting modules and their missing dependencies.

See [Database and RBAC](plugins/database-and-rbac.md), [Pages and submenus](plugins/pages-and-menus.md), and [Search](plugins/search.md). Bundled plugins are compiled source with application privileges. Super admins can also install [ZIP plugins](plugins/zip-packages.md) dynamically after enabling third-party extensions in Security. ZIPs require author metadata, run pages in isolated frames, and use host-managed tables/settings/search; they cannot execute server code or raw SQL.
