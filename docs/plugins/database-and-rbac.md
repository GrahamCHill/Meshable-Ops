# PostgreSQL tables and RBAC

Declare tables with `PluginTable(name, label, readRoles, writeRoles)` in a `DATABASE` manifest. Names must be SQL identifiers of at most 63 characters under `plugin_<id_with_underscores>_`. Duplicate names and invalid role policies fail at startup. Create schema using reviewed JPA entities and migrations; do not accept DDL from the browser.

Call the host before every table operation:

```kotlin
val actor = actors.current(email) // active account and current database role
registry.requireTable("tasks", "plugin_tasks_boards", actor.role) // read
registry.requireTable("tasks", "plugin_tasks_boards", actor.role, write = true)
// Restrict repository queries to actor.id unless actor.role == "ADMIN".
```

The host checks enabled state, plugin role, and the saved table policy. Administrators retain read/write access; writers must also be readers. In admin, members can be denied, read-only, or read/write for each table. Kanban enforces these checks in its service and ownership in its queries. Ownership is independent of a role's table access.

These are **application RBAC checks**, not PostgreSQL per-user roles or row-level-security policies. The application's pooled database connection is shared. A trusted plugin can bypass its host by issuing raw SQL, so declarations are not a sandbox. Plugin authors must use the guards and test every service path, including jobs and search providers.

`plugin_settings` persists activation, allowed roles, `search_enabled`, and `table_access_json`. Missing rows mean disabled and excluded from search; declared default table policies apply. PUT settings is a full replacement:

```json
{
  "enabled": true,
  "allowedRoles": ["ADMIN", "USER"],
  "searchEnabled": false,
  "tableAccess": {
    "plugin_tasks_boards": {"readRoles": ["ADMIN", "USER"], "writeRoles": ["ADMIN"]}
  }
}
```

## Clearing data

**Administration → Plugins → Clear plugin data** lists the declared tables and requires the exact plugin ID. The server accepts no table names or SQL from the request. It locks all declared tables, then deletes their rows in manifest order in one transaction. Declare dependent/child tables before parents. No `CASCADE`, table drops, sequence resets, core-table deletions, or settings resets are requested. Foreign-key failures roll back all deletes. Review triggers and FK cascades when authoring migrations; do not create plugin-to-core deletion cascades.

The action is permanent: take a database backup first. It is available even for a disabled plugin so an administrator can retire its data. Declared tables must exist. Remove data deliberately before removing source if it is no longer needed; removing source alone retains tables.

Kanban declares `plugin_kanban_development_links` before `plugin_kanban_boards`; links reference their board and are removed with it. Deleting a card removes its card-specific links during the board save transaction. Calendar separately declares `plugin_calendar_events`; its virtual Kanban deadlines remain owned by boards and are not cleared with calendar events. Shared provider credentials live in the host configuration table and are retained by either clear operation.

Calendar also declares `plugin_calendar_sync_links` before its event/connection parents and `plugin_calendar_connections` for encrypted per-member credentials. These personal connections are owned plugin data and are removed by Calendar clear-data, while shared administrator provider configuration in the host table remains. Imported personal events use an owner-only policy even for other app administrators; sync always selects the connected member's own records. The sync ledger retains remote revisions and local tombstones to reconcile updates/deletions without treating truncated windows as deletion.
