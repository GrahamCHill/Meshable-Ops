# Storage search, AI chat, and indexing jobs

## Feature controls

**Storage** has an ordinary search field, independent of AI answers. At the root it searches all accessible shared folders; inside a folder it searches that folder and its current subfolder tree. It uses the selected embedding model and cosine similarity, with access-checked keyword fallback when embeddings are unavailable or the index is empty. The default local matcher is deterministic text matching; configure external embeddings for semantic model embeddings.

Enabling AI answers does not change the Storage search path. Ordinary file browser search (`POST /api/search`) never invokes the chat model for query rewriting, ranking, or answer generation. It may call the separately selected embedding model to generate a query vector; the internal matcher needs no model or external API call. The chatbot uses the separate `POST /api/search/ask` endpoint for generated answers.

**Administration > AI & search** controls these features independently:

| Control | Default | Effect |
| --- | --- | --- |
| Enable similarity search in Storage | On | Shows storage search fields; off hides them and rejects search/chat requests with HTTP 403 |
| Show bottom-right AI chat launcher | On | Shows the floating assistant when search is enabled and the member has search access; it offers search and setup guidance until AI answers are ready |
| Enable AI answers | Off | Shows optional AI controls and allows answer generation; off leaves ordinary search available in Storage and the assistant |
| Index document content | Off | Enables bounded extraction/chunks; off excludes stored chunks and AI answers immediately, retaining metadata search in Storage and the assistant |

These are application-wide settings. `ADMIN` can change them; environment values are read-only until **Admin settings** is selected. UI controls refresh status about every 30 seconds; saving in the current browser updates them immediately. Backend checks always use current settings. The dedicated `/search` route remains available only when search is enabled.

The chat launcher sits outside the left navigation. Close it with **Close**, the launcher button, or Escape. Its draft/results remain while the mounted panel is closed; switching off the launcher or search, or revoking search access, unmounts the panel. AI readiness changes leave the assistant available: it offers ordinary search with setup guidance until document answers are configured. Chat history is not persisted. Source links resolve the authenticated Storage route. Chat provider fields and tests are hidden in admin while AI is off; embedding controls remain available when ordinary search is on.

Environment switches are `APP_SEARCH_ENABLED=true`, `APP_SEARCH_LAUNCHER_ENABLED=true`, and `AI_ENABLED=false`. No AI account, model download, API key, or ML container is needed for the default local text matcher. Document text extraction remains a separate optional ML service; if it is down, browsing and metadata indexing continue. Existing indexed text remains searchable. Events and audit logs are outside search; Events and audit history remain excluded from document search.

Local feature hashing runs inside the Kotlin backend as `local-feature-hash-v3`, using 1536 dimensions. It is deterministic text matching, not a semantic language-model embedding. Reindex once when upgrading from the former Python matcher or the 384-dimensional `local-feature-hash-v2` index. Startup migrates existing vector columns and rebuilds local plugin vectors automatically. Until storage reindexing completes, keyword search can use the current metadata and existing content chunks. External model identities remain distinct: the app never substitutes local vectors into an external model's index.

If a configured embedding service is unavailable or not fully configured, regular search returns access-checked keyword matches with an explanatory notice. An unavailable chat provider returns guidance and accessible source excerpts, without inventing an answer. Disabling search is enforced by the backend even when a browser has stale settings.

## Indexing schedule

Open **Administration > Storage connections > Search index**, or use **Manage indexing jobs and schedules** on the AI settings page. Enable scheduled indexing, choose a schedule, and save:

- **Every day**: a chosen local time, such as `00:00`.
- **Every week**: a weekday and local time, such as Friday at `00:00`.
- **At the next scheduled time when files change**: poll visible storage each minute and queue one run at the next chosen time. Set `00:00` for the next midnight after detection. Repeated detections preserve the pending deadline.
- **At a fixed interval**: the existing 15-minute to 7-day interval.

The timezone dropdown shows familiar names, city, and current UTC offset, with common choices first and a name/city filter under **Find a timezone**. It stores an explicit IANA identifier, initially `Africa/Johannesburg`. Calendar schedules respect daylight-saving changes. The admin page shows the next run in the saved timezone, last result, and whether changes are queued. Uploads/browsing also queue detected changes, even if their initial indexing already completed.

Schedule configuration, pending state, and the next run are saved in PostgreSQL and survive backend restarts. A previously queued overdue job runs once after the application starts; the next calendar occurrence is then calculated. New changes first discovered after startup queue the next future time. Failed/partial runs are reported and retried at the next scheduled occurrence. The server must remain running to detect changes and execute jobs; this is an application job, not an operating-system scheduler. This scheduler assumes one backend instance.

Change detection compares selected folder paths and each object's name, content type, size, and modification time. It detects additions, modifications, and removals; providers that change bytes without updating metadata require a manual reindex. Jobs only inspect accessible folders on enabled storage connections. **Reindex now** remains available when scheduled jobs are disabled.

| Desired job | Run choice | Day/time/timezone |
| --- | --- | --- |
| Every local midnight | Every day | `00:00`, `Africa/Johannesburg` |
| Friday midnight | Every week | `FRIDAY`, `00:00`, `Africa/Johannesburg` |
| Next midnight after a file change | At the next scheduled time when files change | `00:00`, `Africa/Johannesburg` |
| Every two hours | At a fixed interval | `120` minutes |

Save the job with **Save settings**; changing controls alone does not change the active schedule. All day/time/zone fields are validated. The scheduler polls approximately once per minute after the previous check finishes, so due jobs are not exact-second alarms. A scheduled full reindex is distinct from the immediate metadata/content synchronization performed during browsing and uploads.

## API and failure behavior

`POST /api/search` accepts optional `folderId` and relative folder `path` to scope a search. A path requires a folder ID. The backend validates folder visibility and path traversal before embedding, and both SQL paths apply a literal directory prefix before ranking and limiting. `%` and `_` in folder names are treated literally.

`POST /api/search` returns `{ results, mode, notice }`, where `mode` is `VECTOR` or `KEYWORD`. Storage and content results obey the same live visibility checks in both paths.

`GET /api/search/status` reports configured feature state, not a live provider-health probe. `POST /api/search/ask` returns `grounded: false` guidance if chat is absent/unavailable or no relevant excerpts exist. An explicit search disable returns 403, and access revoked during generation also returns 403. PostgreSQL remains a required application dependency; keyword fallback is not a replacement database. See [API contracts](api.md#search-and-answers).

## Command-line reindex helpers

Run these from the repository root against the intended installation.

Reindex stored vectors:

```bash
./scripts/reindex-items.sh
```

On Windows PowerShell:

```powershell
./scripts/reindex-items.ps1
```

Both reindex helpers:

- log in as an app admin
- call the existing `/api/items/reindex` endpoint
- support `APP_URL`, `APP_ADMIN_EMAIL`, and `APP_ADMIN_PASSWORD`

## Plugin records

Global workspace search can include records from installed plugins. Administrators must enable each plugin and select **Include in workspace search**. Providers enforce table roles and record ownership; changing those controls excludes results immediately. Kanban uses local PostgreSQL vectors and full-text matching without AI credentials. Folder-scoped storage searches and AI document answers remain storage-only. See [Plugin search](plugins/search.md).
