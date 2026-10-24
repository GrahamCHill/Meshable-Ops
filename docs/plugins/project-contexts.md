# Extending clients and projects

A manifest may declare `contextTargets: ["PROJECT", "CLIENT"]` (either or both). A PAGE extension and an accessible plugin page are required. The host adds an **Extensions** section to matching contexts and returns only enabled plugins available to the caller. The project/client API authorizes the context before returning a link or computed panel.

## Compiled modules

Implement the Spring bean contract `WorkspaceContextProvider` from `com.starter.projects`:

```kotlin
class DeliveryPanel : WorkspaceContextProvider {
    override val pluginId = "delivery"
    override fun panels(context: WorkspaceContext, actor: User): List<ContextPanel> {
        // Recheck module table permissions and authorize any additional records.
        return listOf(ContextPanel("Delivery", "A bounded, computed summary"))
    }
}
```

The host passes the authorized context kind, UUID, and name. Up to 20 plain-text panels are returned per plugin. Add separate typed endpoints/tables for richer functionality, with project membership plus module table/row checks. Do not treat URL parameters as authorization. The `ProjectAccess` and `ProjectBoardAccess` services supply current project/team checks.

## Runtime ZIP pages

Add `contextTargets` to `plugin.json`. Pages are linked with `?project=<uuid>` or `?client=<uuid>`. Use the ready host SDK:

```javascript
const context = await window.meshableOps.context();
// context.project or context.client: authorized core view, or null
```

The host verifies the ID through the protected core API before releasing context. SDK navigation preserves context parameters. `window.weaveops` remains an alias for older packages; the bridge protocol stays compatible. Context does not expose project members, secrets, arbitrary APIs, or direct database access. ZIP managed records retain their declared role/owner rules; context alone does not share them between project members. Use reviewed compiled code for custom multi-user relational behavior.

## Contracts and lifecycle

`GET /api/projects/{id}/extensions` and `/api/projects/clients/{id}/extensions` return the current authorized plugin links/panels. Changing access, disabling the plugin, or disabling third-party extensions removes runtime availability without deleting business data. Tags are classification; only explicit membership/team grants confer access.

Kanban implements the project connection using `kanban.boards`; Git links use `source.repositories`; calendar deadlines read only visible boards. Messaging adapters are a separate composition contract. See [composition](composition.md), [ZIP packaging](zip-packages.md), and [notifications](notifications.md).
