# Application API reference



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

