# Code organization

Extension contracts, discovery, access gates, and Kanban live in `backend/src/main/kotlin/com/starter/plugins/` and `frontend/src/plugins/`. Plugin pages/APIs use module discovery. See [Workspace plugins](plugins.md) for contracts, layout, checks, and trust boundaries.

`plugins/packages/` separates bounded ZIP validation, persistent installation/security, runtime page delivery, managed record access, and local search. `frontend/src/plugins/runtime/` contains the sandbox document, message-channel bridge, and frame lifecycle. Admin section groups share one navigation catalogue; personal MFA remains independent of global extension security. See [ZIP package authoring](plugins/zip-packages.md).

`plugins/sourcecontrol/` contains provider manifests, repository allowlists, and GitHub/GitLab/Gitea metadata adapters. `plugins/kanban/KanbanDevelopment*` owns board/card links. `plugins/calendar/` separates local events, provider contracts, Microsoft/Google/CalDAV adapters, and the bounded iCalendar codec. The frontend calendar and board link editors are separate module files; generic capability resolution stays in `PluginRegistry`. See [composition](plugins/composition.md) for adding connections.

Branding uses `BrandingAsset`, `BrandingPreferencesRecord`, and `BrandingLoginImage` with PostgreSQL repositories. `service/branding/` separates bounded raster processing, preferences/validation, background persistence, and the `WorkspaceBrandingService` facade; `BrandingService` keeps existing logo/favicon persistence. Public/admin controllers share the resulting metadata. The frontend separates the branding API, `Brand`/`BrandingHead`, `LoginBackground` (motion/timers/fallbacks), and `admin/branding/` controls composed by `AdminBrandingPage`. Scoped layouts live in `styles/branding.css`. The default logo/favicon is `frontend/public/brand-mark.svg`.

GitHub workflows live in `.github/workflows/`; `checks.yml` is reused by deployment and template releases. Host setup, effective Compose validation, SSH transfer, and release activation are split under `deploy/github/`. Run its Python tests, `check-config.py`, `bash -n` on the scripts, and actionlint when changing deployment. See [template deployment](github-deployment.md).

Meshable Ops is the product name. Existing `com.starter` package paths, Docker storage names, development buckets, and the versioned backup format remain stable so the rebrand does not change persisted data or integration contracts.

Files are grouped by feature and responsibility. Page components compose the screen; API modules own requests and response types; hooks own stateful workflows. Related components sit together so a change to one feature does not require editing a general application file.

## Frontend

| Location | Responsibility |
| --- | --- |
| `frontend/src/api/http.ts` | Shared Axios transport, token injection, and expired-session handling. |
| `frontend/src/api/{auth,users,items,settings,storageConnections,ai,dashboards,superset,events,search,system,audit}.ts` | Feature requests and their request/response contracts. Import the relevant module directly. |
| `frontend/src/api/types.ts` | Types shared across API domains, such as pagination and storage provider names. |
| `frontend/src/api/access.ts`, `components/layout/AccessBoundary.tsx`, `components/admin/AccessStoragePicker.tsx` | Effective permissions, route gating, and actual S3/Blob path selection. |
| `frontend/src/components/pages/` | Route-level screen composition and navigation. |
| `frontend/src/components/storage/` | Folder metadata form, document browser, and file listing. |
| `frontend/src/components/admin/storage/` | Connection form/cards, folder selection and access controls, default settings, and search-index settings. |
| `frontend/src/components/admin/ai/` and `frontend/src/components/search/` | Provider configuration fields, source links, search matches, and sourced answers. |
| `frontend/src/components/search/SearchLauncher.tsx` and `SearchPanel.tsx` | Optional floating AI chat and dedicated search UI; `StorageSearch.tsx` provides scoped ordinary search directly in Storage. |
| `frontend/src/components/admin/storage/ReindexScheduleSettings.tsx` | Calendar, interval, and changed-file indexing schedule controls. |
| `frontend/src/components/admin/useCredentialRequest.ts` | Write-only credential requests that avoid the shared mutation cache and retain safe error messages. |
| `frontend/src/components/admin/storage/useStorageSettings.ts` | Default-connection draft, validation, saving, connection tests, and reindex mutations. It stays mounted when switching storage tabs to preserve unsaved changes. |
| `frontend/src/hooks/useDashboardEmbed.ts` | Superset SDK lifecycle, guest tokens, loading/error state, observers, and cleanup. |
| `frontend/src/lib/superset/` | Iframe sizing, restricted embed actions, and iframe navigation rules. |
| `frontend/src/lib/supersetTheme.ts` | Theme applied inside Superset frames. |
| `frontend/src/styles/` | Theme tokens, base elements, shared components, shell layout, and feature styles. `global.css` imports them in order. |

