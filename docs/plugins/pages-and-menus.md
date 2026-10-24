# Pages and submenus

Add `frontend/src/plugins/modules/<id>/index.ts`:

```ts
import type { WorkspacePluginModule } from '../../registry'
export default {
  id: 'tasks', apiVersion: 1,
  loadPage: () => import('./TasksPage'),
} satisfies WorkspacePluginModule
```

Vite discovers compiled descriptors. The server supplies labels, roles, and submenu metadata; it cannot supply executable JavaScript. A matching local descriptor/API version is required before a page appears.

Pages mount at `/plugins/:pluginId/*`. Add `PluginMenuItem("boards", "Boards")` to the backend manifest for `/plugins/tasks/boards`. Submenu paths are single lowercase segments, with no URLs, slashes, or traversal. Each item can restrict `roles` to `ADMIN` and/or `USER`. The host hides disallowed links and rejects direct page navigation to undeclared/disallowed sections. The root page remains the plugin's landing page. Sections can own deeper routes under their declared first segment.

Read the wildcard with React Router `useParams()` or render nested routes inside your page. Use query parameters for record links; Kanban uses `/plugins/kanban/boards?board=<uuid>`. Provide an unknown/deleted-record state. Private React Query cache keys must include the account ID. Clear private cached state on logout.

`createPluginApi(id)` in `frontend/src/plugins/sdk.ts` supplies authenticated scoped GET/POST/PUT requests. Backend controllers still enforce access; menu roles are presentation rules and do not replace API authorization. API-only plugins need no frontend descriptor. Administration itself continues to use section cards, with no admin submenu tree.
