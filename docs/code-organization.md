# Code organization

Files are grouped by feature and responsibility. Page components compose the screen; API modules own requests and response types; hooks own stateful workflows. Related components sit together so a change to one feature does not require editing a general application file.

## Frontend

| Location | Responsibility |
| --- | --- |
| `frontend/src/api/http.ts` | Shared Axios transport, token injection, and expired-session handling. |
| `frontend/src/api/{auth,users,items,settings,storageConnections,ai,dashboards,superset,events,search,system,audit}.ts` | Feature requests and their request/response contracts. Import the relevant module directly. |
| `frontend/src/api/types.ts` | Types shared across API domains, such as pagination and storage provider names. |
| `frontend/src/components/pages/` | Route-level screen composition and navigation. |
| `frontend/src/components/storage/` | Folder metadata form, document browser, and file listing. |
| `frontend/src/components/admin/storage/` | Connection form/cards, folder selection and access controls, default settings, and search-index settings. |
| `frontend/src/components/admin/ai/` and `frontend/src/components/search/` | Provider configuration fields, source links, search matches, and sourced answers. |
| `frontend/src/components/search/SearchLauncher.tsx` and `SearchPanel.tsx` | Floating chat/search launcher and reusable search UI; the full `/search` page uses the same panel. |
| `frontend/src/components/admin/storage/ReindexScheduleSettings.tsx` | Calendar, interval, and changed-file indexing schedule controls. |
| `frontend/src/components/admin/storage/useStorageSettings.ts` | Default-connection draft, validation, saving, connection tests, and reindex mutations. It stays mounted when switching storage tabs to preserve unsaved changes. |
| `frontend/src/hooks/useDashboardEmbed.ts` | Superset SDK lifecycle, guest tokens, loading/error state, observers, and cleanup. |
| `frontend/src/lib/superset/` | Iframe sizing, restricted embed actions, and iframe navigation rules. |
| `frontend/src/lib/supersetTheme.ts` | Theme applied inside Superset frames. |
| `frontend/src/styles/` | Theme tokens, base elements, shared components, shell layout, and feature styles. `global.css` imports them in order. |

Add feature-specific API calls and types beside their existing domain. Keep reusable UI in `components/ui`; keep components that only make sense for storage inside the storage folders. Preserve query keys when moving components so cache invalidation continues to work.

## Backend

| Location | Responsibility |
| --- | --- |
| `backend/src/main/kotlin/com/starter/controller/` | One controller per HTTP resource. Request mappings and security annotations live with the controller. |
| `backend/src/main/kotlin/com/starter/dto/` | Request/response contracts grouped by domain. Generic message and pagination contracts are in `CommonDtos.kt`. |
| `backend/src/main/kotlin/com/starter/model/postgres/` | One JPA entity per file. |
| `backend/src/main/kotlin/com/starter/repository/postgres/` | One repository per entity. |
| `backend/src/main/kotlin/com/starter/service/FolderStorageService.kt` | Folder access boundaries, managed roots, upload/download orchestration, and ZIP archives. |
| `backend/src/main/kotlin/com/starter/service/storage/` | Storage models, adapter contract/factory, shared path helpers, and separate S3 and Azure Blob implementations. |
| `backend/src/main/kotlin/com/starter/ai/` | Provider configuration, protocol adapters, embedding validation, document indexing, extraction requests, and grounded answers. |
| `backend/src/main/kotlin/com/starter/service/{SearchService,KeywordSearchService,ReindexSchedule,VectorReindexScheduler}.kt` | Search availability/fallback, access-checked keywords, timezone-aware schedule calculation, and persisted indexing jobs. |
| `backend/src/main/kotlin/com/starter/security/` | Credential encryption/conversion/migration and password encoding/validation. |
| `ml-backend/app/document_text.py` | Isolated, bounded text/PDF/DOCX extraction. |

Provider implementations handle cloud operations. Keep application visibility and folder-access decisions in the services that call them. Controller, DTO, entity, and repository package names remain unchanged; storage internals are grouped under `com.starter.service.storage`.

## Checks after a refactor

Run `npm run build` from `frontend/` and `mvn test` from `backend/` with Java 21. When changing storage or embedding, also check the relevant UI flow: connection draft persistence across tabs, folder browsing, dashboard switching, reload/expand, and return navigation. These checks complement the backend access-control and provider regression tests.