Add feature-specific API calls and types beside their existing domain. Keep reusable UI in `components/ui`; keep components that only make sense for storage inside the storage folders. Preserve query keys when moving components so cache invalidation continues to work.

## Backend

`config/AccountBootstrapConfig.kt` creates only explicitly configured initial accounts and preserves existing accounts. `BootstrapProperties` contains no demo identities. The single PostgreSQL initializer handles database/extensions; application DDL belongs to JPA and `schema.sql`. See [initialization and security](initialization-and-security.md).

| Location | Responsibility |
| --- | --- |
| `backend/src/main/kotlin/com/starter/access/` | Role hierarchy/migration, feature catalogue, policy persistence, API gates, canonical storage ACLs, and search SQL predicates. |
| `backend/src/main/kotlin/com/starter/controller/` | One controller per HTTP resource. Request mappings and security annotations live with the controller. |
| `backend/src/main/kotlin/com/starter/dto/` | Request/response contracts grouped by domain. Generic message and pagination contracts are in `CommonDtos.kt`. |
| `backend/src/main/kotlin/com/starter/model/postgres/` | One JPA entity per file. |
| `backend/src/main/kotlin/com/starter/repository/postgres/` | One repository per entity. |
| `backend/src/main/kotlin/com/starter/service/FolderStorageService.kt` | Folder access boundaries, managed roots, upload/download orchestration, and ZIP archives. |
| `backend/src/main/kotlin/com/starter/service/storage/` | Storage models, adapter contract/factory, shared path helpers, and separate S3 and Azure Blob implementations. |
| `backend/src/main/kotlin/com/starter/ai/` | Provider configuration, protocol adapters, embedding validation, document indexing, extraction requests, and grounded answers. |
| `backend/src/main/kotlin/com/starter/ai/LocalTextVectors.kt` | Default deterministic local matcher, independent of provider APIs and the ML helper. |
| `backend/src/main/kotlin/com/starter/service/{SearchService,KeywordSearchService,ReindexSchedule,VectorReindexScheduler}.kt` | Search availability/fallback, access-checked keywords, timezone-aware schedule calculation, and persisted indexing jobs. |
| `backend/src/main/kotlin/com/starter/security/` | Credential encryption/conversion/migration and password encoding/validation. |
| `ml-backend/app/document_text.py` | Isolated, bounded text/PDF/DOCX extraction. |

Provider implementations handle cloud operations. Keep application visibility and folder-access decisions in the services that call them. Storage internals are grouped under `com.starter.service.storage`. New MFA, webhook, and sharing domain controllers/DTOs/entities/repositories are grouped in their own packages; the JPA repository scan explicitly includes these packages and the existing PostgreSQL repositories. MongoDB repository scanning is unchanged.

## Checks after a refactor

Run `npm run build` from `frontend/` and `mvn test` from `backend/` with Java 21. When changing extraction, install `ml-backend/requirements.txt` in a virtual environment and run `python -m unittest discover -s tests -v` from `ml-backend/`.

Check the relevant UI flow after implementation changes: connection draft persistence across tabs, folder browsing/revocation, dashboard switching/reload/expand, and return navigation. Search changes should cover the absent launcher with AI off, hidden/disabled switches, optional sourced answers, and keyword fallback. Schedule changes should cover weekday/timezone calculations, repeated-change coalescing, saved due state, and disabled/manual jobs. Unit/provider mocks and fixture UI checks complement, rather than replace, a live deployment check.

For documentation-only changes, validate relative links, headings/fences, API paths, and configuration names against the checked-in sources. See the [documentation index](README.md) and [API reference](api.md).

PDF delivery policy lives in `DocumentAccessService`/`DocumentDeliveryService`; bounded raster rendering and watermarking live in `PdfService`. The frontend separates `AdminDocumentAccessPage`, `PdfViewer`, and the document-access API/hook. Tests cover direct-route restrictions, policy/access changes during processing, ZIP transforms, multiple/rotated PDF pages, and unsupported input.

