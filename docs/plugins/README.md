# Creating a plugin

For a minimal uploadable frontend + backend example, use [custom-page](../../examples/plugins/custom-page/README.md): run its `package.py`, then install the resulting ZIP. It includes a custom page, guide submenu, settings, author file, and Python route. For React plus Python/Kotlin, use the [full-stack example](../../examples/plugins/full-stack/README.md).

See [Kanban 1.2.0](kanban.md) for columns, transition permissions, draggable tickets, epics, tags, project charts and bulk plugin activation.

See [presentation, admin categories, screen contributions, and removal](presentation-and-lifecycle.md) for icons/banners, categories, required/optional plugin relationships, author-defined incompatibility, optional settings, and extension slots.

Declare [compatible application versions](compatibility-and-updates.md) with `compatibleVersions`. Exact releases, lists, and ranges are supported. Missing/out-of-range declarations show caution and backup advice without blocking use. Installed ZIPs and their saved state persist across application updates in the same database.

Choose between [runtime ZIP packages](zip-packages.md) for optional frontend pages and/or uploaded Python/Kotlin backend code, managed data/settings/search, and compiled source modules for in-process integrations and typed host hooks. [Backend-only ZIPs](backend-runtime.md) need no page. Both models need source and permission review. ZIP installation requires the super admin's third-party extensions security switch.

Source plugins support **React, Kotlin and Python** contributions. Start from the [full-stack authoring guide](full-stack.md) and the [Documents/Workflows extraction](documents-and-workflows.md), or use `backend/src/main/kotlin/com/starter/plugins/kanban/` and `frontend/src/plugins/modules/kanban/` for a React/Kotlin example. Source modules are built into application/worker images; runtime ZIPs load optional browser bundles and isolated Python/Kotlin backend code from their uploaded artifacts. Review dependencies, SQL migrations, secrets, ownership, and outbound requests as application code.

1. Add a Spring `@Component` implementing `WorkspacePlugin` under `com.starter.plugins.<module>`. Declare a unique lowercase ID of 2–40 characters, a SemVer version (for example `1.2.0` or `1.0.0-a`), and host `apiVersion = 1`.
2. Declare extension types: `PAGE`, `API`, `DATABASE`, `SETTINGS`, `NOTIFICATIONS`, and/or `SEARCH`. Add submenu definitions and owned table declarations as needed. `defaultRoles` may restrict initial access within `supportedRoles` (both must retain `ADMIN`).
3. Put controller routes under `/api/plugins/<id>/...`. The host interceptor rechecks account activation, plugin enabled state, and allowed roles. Services must call `PluginRegistry.requireTable` before database reads/writes and enforce record ownership. Background jobs need the same checks.
4. Add JPA entities/repositories in the plugin package and a reviewed migration. Declare all owned tables in the manifest; declarations register ownership/policy, they do not execute arbitrary DDL. Prefix tables `plugin_<id_with_underscores>_`. The existing Spring/JPA scanning includes this package.
5. Add the frontend module descriptor and lazy page. See [Pages and submenus](pages-and-menus.md).
6. Declare configuration fields and validators for the shared [Settings menu](settings.md). Add [notification methods and types](notifications.md) when the plugin delivers messages. Optionally implement a `PluginSearchProvider`; see [Search](search.md).
7. Test disabled/restricted states, direct API requests, ownership, read-only roles, search exclusion, stale writes, and clear-data rollback. Run `mvn test` using Java 21 in `backend/` and `npm run build` in `frontend/`.
8. Rebuild and deploy backend/frontend, then open its **Manage** view in **Administration → Plugins** and enable it under **General**. Expose editable configuration through an admin category when appropriate; modules without editable options can omit Settings. New modules do not seed business records.

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

Kanban has saved-board and guide submenus. Boards support up to 20 lists and 200 cards, with private ownership or project/team access, assignments/reviewers, sprints, dates, story points, stable references, comments/mentions, and optimistic revisions (`409` for stale saves). Projects link existing storage files/folders rather than copying originals into cards. See [projects and delivery](../projects-and-delivery.md). MongoDB is unchanged.

Separate [integration plugins](integrations.md) provide Discord, Telegram, Teams, WhatsApp Business, SharePoint, and OneDrive connections. See [Settings](settings.md) for encrypted configuration and [client/project contexts](project-contexts.md) for extending business records.

[Google Drive](google-drive.md) is an independent folder-browser plugin with root-scoped metadata access and pagination.

[Source-control plugins](source-control.md) add GitHub, GitLab, and Gitea repository/PR links to boards and individual cards. [Calendar](calendar.md) adds local events, Kanban deadlines, and separate Microsoft, Google, and CalDAV connections. Use [plugin composition](composition.md) to declare capabilities, dependencies, and optional cross-plugin features. Plugins with relationships have a Connections tab; disabled dependencies retain data and block dependent operations.

## Licensing modules

Plugins that form a covered extension or combined work inherit the app's **AGPL-3.0-only** community licensing. Original plugin functionality accepted into this repository follows that license. Independently authored service-integration components may retain compatible licenses, while independent external services retain their own terms. The covered combination remains subject to AGPL; language, ZIP packaging or process boundaries do not automatically create an exemption. See the [licensing policy](../../LICENSING.md#plugins-and-external-service-integrations) and [plugin examples](licensing.md). The commercial alternative requires an executed agreement covering the relevant rights. Core/community contributions still need explicit [CLA acceptance](../../CLA.md) before merging.
