# Optional search participation

Declare `SEARCH` and `DATABASE`, then implement a Spring bean `PluginSearchProvider` with `pluginId`, `tableName`, and `search(query, limit, actor)`. The provider's table must be declared in that plugin's manifest. Only one provider per plugin/table is accepted.

The host calls providers only when the module is enabled, allowed for the current role, included in search, and its table readable for that role. It rechecks activation/roles/search settings before releasing results. **Providers must enforce row ownership/ACLs in SQL before ranking and LIMIT.** Never fetch other users' private rows and rely on the browser to filter them. Use parameterized SQL.

Return `VectorSearchPoint` values with stable namespaced IDs and payload fields `entityType=plugin`, `pluginId`, `title`, `text`, `source`, and an internal `href` beneath `/plugins/<id>/`. Optional `matchMode` describes provider ranking. Do not return secrets or credential fields. The frontend links results to their plugin pages.

Kanban indexes validated board/list/card text on each save in the same PostgreSQL transaction. Its `search_embedding` is a 384-dimensional local feature-hash vector: it needs no AI key or external model. PostgreSQL ranks with cosine similarity and full-text matching, restricting rows to their owner or an administrator. Legacy boards without vectors still have keyword matching over their saved name/content; saving them builds their vector. Its search implementation lives in `KanbanSearchProvider.kt`; SQL additions live in `schema.sql`.

**Include in workspace search** starts off for every plugin. Turning it off or disabling the plugin immediately excludes its records from new server search responses while retaining saved data/vectors. Global search (`/search`) merges allowed plugin results with storage results. Folder-scoped storage-browser search remains confined to that folder. AI chat continues to retrieve storage document excerpts only; enabling plugin search does not send plugin content to a chat provider. MongoDB, events, and audit logs remain excluded.

For new providers, implement updates/deletes and schema migration together so derived vectors follow their source data. The host clear-data action empties all declared tables; declare separate derived-index tables too, in dependency order. No generic workflow/event or scheduled indexing hook is supplied for arbitrary providers in API version 1.