Notification delivery and plugin method discovery live in `notification/`: method contracts/transports, typed catalogue, settings/preferences, HTML renderer, metadata-only delivery claims, shared publisher, and workflow after-commit hooks. `AdminNotificationsPage` composes separate rule, message, method, history, and sending components; `NotificationPreferencesPage` is owner-scoped. See [notifications](notifications.md) and [plugin messaging](plugins/notifications.md).

Optional mail lives in `backend/src/main/kotlin/com/starter/email/`: configuration/validation, encrypted settings selection, a reusable optional delivery service, and SMTP/API adapters. `AdminEmailController` owns the admin endpoints. Frontend files are `api/email.ts`, `AdminEmailPage.tsx`, `admin/email/EmailTransportForm.tsx`, and `EmailSendPanel.tsx`. Email tests use loopback SMTP/HTTP fixtures and check STARTTLS refusal, provider payloads/redirects, credential redaction/binding, disabled behavior, and admin authorization. [Email setup](email-delivery.md) describes integration and operational limits.

MFA lives in `mfa/`: credentials/challenges, TOTP/email/recovery enrollment and verification, the email configuration guard, and HTTP contracts. Shared security helpers provide TOTP, secure random values, rate limits, and JWT security-version enforcement. The frontend separates `api/mfa.ts`, the login challenge form, and `AccountSecurityPage` with locally generated QR codes.

Webhooks live in `webhook/`: encrypted endpoints/deliveries, signature verification, a DNS-validating HTTP client, and admin/public controllers. UI work is split between `AdminWebhooksPage`, `WebhookHistory`, and `api/webhooks.ts`. Protected PDF sharing lives in `share/`: settings, live storage checks, metadata/access grants, service orchestration, and admin/public controllers. The frontend has `SharePdfDialog`, `AdminSharesPage`, `SharedFilePage`, and `api/shares.ts`. `PdfService` generates watermarked encrypted copies; `EmailAttachment` is internal to mail delivery and not a public upload contract. `notification/NotificationService` is the future channel boundary; only EMAIL is implemented.

Security tests cover RFC TOTP vectors/replay, session invalidation, no session before MFA, verified email enrollment, one-use recovery, email disable guards, webhook signing/timestamp/redirect/network boundaries, protected PDF passwords/watermarks, sharing access/revocation/source fingerprints, SMTP/API attachments, and admin authorization. Local mocks do not replace PostgreSQL/cloud/provider integration verification.

Document generation is a separate `documents/` domain: typed contracts/entities/repositories, field validation/decimal calculations, template publishing, access checks, a renderer client, and generated PDFs and the shared `GeneratedDocumentDelivery` policy/attachment pipeline. `workflow/` holds admin authoring and draft/ready validation, owner-private run persistence/access, typed server step/condition evaluation, and durable email claim/completion handling. Runs snapshot a design and expose only current/reached data. Both packages are included in PostgreSQL repository scanning; MongoDB is unchanged. Library queries project metadata without fetching PDF blobs; document references are checked with ownership counts.

The frontend keeps vendor source in `vendor/weasycanvas/{editor,src}` and scoped designer CSS. Host screens are `AdminTemplatesPage`, `DocumentsPage`, `AdminWorkflowsPage` (visual builder), and `WorkflowsPage` (guided runner), lazy loaded so the designer/React Flow stay outside the user runner bundle. `components/documents/` contains a blank template factory, typed field rules/forms, preview, and email dialogs; `components/workflows/` contains block rendering/inspector plus separate run panel, step form, document actions, and explicit email step. APIs live in `api/documents.ts` and `api/workflows.ts`. Template editor and workflow CSS remain scoped.

`weasy-renderer/backend/` contains the matching WeasyCanvas schema, HTML template, and helpers. `app.py` exposes internal bounded validation/rendering; `worker.py` normalizes template content, blocks external resources, and renders in a limited subprocess. Run its Python tests separately with Pango installed. See [renderer setup](../weasy-renderer/README.md) and [feature scope](documents-and-workflows.md). No source `.env`, local state, demo persistence, node_modules, or Python environment is copied from the source project.
