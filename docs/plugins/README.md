# Creating a plugin

Start from `backend/src/main/kotlin/com/starter/plugins/kanban/` and `frontend/src/plugins/modules/kanban/`. Plugins are trusted source modules included in both application images. Review them as application code: dependencies, SQL migrations, secrets, ownership, and outbound requests all need review.

1. Add a Spring `@Component` implementing `WorkspacePlugin` under `com.starter.plugins.<module>`. Declare a unique lowercase ID of 2–40 characters, a `major.minor.patch` version, and host `apiVersion = 1`.
2. Declare extension types: `PAGE`, `API`, `DATABASE`, `SETTINGS`, `NOTIFICATIONS`, and/or `SEARCH`. Add submenu definitions and owned table declarations as needed. `defaultRoles` may restrict initial access within `supportedRoles` (both must retain `ADMIN`).
3. Put controller routes under `/api/plugins/<id>/...`. The host interceptor rechecks account activation, plugin enabled state, and allowed roles. Services must call `PluginRegistry.requireTable` before database reads/writes and enforce record ownership. Background jobs need the same checks.
4. Add JPA entities/repositories in the plugin package and a reviewed migration. Declare all owned tables in the manifest; declarations register ownership/policy, they do not execute arbitrary DDL. Prefix tables `plugin_<id_with_underscores>_`. The existing Spring/JPA scanning includes this package.
5. Add the frontend module descriptor and lazy page. See [Pages and submenus](pages-and-menus.md).
6. Declare configuration fields and validators for the shared [Settings menu](settings.md). Add [notification methods and types](notifications.md) when the plugin delivers messages. Optionally implement a `PluginSearchProvider`; see [Search](search.md).
7. Test disabled/restricted states, direct API requests, ownership, read-only roles, search exclusion, stale writes, and clear-data rollback. Run `mvn test` using Java 21 in `backend/` and `npm run build` in `frontend/`.
8. Rebuild and deploy backend/frontend, then open its **Settings** in **Administration → Plugins** and enable it under **General**. New modules do not seed business records.

```kotlin
@Component
class TasksPlugin : WorkspacePlugin {
    override val manifest = PluginManifest(
        "tasks", "Tasks", "1.0.0", "Task management",
        extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE),
        menuItems = listOf(PluginMenuItem("boards", "Boards")),
        tables = listOf(PluginTable("plugin_tasks_boards", "Task boards")))
}
```

Plugin settings and content belong in PostgreSQL backups; the metadata JSON export omits them. Disabling or removing code does not drop tables. Keep required migrations while upgrading old installations. Do not store credentials in manifests or browser bundles; use the server's existing secret encryption facilities.

Kanban has saved-board and guide submenus. Boards support up to 20 lists and 200 cards, with UUID identifiers, validation, owner access, and optimistic revisions (`409` for stale saves). Team sharing, assignments, attachments, and workflow execution hooks are not implemented by this example. MongoDB is unchanged.

Separate [integration plugins](integrations.md) provide Telegram, Teams, WhatsApp Business, SharePoint, and OneDrive connections. See [Settings](settings.md) to add encrypted plugin-specific configuration without a custom frontend form.

[Source-control plugins](source-control.md) add GitHub, GitLab, and Gitea repository/PR links to boards and individual cards. [Calendar](calendar.md) adds local events, Kanban deadlines, and separate Microsoft, Google, and CalDAV connections. Use [plugin composition](composition.md) to declare capabilities, dependencies, and optional cross-plugin features. Every settings menu includes a Connections tab; disabled dependencies retain data and block dependent operations.

## Licensing modules

The public host and original bundled plugins use AGPL-3.0-only, with a separate commercial licensing path. Compiled-in modules can form a combined work; a manifest or directory boundary does not automatically permit a proprietary plugin. Review [licensing](../../LICENSING.md) and dependency terms before distribution. Core/community contributions need explicit [CLA acceptance](../../CLA.md) before merging; this preserves public AGPL rights and permits future commercial sublicensing.
