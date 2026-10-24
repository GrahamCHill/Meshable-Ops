# Code organization

`plugins/operations/` contains the optional finance/time/on-call suite. `OperationsPlugins` declares manifests; `OperationsStore` owns the fixed-table persistence/lock boundary; `OperationsAccess` and `TimeVisibility` enforce project, ownership and senior-member rules. Services are split into time imports/ledger, rates/items, cloud providers, billing profiles/generation/documents/delivery/inbox, schedules and on-call/webhook handling. `OperationsControllers` exposes typed routes. The generic webhook receiver supports reviewed `IncomingWebhookExtension` consumers only after normal endpoint authentication. Source claims, timers and jobs use PostgreSQL locks and optimistic revisions rather than browser-only guards.

The lazy React modules live under `plugins/modules/{timesheets,activitywatch,timecamp,cloud-costs,billing-items,billing,on-call}`; shared screens are split under `plugins/operations/` with separate billing profile, schedule and document/reply components. Python `weasy-renderer/plugins/billing` renders structured, escaped multi-page PDFs under the existing bounded child-process runtime. See [Operations plugins](plugins/operations.md) for the data flow and current provider limits.

The admin event viewer is `components/pages/AdminEventsPage.tsx`, with `api/events.ts` and scoped `styles/events.css`. `EventController` gates both the cursor and legacy page APIs; `EventHistoryService` performs read-only, timestamp/ID keyset queries over the existing event collection. The stable `events` feature is now admin-only at `/admin/events`, and `NavigationCatalogue` omits it from workspace links. See [event history and access](event-log.md).

`navigation/` owns the declared menu catalogue, filtered member response, optimistic global ordering and registered category artwork controllers. `WorkspaceArtworkService` shares bounded raster normalization while project/admin controllers enforce their own permissions. The frontend separates `WorkspaceNavigation`, `NavigationToggle`, `AdminNavigationPage`, reusable `ArtworkPicker` and project cards/artwork. `projects/ProjectRepositoryLinks` enforces membership/manage rights independently of Kanban. Git provider discovery lives in `sourcecontrol/`; Google Drive has its own `googledrive/` service/controller and lazy frontend folder browser.

Extension contracts, discovery, access gates, and Kanban live in `backend/src/main/kotlin/com/starter/plugins/` and `frontend/src/plugins/`. Plugin pages/APIs use module discovery. See [Workspace plugins](plugins.md) for contracts, layout, checks, and trust boundaries.

Documents and Workflows are separately optional source modules under `backend/.../plugins/{documents,workflows}/` and `frontend/src/plugins/modules/{documents,workflows}/`. Each frontend module owns its pages, API contracts and components. Compiled route descriptors retain first-class URLs/admin cards; `BundledModuleGuard` protects the legacy API roots and `BundledModuleAccess` applies service/table policy. Notification definitions and workflow alerts belong to the modules. `BundledModuleMigration` preserves existing use without reseeding records or overwriting settings. The Documents Python contribution lives in `weasy-renderer/plugins/documents/`, explicitly registered by `plugins/registry.py`. Shared `rendering/WeasyRendererClient` and the renderer host retain independent project reporting. See [full-stack plugins](plugins/full-stack.md) and [module lifecycle](plugins/documents-and-workflows.md).

`plugins/packages/` separates bounded ZIP validation, persistent installation/security, frontend asset inlining, runtime pages, managed records, and local search. Its `backend/` package owns route authorization, short-lived invocation scopes, worker transport and permissioned host callbacks. `plugin-runtime/` contains the private Docker control plane, worker bootstrap, and Python/Kotlin SDKs; uploaded code never executes in the application/control-plane process. `frontend/src/plugins/runtime/` contains the sandbox document, message-channel bridge, and frame lifecycle. Admin section groups share one navigation catalogue; personal MFA remains independent of global extension security. See [ZIP package authoring](plugins/zip-packages.md) and [backend runtime](plugins/backend-runtime.md).

`plugins/sourcecontrol/` contains provider manifests, repository allowlists, and GitHub/GitLab/Gitea metadata adapters. `plugins/kanban/KanbanDevelopment*` owns board/card links. `plugins/calendar/` separates local events, provider contracts, Microsoft/Google/CalDAV adapters, and the bounded iCalendar codec. The frontend calendar and board link editors are separate module files; generic capability resolution stays in `PluginRegistry`. See [composition](plugins/composition.md) for adding connections.

