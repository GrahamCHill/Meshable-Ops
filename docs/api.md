# Application API reference

Optional modules use the authenticated `/api/plugins` catalogue, administrator-only `/api/admin/plugins` settings, and guarded `/api/plugins/{id}/…` routes. See [Workspace plugins](plugins.md#api) for contracts and Kanban endpoints, [plugin settings](plugins/settings.md) for redacted configuration APIs, and [integration plugins](plugins/integrations.md) for messaging/file-listing endpoints.

[Source-control APIs](plugins/source-control.md#api) link approved repository/PR metadata to boards/cards. [Calendar APIs](plugins/calendar.md#api-and-extension-points) manage local events, export ICS, read selected providers, and explicitly publish saved events. [Capability dependencies](plugins/composition.md) are enforced per role, including direct API calls.



Browser requests use the application origin's `/api` prefix through the frontend proxy. Authenticate through the app; the backend accepts its bearer token or app auth cookie. The cookie is also used by guarded Superset authoring routes. Search and storage endpoints require authentication; admin endpoints additionally require `ADMIN`. Login/logout and public health checks are separate existing flows.



This is a reference for the current storage/search/admin contracts. [Controllers](../backend/src/main/kotlin/com/starter/controller/) and [DTOs](../backend/src/main/kotlin/com/starter/dto/) remain the source of truth. No public API indexes MongoDB collections, events, audit logs, or OCR text.



## Search and answers



| Method and path | Request | Response |

| --- | --- | --- |

| `GET /api/search/status` | None | Effective feature state and embedding provider/dimensions; no keys |

| `POST /api/search` | `{ "query": "contract renewal", "limit": 10 }` | `{ results, mode, notice }` |

| `POST /api/search/ask` | `{ "query": "When does the contract renew?", "limit": 5 }` | `{ answer, sources, model, grounded }` |



Queries must be nonblank and at most 4000 characters. Search limit is 1–50, default 10; answer-source limit is 1–8, default 5. The floating panel initially requests five results.



Example status shape:



```json

{

  "enabled": false,

  "indexContent": false,

  "embeddingProvider": "LOCAL",

  "dimensions": 384,

  "searchEnabled": true,

  "launcherEnabled": true

}

```



`enabled` is calculated AI configuration state (search/answer flags and a nonblank chat model), not a live provider-health result. The UI also requires `indexContent` for its Ask AI button. `launcherEnabled` is false whenever search is disabled, even if the saved launcher checkbox is on.



Each search result has:



```json

{

  "pointId": "opaque-index-point-id",

  "score": 0.72,

  "payload": {

    "entityType": "document",

    "folderId": "folder-uuid",

    "documentId": "document-uuid",

    "title": "Contract.pdf",

    "storagePath": "contracts/contract.pdf"

  }

}

```



`results` may contain `folder`, `document`, or `chunk` entries. Chunk payloads additionally include `text` and `chunkIndex`. Payload values are strings; optional fields depend on the entry type. `mode` is `VECTOR` or `KEYWORD`. Keyword fallback includes a `notice`; a healthy empty result can have no notice. Scores express relevance within the returned mode, not factual confidence or comparable probabilities between modes.



Answers contain numbered sources with `number`, `pointId`, `documentId`, `folderId`, `title`, `path`, `excerpt`, and `score`. The UI opens their regular authenticated Storage route. When AI is absent/unavailable, content indexing is off, or no relevant excerpts exist, the API returns guidance with `grounded: false`; `model` may be null and sources may be empty. A chat-provider outage can retain accessible source excerpts. The service does not manufacture an answer from filenames.



Disabling search returns 403 for both POST endpoints. Revoking document access during generation also returns 403. Vector and keyword queries enforce live selection/connection visibility. PostgreSQL remains required; an embedding-provider fallback is not a database failover.



## Storage access



| Method and path | Purpose/access |

| --- | --- |

| `GET /api/storage-connections` | List enabled named connections with accessible-folder counts; no credentials |

| `GET /api/items?page=0&size=20` | List accessible application-managed roots on the default connection |

| `GET /api/items?connectionId=<uuid>` | List accessible selected roots for that connection |

| `GET /api/items/{id}` | Resolve one accessible root, including source-link navigation |

| `GET /api/items/{id}/contents?path=<relative-path>` | Browse virtual folders/objects and current processing status |

| `GET /api/items/{id}/download?path=<relative-path>&archive=false` | Download an object; `archive=true` downloads a folder ZIP |

| `POST /api/items/{id}/upload?path=<relative-path>` | Multipart `files` and matching `paths` arrays; requires the upload grant |

| `POST /api/items` | Create an application-managed folder; admin only |

| `PUT /api/items/{id}` | Update folder metadata; admin only |

| `DELETE /api/items/{id}` | Remove metadata/empty managed root according to storage rules; admin only |

| `POST /api/items/reindex` | Full reindex of visible roots, metadata, and enabled content; admin only |



Folder create/update uses `title`, optional `description`, and `tags`. Relative paths cannot escape the selected prefix. Hidden folders/disabled connections are denied even for a known ID. Cloud write permissions must also permit uploads. Removing an external selection does not delete cloud objects. Reindex returns `{ "message": "..." }` with folder/document/content/error counts and runs synchronously; clients should allow sufficient time.



## Admin storage connections



| Method and path | Purpose |

| --- | --- |

| `GET/POST /api/admin/storage-connections` | List or create named connections |

| `PUT/DELETE /api/admin/storage-connections/{id}` | Save or remove a connection; remove selections first before deleting/repointing |

| `POST /api/admin/storage-connections/{id}/test` | Check the saved connection |

| `GET /api/admin/storage-connections/{id}/browse?path=<relative-path>` | Admin-only folder discovery within its base path |

| `POST /api/admin/storage-connections/{id}/folders` | Select a folder using `path`, `nickname`, and `accessible` |

| `GET /api/admin/storage-folders` | List legacy application-managed folder publication settings |

| `PATCH /api/admin/storage-folders/{id}` | Update `nickname` and `accessible` for a selected/managed root |

| `DELETE /api/admin/storage-folders/{id}` | Remove an external selection; managed roots use visibility instead |



A connection request uses `nickname`, `provider` (`S3` or `AZURE_BLOB`), `bucket` (Azure container name), optional `region`/`endpoint`, `basePath`, `enabled`, `useServerIdentity`, `accessKey`, and `secretKey`. Set `useServerIdentity: true` explicitly for workload identity. With stored credentials selected, blank credential fields can retain keys for the same provider/endpoint; replacing an access/account key requires the new secret as well. Location changes are blocked while folders remain selected.



Responses contain `accessKeyConfigured` and `secretConfigured`, never saved keys. Creating a connection shares no folders automatically; overlapping parent/child selections are rejected.



## Default storage and indexing jobs



| Method and path | Purpose |

| --- | --- |

| `GET /api/admin/settings` | Read redacted default storage and job configuration/status |

| `PUT /api/admin/settings` | Save the full default storage/job request |

| `POST /api/admin/settings/test-storage` | Validate/test draft connection values without saving |



This PUT is not a schedule-only PATCH. Read current settings, construct the corresponding full request, and preserve the existing storage and job fields when editing one section. Omitting fields can apply their DTO defaults. Response-only fields (IDs, configured flags, timestamps, pending/next-run state) are not request inputs.



Storage request fields are `storageProvider`, `storageBucket`, `storageRegion`, `storageEndpoint`, `storageRootPath`, optional write-only `storageAccessKey`/`storageSecretKey`, and `useServerIdentity`. Set `useServerIdentity: false` with blanks to preserve configured keys only for the same provider/bucket/endpoint. Set it true to clear stored keys and use the server identity.



The job fields below are a fragment to merge into a complete request, not a standalone PUT body:



```json

{

  "vectorReindexEnabled": true,

  "vectorReindexIntervalMinutes": 1440,

  "vectorReindexSchedule": "WEEKLY",

  "vectorReindexTime": "00:00",

  "vectorReindexDayOfWeek": "FRIDAY",

  "vectorReindexTimeZone": "Africa/Johannesburg"

}

```



Modes are `INTERVAL`, `DAILY`, `WEEKLY`, and `ON_CHANGE`. Interval must be 15–10080 minutes; time is valid `HH:mm`, day is an uppercase weekday, and timezone is a valid IANA name. The response additionally exposes `vectorReindexNextRunAt`, `vectorReindexPendingChanges`, `vectorReindexLastCheckAt`, `lastVectorReindexAt`, and `lastVectorReindexStatus`. Due/last-run values are serialized instants. See [job semantics](search-and-indexing.md#indexing-schedule).



## Admin AI and feature controls



| Method and path | Purpose |

| --- | --- |

| `GET /api/admin/ai` | Read `{ source, encryptionReady, admin, environment }`; both views are redacted |

| `PUT /api/admin/ai` | Save the source and, for `ADMIN`, its complete separate configuration |

| `POST /api/admin/ai/test/chat` | Test saved/active chat using harmless fixed text |

| `POST /api/admin/ai/test/embeddings` | Test saved/active embeddings using harmless fixed text |



The request contains `source` (`ENVIRONMENT` or `ADMIN`), `searchEnabled`, `launcherEnabled`, `enabled` (AI answers), `indexContent`, and `chat`/`embeddings`. Each endpoint input has `provider`, `baseUrl`, `model`, optional write-only `apiKey`, `clearApiKey`, and `sendDimensions`.



Supported provider identifiers are `OPENAI_COMPATIBLE`, `OLLAMA`, `AZURE_OPENAI`, `ANTHROPIC`, and `LOCAL`. Chat cannot use `LOCAL`; embeddings cannot use `ANTHROPIC`. Groq uses the OpenAI-compatible adapter. LOCAL needs no endpoint or key. External embeddings must return 384 finite, nonzero dimensions; `sendDimensions` controls the optional dimensions request parameter for providers that support it.



Each returned configuration includes its flags and redacted endpoints, with `apiKeyConfigured` instead of `apiKey`. A blank key preserves one only for the same provider/base URL; `clearApiKey: true` removes it. Sources do not share keys. Saved credentials need `APP_SECRETS_ENCRYPTION_KEY`; an absent key produces a safe validation error, not plaintext persistence. Provider URLs reject embedded credentials/query parameters/fragments and require HTTPS for keys except loopback tests. See [configuration/security details](ai-search-and-secrets.md).



## Errors and related APIs



Validation errors use HTTP 400 (field-error maps or a safe message). Explicit permission/search-disable errors use 403; missing/hidden storage roots use 404. Login credential failures use 401. AI provider-test failures use safe 400/503 responses as appropriate; raw upstream error bodies are not exposed. Database/infrastructure failures are distinct from a normal unavailable-AI guidance response.



Related existing endpoints include `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/session-cookie`, `GET /api/health`, `GET /api/superset/dashboard-embeds`, admin dashboard-link routes, `GET /api/events`, and admin audit/export routes. Registration is invite-only and rejects self-service requests. Event/audit viewing and admin JSON export remain separate from search.



## PDF viewing and download policy



| Method and path | Purpose |

| --- | --- |

| `GET /api/document-access` | Authenticated effective `{ pdfPreviewEnabled, downloadMode, watermarkText }` |

| `GET /api/admin/document-access` | Admin policy configuration |

| `PUT /api/admin/document-access` | Admin saves all three policy fields |

| `GET /api/items/{id}/pdf?path=<relative-file>` | PDF preview `{ filename, pageCount }` |

| `GET /api/items/{id}/pdf/pages/{page}?path=<relative-file>` | One-based page as `image/png`; original PDF is never sent for preview |



Download modes are `NORMAL`, `WATERMARKED`, and `DISABLED`. Watermark text must be 1–100 characters on a single line. PDF viewing is independent of downloads. Existing `/items/{id}/download` checks this policy for both files and archives; disabled downloads return 403. Watermark mode stamps PDFs, including those in folder ZIPs, and preserves other formats. Detection uses filename, content type, or a PDF header; renaming a PDF does not bypass watermarking. Failed transformation aborts delivery, never returns an unstamped original. The user and UTC timestamp come from the server.



PDF processing accepts unencrypted PDFs up to 25 MB and 200 pages. Invalid pages/files or processing saturation return safe 400 errors. Preview metadata, page images, and downloads use `Cache-Control: no-store`. Live folder visibility and current policy are checked again before delivery. [Document access details](document-access.md).



Search requests also accept optional `folderId` and `path` (a relative subfolder). A nonempty path requires a folder. Both vector and keyword fallback queries enforce this scope before ranking/limiting and retain live sharing checks.

## Email delivery

All email endpoints require the existing `ADMIN` role.

| Method and path | Purpose |
| --- | --- |
| `GET /api/admin/email` | `{ source, encryptionReady, admin, environment }`, with redacted credentials |
| `PUT /api/admin/email` | Save source and, for `ADMIN`, its complete separate configuration |
| `POST /api/admin/email/test` | Send fixed plain-text test content to `{ "recipient": "reader@example.com" }` using saved settings |
| `POST /api/admin/email/send` | Send `{ "recipient", "subject", "text" }` using saved settings |

The settings request contains `source` (`ENVIRONMENT` or `ADMIN`), `enabled`, `provider` (`SMTP`, `RESEND`, `SENDGRID`, `MAILGUN`), `fromAddress`, `fromName`, `replyTo`, `smtpHost`, `smtpPort`, `smtpSecurity` (`STARTTLS`, `TLS`, `NONE`), `smtpAuth` (`PASSWORD`, `OAUTH2`, `NONE`), optional write-only `smtpUsername`/`smtpPassword`, `clearSmtpCredentials`, `apiBaseUrl`, optional write-only `apiKey`, `clearApiKey`, and `mailgunDomain`. A source-only `{ "source": "ENVIRONMENT" }` PUT switches to the environment without copying credentials. For `ADMIN`, send the complete configuration; omitted fields use DTO defaults.

Responses replace credentials with `smtpUsernameConfigured`, `smtpPasswordConfigured`, and `apiKeyConfigured`. Blank secrets retain values only for the same destination/account; explicit clear flags take precedence. All admin credentials require the server wrapping key. Authenticated SMTP requires TLS, API URLs require HTTPS except loopback tests, and provider redirects are rejected. Enabled configurations must include valid sender and required credentials. See [email configuration](email-delivery.md).

Test/send return `{ "status": "ACCEPTED", "message": "..." }` after provider acceptance, which is not confirmed inbox delivery. Delivery disabled returns 403; unavailable/rejected provider or saturated workers return safe 503 errors. Validation/header injection errors return 400. Each send accepts one mailbox, a 1–200 character single-line subject, and 1–20000 characters of plain text. No attachments or message history are stored by this API. Sending is synchronous, without automatic retries.

## Authentication and MFA

`POST /api/auth/login` still accepts `{ email, password }`. Without MFA it returns the session response, now including `suggestMfaSetup`. With MFA it returns `{ mfaRequired: true, challengeToken, methods, expiresAt }`, clears the auth cookie, and returns no session token. Methods are `TOTP`, `EMAIL`, or `RECOVERY`; email is listed only when enrolled and configured. An outage never changes this into a password-only success.

| Method and path | Request / response |
| --- | --- |
| `POST /api/auth/mfa/email` | Public `{ challengeToken }`, sends a code for a valid login challenge to the saved account email |
| `POST /api/auth/mfa/verify` | Public `{ challengeToken, method, code }`, returns a session only after successful factor verification |
| `GET /api/account/mfa` | Authenticated `{ totpEnabled, emailEnabled, emailAvailable, encryptionReady, recoveryCodesRemaining, setupRecommended }` |
| `POST /api/account/mfa/dismiss` | Dismiss optional primary-admin setup recommendation |
| `POST /api/account/mfa/setup/{kind}` | `kind`: `totp`, `email`, or `manage-email`; `{ password, method?, code?, challengeToken? }` |
| `POST /api/account/mfa/confirm` | `{ challengeToken, method, code }`; returns `{ auth, recoveryCodes }` |
| `POST /api/account/mfa/disable` | `{ password, method, code, challengeToken?, target }`; target `TOTP`, `EMAIL`, or `ALL`; returns `{ auth, recoveryCodes: [] }` |
| `POST /api/account/mfa/recovery-codes` | Same proof fields (target ignored); returns fresh `{ auth, recoveryCodes }` |

Setup returns `{ challengeToken, expiresAt, secret?, otpauthUri? }`; the secret/URI occur only for TOTP enrollment. When adding another factor, proof of an existing factor is required. `manage-email` sends a password-authorized email challenge usable as existing-factor proof. It cannot itself enable email MFA. New factors remain inactive until confirmation. Enrollment and recovery regeneration return new recovery codes once; replace the current JWT with `auth.token` after any security change. Old sessions are invalidated. Invalid/expired/replayed codes return 401; validation/unavailable setup returns 400, rate limits 429 with `Retry-After`, and delivery failures 503. See [MFA behavior and recovery](mfa.md).

## Webhooks

Admin endpoints require `ADMIN`; incoming endpoint authentication is independent of application login.

| Method and path | Purpose |
| --- | --- |
| `GET /api/admin/webhooks` | Redacted endpoint list |
| `POST /api/admin/webhooks` | Create `{ name, direction, enabled, url?, authentication, secret?, rotateSecret? }` |
| `PUT /api/admin/webhooks/{id}` | Update the same fields; direction is immutable |
| `GET /api/admin/webhooks/{id}/deliveries` | Latest 50 `{ id, createdAt, deliveryKey, status, httpStatus?, payload? }` records |
| `POST /api/admin/webhooks/{id}/send` | Manual outgoing send of `{ event, data }`; returns delivery metadata and the sent envelope |
| `POST /api/webhooks/incoming/{id}` | Public JSON receipt with endpoint HMAC/Bearer authentication; `{ message: "Webhook accepted" }` |

Direction is `INCOMING` or `OUTGOING`; authentication is `HMAC` or `BEARER`. Save returns `{ endpoint, secret? }`; new/rotated secrets are shown once and omitted from subsequent list responses. Blank inputs retain an unchanged destination's secret. The endpoint view contains `id`, `name`, `direction`, `enabled`, `url`, `authentication`, and `secretConfigured`.

HMAC requires unique `X-Webhook-Id`, Unix-seconds `X-Webhook-Timestamp`, and `X-Webhook-Signature: sha256=<hex HMAC-SHA256(secret, timestamp + "." + deliveryId + "." + rawBody)>`. Timestamp tolerance is five minutes. Bearer uses `Authorization: Bearer <secret>` and an optional deduplication ID. Bodies are UTF-8 JSON, max 64 KiB. Outgoing envelopes contain `{ id, event, createdAt, data }`; event names match `[A-Za-z0-9._-]{1,80}`. No automatic actions/retries occur. Invalid authentication returns 401; disabled/missing incoming endpoints 404; malformed input/oversize 400; rate limits 429. Public HTTPS/DNS restrictions and encrypted retention limits are in [the webhook guide](webhooks.md).

## Protected PDF sharing

Admin endpoints require `ADMIN`. Email includes the watermarked, password-encrypted PDF attachment **and** a protected online link; it never includes the password.

| Method and path | Purpose |
| --- | --- |
| `GET /api/admin/shares/settings` | `{ enabled, publicBaseUrl, maxLifetimeHours, emailAvailable }` |
| `PUT /api/admin/shares/settings` | Save `{ enabled, publicBaseUrl, maxLifetimeHours }` |
| `GET /api/admin/shares` | Latest 100 redacted share metadata records |
| `POST /api/admin/shares` | `{ folderId, path, recipient, password, lifetimeHours }`; emails the protected attachment and returns `{ share, url }` |
| `POST /api/admin/shares/{id}/revoke` | Revoke future online access; cannot recall attachments |
| `POST /api/shared/{token}/unlock` | Public `{ password }`; returns `{ accessToken, expiresAt }`, a grant valid for at most 15 minutes |
| `GET /api/shared/{token}` | With `X-Share-Access`, returns `{ filename, pageCount, recipient, expiresAt, canPreview, canDownload }` |
| `GET /api/shared/{token}/pages/{page}` | With `X-Share-Access`, one-based watermarked raster PNG |
| `POST /api/shared/{token}/download` | With `X-Share-Access` and `{ password }`, watermarked password-encrypted PDF attachment |

Share passwords require 12–72 characters and at most 72 UTF-8 bytes. Lifetime defaults to 72 hours, is at least one hour, and must not exceed the admin maximum (default 168, configurable up to 720). The generated attachment must fit 10 MiB; original PDF limits also apply. Tokens/grants/passwords are excluded from list responses, and share passwords are write-only. Shared file responses use no-store caching. A link requires a valid grant and remains subject to live expiry/revocation/source checks and document policy; downloaded/emailed attachments remain usable with their password after expiry/revocation. Bad passwords/grants return 401, inaccessible/changed/revoked links 404, policy blocks 403, invalid setup/PDF input 400, rate limits 429, failed email acceptance 503. See [sharing limits](file-sharing.md).

## Document templates and generated documents

Templates use the structured WeasyCanvas v0.8.0 schema. JSON supplied by regular users cannot replace the saved layout used for generation. These routes require authentication; template mutations additionally require `ADMIN`.

| Method and path | Purpose |
| --- | --- |
| `GET /api/admin/document-templates` | Latest 100 templates, including unpublished drafts |
| `POST /api/admin/document-templates` | Create `{ name, category, published, template, fieldRules, revision }` |
| `PUT /api/admin/document-templates/{id}` | Update the same fields with the current optimistic revision |
| `GET /api/documents/templates` | Latest 100 published templates for document generation |
| `GET /api/documents` | Latest 100 generated-document metadata records owned by the user; admins can see all |
| `POST /api/documents` | Generate from `{ templateId, name, values: { "customer.name": "Example", ... } }` |
| `GET /api/documents/{id}/pages/{page}` | Authenticated one-based raster PNG preview under current document policy |
| `GET /api/documents/{id}/download` | Original or policy-watermarked PDF download |
| `POST /api/documents/{id}/email` | Explicit send of `{ recipient, subject, text, protection, password? }` as a real PDF attachment |

`category` is optional text metadata (up to 100 characters, default empty), retained for compatibility. It has no fixed values and does not determine document content or fields. A template view contains `id`, `name`, `category`, `published`, `revision`, `updatedAt`, `template`, and `fieldRules`. Templates are limited to 1.9 MB including embedded images. Each field rule has `key`, `type`, optional `operation`, and `inputs`. Types are `TEXT`, `MULTILINE`, `NUMBER`, `MONEY`, `DATE`, `EMAIL`, or `DERIVED`. Derived rules use two numeric/derived input keys and `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, or `PERCENT`; cycles are invalid. The backend recomputes derived values even if a caller supplies them. Dates use ISO format; required/fallback/length constraints also come from `template.apiFields`. Values are literal flat keys, not executable expressions or remote API fetch definitions.

Generation returns `{ id, ownerId, templateId, templateRevision, templateName, name, filename, pageCount, byteSize, createdAt }`. Generated PDFs, source snapshots, and resolved field values are not returned in library metadata. PDFs are bounded to 10 MiB/200 pages. A generated record remains tied to its original template revision after later template changes. Regular accounts cannot access another user's document through the ordinary library endpoints. Published workflows can grant scoped access to PDFs at reached document/email steps, as described below.

Email protection is `PLAIN` (default) or `PASSWORD`. Protected sends require a 12-character minimum password within 72 UTF-8 bytes; passwords are write-only, not persisted, and not automatically included in message content. Both modes honor disabled downloads and mandatory policy watermarks. Sends return the existing `{ status, message }` provider-acceptance response; there is no automatic retry. This endpoint sends one generated PDF per message. Rate limits: generation 20/account/hour, previews 120/account/five minutes, email 10/account/five minutes. Renderer unavailable returns safe 503; invalid templates/values 400; permission policy 403; missing/inaccessible records 404; concurrent edits 409; rate limits 429. [Document setup and limits](documents-and-workflows.md).

## Workflow definitions and guided runs

Authoring requires an active `ADMIN` account; designs are managed at `/admin/workflows`.

| Endpoint | Contract |
| --- | --- |
| `GET /api/admin/workflows` | Latest 100 designs, including drafts and archives |
| `POST /api/admin/workflows` | Create `{ name, state, published, graph: { nodes, edges }, documentIds, revision }` |
| `PUT /api/admin/workflows/{id}` | Update using current revision |

Design views add `id`, `ownerId`, and `updatedAt`. State is `DRAFT`, `READY`, or `ARCHIVED`; `published` defaults false and is forced false unless ready. Legacy `POST /api/workflows` and `PUT /api/workflows/{id}` remain admin-only aliases. `GET /api/workflows` now returns the runnable catalogue, not editable graphs.

Nodes contain `id`, `kind`, `label`, `x`, `y`, optional `documentId`, and configuration `note`, `recipient`, `subject`, `protection`, `field`, `operator`, `expected`. Kinds are `START`, `DOCUMENT`, `REVIEW`, `CONDITION`, `EMAIL`, `END`. Protection is `PLAIN`/`PASSWORD`; no block stores a password. Operators are `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `GREATER_THAN`, `IS_SET`. Each edge is `{ id, source, target, branch }` with `NEXT`, `TRUE`, or `FALSE`. Drafts still require valid references, coordinates, and acyclic connections. Ready designs require one start, an end, reachable complete paths, both condition outputs, document selections, and email recipient/subject. Numeric comparison values must be decimals. Limits: 50 nodes, 100 edges, 50 unique attached documents, finite coordinates within +/-10000. Optimistic conflicts return 409.

Runtime endpoints require authentication and an active account. Runs are private to their owner, including against other administrators.

| Endpoint | Contract |
| --- | --- |
| `GET /api/workflows` | Latest 100 published ready designs; admins also see private ready previews. Metadata `{ id, name, revision, published, stepCount }` |
| `GET /api/workflows/runs` | Latest 100 own run summaries `{ id, name, workflowId, status, updatedAt }` |
| `POST /api/workflows/runs` | Start `{ workflowId }`, snapshots design; limited to 20/account/hour |
| `GET /api/workflows/runs/{id}` | Own run view |
| `POST /api/workflows/runs/{id}/advance` | `{ revision, nodeId, action, value?, note? }` |
| `PUT /api/workflows/runs/{id}/document` | `{ revision, nodeId, documentId }`; choose a PDF from the caller's library on current document/email step |
| `POST /api/workflows/runs/{id}/email` | `{ revision, nodeId, password? }`; explicit send using fixed design defaults, actual PDF attachment |
| `POST /api/workflows/runs/{id}/cancel` | `{ revision }`; retain history, cannot resume |
| `GET /api/workflows/runs/{id}/documents/{documentId}/pages/{page}` | PNG preview of a reached PDF, no-store, preview policy enforced |
| `GET /api/workflows/runs/{id}/documents/{documentId}/download` | PDF attachment, no-store, download/watermark policy enforced |

A run view contains `id`, `name`, `workflowId`, `workflowRevision`, `status` (`IN_PROGRESS`, `COMPLETED`, `CANCELLED`), optimistic `revision`, `current` step (or null), `history`, condition `values`, reached `documents` metadata, `blockedReason`, `emailState` (`NONE`, `SENDING`, `UNCERTAIN`), and `emailAttemptAt`. It does not expose future graph nodes or PDF bytes. History entries contain node ID, label/kind, outcome, note, completion time. Unpublishing/archive pauses continuation and scoped PDF access; owner history remains readable.

Actions: `CONTINUE` for start/document/end; `REVIEWED` for a manual review; `EVALUATE` for a condition; `CONFIRM_EMAIL` with a nonblank note for externally handled/checked delivery. Values have a 500-character limit and notes 1000. Conditions are server evaluated using literal strings or bounded decimals; no expression interpreter. Replacements apply to later references to the same original PDF in that run. Neither action nor replacement can jump nodes or alter graph/recipient defaults.

Email uses the ordinary generated-PDF protection, attachment size, rate, active-account, and policy checks. Passwords are write-only, never persisted. Missing mail configuration returns 400 before claiming a send. A committed attempt prevents automatic resends on timeout; uncertain results require manual checking and confirmation. In-flight attempts prevent confirmation/cancellation for two minutes. Email returns the updated run after provider acceptance; that is not inbox delivery. There is no background scheduler or multi-person approval/assignment engine. [Workflow behavior](documents-and-workflows.md#building-workflows).

## Branding

Branding metadata/images are public GET endpoints so the business name, colours, logos, and sign-in backgrounds work before authentication. Admin writes require `ADMIN`.

| Endpoint | Contract |
| --- | --- |
| `GET /api/branding` | `{ logoVersion, faviconVersion, settings, loginImages: [{slot, version}] }`; metadata requires cache revalidation |
| `PUT /api/admin/branding` | Save the complete `settings` object, including its current `revision`; return updated branding metadata |
| `GET /api/branding/assets/{kind}/{version}` | Normalized `image/png`, `nosniff`, public caching; 404 for removed/replaced versions |
| `POST /api/admin/branding/{kind}` | Multipart form field `file`; returns updated branding metadata |
| `DELETE /api/admin/branding/{kind}` | Reset that custom asset and return updated metadata |
| `GET /api/branding/login-images/{slot}/{version}` | Normalized public `image/jpeg`, `nosniff`, versioned caching; 404 for removed/replaced versions |
| `POST /api/admin/branding/login-images/{slot}` | Multipart `file`; save or replace a sign-in image and return updated metadata |
| `DELETE /api/admin/branding/login-images/{slot}` | Remove that sign-in image and return updated metadata |

`kind` is `LOGO` or `FAVICON`; `version` is the returned SHA-256 content version. Empty, unsupported, oversized, corrupt, or excessive-dimension uploads return 400. Inputs are PNG/JPEG/WebP up to 1 MiB and 2048 by 2048 pixels. The server ignores uploaded filenames/content-type claims and stores a re-encoded PNG. Logo dimensions fit within 512 pixels; favicon dimensions are 64 square with transparent padding. No external image URLs are accepted.

`settings` contains `applicationName` (trimmed, 1–80 characters), nullable six-digit hex `sidebarBackgroundColor`, `sidebarTextColor`, and `linkColor`, `loginBackgroundMode` (`NONE`, `SINGLE`, `SLIDESHOW`), nullable `loginBackgroundSlot`, `slideshowSeconds` (5–60), and `revision`. Missing preferences use WeaveOps/theme defaults without seeding a row. Background modes need an uploaded image; a selected single-image slot must exist. Stale preference updates return 409. API slots are 0–4, displayed as images 1–5 in the UI. Background inputs are PNG/JPEG/WebP up to 5 MiB and 4096 × 4096 pixels, re-encoded as JPEG at a longest side of 1920 pixels.

A missing favicon falls back to the custom logo; a missing logo falls back to the bundled placeholder. No external image URLs or arbitrary CSS values are accepted. The frontend refreshes branding after saves and applies the name/title, sidebar/text/link colours, and image preferences. See [branding behavior and persistence](branding.md).

## Notifications

| Endpoint | Contract |
| --- | --- |
| `GET /api/admin/notifications` | Admin: `{ revision, methods, notifications }` including method descriptors/availability and type definitions/rules |
| `PUT /api/admin/notifications` | Admin: `{ revision, methods: { methodId: boolean }, rules: { notificationId: rule } }`; revision starts at -1 for absent settings, stale writes 409 |
| `POST /api/admin/notifications/preview` | Admin: `{ notificationId, methodId, template }`; synthetic values only, returns subject/text/sanitized HTML/parameters, sends nothing |
| `POST /api/admin/notifications/send` | Admin: `{ methodId, recipient, subject, message, userId? }`; General messages rule; optional internal `userId` applies member preferences; returns per-method acceptance statuses |
| `GET /api/admin/notifications/deliveries` | Admin: latest 200 metadata-only delivery records with masked recipient hints; 90-day retention |
| `GET /api/account/notifications` | Active member: own optional notification choices, inherited defaults, method availability |
| `PUT /api/account/notifications` | Active member: `{ revision, choices: { notificationId: [methodId] } }`; absent entry restores defaults; empty list opts out; cannot expand admin-allowed methods |
| `GET /api/notifications/availability` | Active member: `{ documentsEmail, workflowEmail, sharesEmail }` for corresponding UI controls |

A rule contains `enabled`, `allowedMethods`, `defaultMethods`, `allowMemberChoice`, and `templates` keyed by method ID. A template contains `subject` (200), `text` (20,000), `html` (100,000), ordered `parameters` (20 entries, 1,000 characters each), and optional `providerTemplate` (200). Placeholder names must be declared by the type. Security message bodies must contain `{{code}}`; shared methods cannot receive codes. Security/explicit PDF routes do not support member opt-outs. Email-only MFA interlocks reject disabling its method/OTP route with 400. No public OTP-delivery endpoint accepts user-supplied verification flags.

Method descriptors include plugin ID, recipient kind (`EMAIL`, `PHONE`, `FIXED`), HTML/OTP/attachment support, parameter mode, and text length limit. Plugin methods require the owning module's `NOTIFICATIONS` extension and enabled/configured state. Method and type switches apply to direct and automatic sends. Delivery attempts are claimed before external I/O and never automatically retried. Codes, bodies, credentials and provider responses are omitted from history. HTML previews are sanitized and sandboxed. See [behavior and limits](notifications.md) and [plugin delivery API](plugins/notifications.md).

## Role, view, and directory policies

See [Access controls](access-controls.md#persistence-and-apis) for policy JSON and endpoints. `GET /api/access/me` supplies effective features; super admins manage policies at `/api/admin/access-controls` and assign roles with `PATCH /api/admin/users/{id}/role`. Role changes invalidate sessions. Existing admin endpoints also accept `SUPER_ADMIN`; delegated admin access must satisfy the feature policy. Storage permissions apply to all object reads, archives, previews, and search.