Branding uses `BrandingAsset`, `BrandingPreferencesRecord`, and `BrandingLoginImage` with PostgreSQL repositories. `service/branding/` separates bounded raster processing, preferences/validation, background persistence, and the `WorkspaceBrandingService` facade; `BrandingService` keeps existing logo/favicon persistence. Public/admin controllers share the resulting metadata. The frontend separates the branding API, `Brand`/`BrandingHead`, `LoginBackground` (motion/timers/fallbacks), and `admin/branding/` controls composed by `AdminBrandingPage`. Scoped layouts live in `styles/branding.css`. The default logo/favicon is `frontend/public/brand-mark.svg`.

GitHub workflows live in `.github/workflows/`; `checks.yml` is reused by deployment and template releases. Host setup, effective Compose validation, SSH transfer, and release activation are split under `deploy/github/`. Run its Python tests, `check-config.py`, `bash -n` on the scripts, and actionlint when changing deployment. See [template deployment](github-deployment.md).

Meshable Ops is the product name. Existing `com.starter` package paths, Docker storage names, development buckets, and the versioned backup format remain stable so the rebrand does not change persisted data or integration contracts.

Files are grouped by feature and responsibility. Page components compose the screen; API modules own requests and response types; hooks own stateful workflows. Related components sit together so a change to one feature does not require editing a general application file.

`ClientLogoController` applies client read/manage checks before delegating bounded image handling to `WorkspaceArtworkService`. `ClientEditor`, `ClientLogo` and `ClientDetailPage` share client editing, private artwork and readable contact details. `ProjectCreationRules` checks global creation roles and existing explicit project/team leadership; both project/client creation and board creation enforce it.

`ProjectSections` groups card grids by configured order. `ProjectSectionsSettings` edits section names/order/default; `ProjectSectionRules` validates IDs/names and resolves display names. Existing project category strings retain section assignments across upgrades.

`backend/.../projects/` separates models, project/team access and SQL predicates, client/project validation, extension contexts, work aggregation, PDF reporting, digests, and opt-in schedules. `frontend/src/api/projects.ts` supplies typed contracts; `components/projects/` contains reusable forms/reports/digest/schedule panels; route pages compose these. Kanban splits card fields, sprint fields, project/team selection, comments, and development links into module components.

Kanban's `KanbanRules` owns saved-state movement policies and epic/tag/subtask validation; both whole-board writes and locked move commands use it. Frontend board configuration, column/epic settings, ticket tiles/dialogs, filters and summaries are separate module files. `ProjectWorkOverview`/`WorkCharts` render permission-scoped aggregates. `usePageDraft` retains dialog fields only within a page/account; `useModalDialog` supplies safe backdrop dismissal. `PluginActivationService` applies revision-checked activation batches in one transaction while preserving other plugin settings.

`plugins/integrations/MemberMessaging*` separates provider identity mapping, private delivery, and explicit shared posts. `MemberIdentityEntities` supplies fixed JPA schemas; credentials remain in encrypted plugin configuration. `weasy-renderer/report.py` renders escaped structured project reports independently of authored document templates. Python's complete service roles are documented in [architecture](architecture.md#why-python-is-present).

`components/ui/TimeZoneSelect.tsx` and `lib/timezones.ts` share grouped, searchable timezone choices across member profiles, work schedules/digests, and indexing. API values remain IANA identifiers; labels use familiar names and current offsets. `ProjectDetails.contactSource` and `ProjectView.effectiveContacts` centralize client inheritance versus project replacement without a global fallback.

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

Webhooks live in `webhook/`: encrypted endpoints/deliveries, signature verification, a DNS-validating HTTP client, and admin/public controllers. UI work is split between `AdminWebhooksPage`, `WebhookHistory`, and `api/webhooks.ts`. Protected PDF sharing lives in `share/`: settings, live storage checks, metadata/access grants, service orchestration, and admin/public controllers. The frontend has `SharePdfDialog`, `AdminSharesPage`, `SharedFilePage`, and `api/shares.ts`. `PdfService` generates watermarked encrypted copies; `EmailAttachment` is internal to mail delivery and not a public upload contract. `notification/NotificationService` exposes delivery through the host transport registry and enabled plugin adapters; protected PDF attachments still use the email delivery pipeline.

Security tests cover RFC TOTP vectors/replay, session invalidation, no session before MFA, verified email enrollment, one-use recovery, email disable guards, webhook signing/timestamp/redirect/network boundaries, protected PDF passwords/watermarks, sharing access/revocation/source fingerprints, SMTP/API attachments, and admin authorization. Local mocks do not replace PostgreSQL/cloud/provider integration verification.

Document generation lives in the optional `plugins/documents/` module: typed contracts/entities/repositories, field validation/decimal calculations, template publishing, access checks, a renderer client, and generated PDFs and the shared `GeneratedDocumentDelivery` policy/attachment pipeline. `plugins/workflows/` holds admin authoring and draft/ready validation, owner-private run persistence/access, typed server step/condition evaluation, and durable email claim/completion handling. Runs snapshot a design and expose only current/reached data. Both modules use the existing `com.starter.plugins` repository scan; MongoDB is unchanged. Library queries project metadata without fetching PDF blobs; document references are checked with ownership counts.

The frontend keeps vendor source in `vendor/weasycanvas/{editor,src}` and scoped designer CSS. Plugin screens are `AdminTemplatesPage`, `DocumentsPage`, `AdminWorkflowsPage` (visual builder), and `WorkflowsPage` (guided runner), lazy loaded so the designer/React Flow stay outside the user runner bundle. `plugins/modules/documents/components/` contains a blank template factory, typed field rules/forms, preview, and email dialogs; `plugins/modules/workflows/components/` contains block rendering/inspector plus separate run panel, step form, document actions, and explicit email step. Each module owns its `api.ts`. Template editor/workflow CSS remains scoped; the shared storage/PDF reader styles stay in `styles/pdf.css`.

`weasy-renderer/backend/` contains the matching WeasyCanvas schema, HTML template, and helpers. `plugins/documents/` owns template validation, normalization and rendering; `app.py` provides bounded authenticated requests, `worker.py` dispatches reviewed operations in a limited subprocess, and `resources.py` applies shared PDF limits and blocks external resources. Run its Python tests separately with Pango installed. See [renderer setup](../weasy-renderer/README.md) and [feature scope](documents-and-workflows.md). No source `.env`, local state, demo persistence, node_modules, or Python environment is copied from the source project.

The shell is composed in `components/layout/AppLayout.tsx` and `UserMenu.tsx`; `UserSettingsPage` combines owner-scoped profile, security/MFA, and notification preferences. `api/profile.ts` calls `AccountProfileController`/`AccountProfiles`, with `UserProfile` and its repository storing private images and timezone preferences.

Plugin presentation validation/defaults live in `PluginManifests`/`PluginPresentation`; `PluginArtwork` and `PluginLifecycle` handle authenticated assets and installation removal. `plugins/ApplicationExtensions.tsx` renders declared host slots using compiled lazy components or sandboxed runtime pages. `components/admin/useAdminGroups.ts` merges plugin settings into admin categories. Calendar interaction is split between `CalendarGrid`, `calendarLayout`, `EventEditor`, and `CalendarPage`. See [presentation contracts](plugins/presentation-and-lifecycle.md) for current slots and limits.

`CalendarEventDetails` validates bounded document references and participant metadata, stored alongside calendar rows without granting document access or sending invitations. `EventInformation` renders the shared inspector/agenda detail view; `EventDetailsFields` handles local metadata editing. Provider parsers expose supported incoming fields; personal appointment sync retains its existing invited/recurring-event limits.

`PluginVersionRules` parses bounded version declarations and SemVer precedence; `PluginVersionPolicy` produces advisory warnings against `ApplicationVersion`, loaded from Maven build information. `PluginCompatibility` renders installation/catalogue warnings. PostgreSQL-backed package/configuration/settings storage is retained during app updates; the registry detects newly bundled ID collisions instead of silently replacing installed descriptors. `deploy/github/check-release-version.py` keeps template release labels aligned with frontend/backend package versions.
