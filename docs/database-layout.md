# Database layout

Optional extensions use `plugin_settings` (enabled state, roles, search visibility, table policies) and `plugin_kanban_boards` (owner, name, revision, timestamp, validated JSON, local search text/vector). Include these in PostgreSQL backups; metadata JSON export omits them. Disabling retains data. Plugin search is opt-in, with live role and ownership checks. Admins can empty only declared plugin tables with an explicit confirmation; schema and settings remain. The host-managed `workspace_plugin_configuration` stores encrypted configuration JSON and optimistic revisions; it is retained when clearing owned plugin tables. Include it in backups and wrapping-key rotation. See [Workspace plugins](plugins.md).

Workspace branding uses PostgreSQL: `branding_assets` stores logo/favicon PNGs; `branding_preferences` stores the business name, hex colour overrides, background mode/selection/timing, and optimistic revision; `branding_login_images` stores up to five numbered JPEG images with content versions. Image listing queries project metadata without fetching the image bytes. Include all three tables in database backups; the metadata JSON export omits them. See [branding](branding.md).

PostgreSQL stores application identity, storage metadata, feature/provider configuration, indexing jobs, and derived search content. S3/Azure Blob stores original files. MongoDB retains events and audit history; it is not a search source. Redis supports Superset.

## Stores and ownership

| Store | Contents | Search participation |
| --- | --- | --- |
| PostgreSQL `app_db` | Users, shared folders, connections, settings, document identities/chunks, and pgvector indexes | Folder/document metadata and enabled extracted content |
| PostgreSQL `superset` | Superset's own metadata | Outside application document search |
| S3-compatible bucket or Azure Blob container | Original document objects beneath selected prefixes | Read through authenticated storage adapters for discovery/extraction |
| MongoDB `app_db` | `events`, `activity_logs` | Excluded from search and AI retrieval; integration deferred |
| Redis | Superset cache and Celery broker/results | Outside application search |

## Relational relationships

```mermaid
erDiagram
    USERS ||--o{ ITEMS : creates
    STORAGE_CONNECTIONS o|--o{ ITEMS : maps
    ITEMS ||--o{ ITEM_TAGS : has
    ITEMS ||--o| ITEM_VECTORS : indexes
    ITEMS ||--o{ STORAGE_DOCUMENTS : contains
    STORAGE_DOCUMENTS ||--o| DOCUMENT_VECTORS : indexes_metadata
    STORAGE_DOCUMENTS ||--o{ DOCUMENT_CHUNKS : contains
    DASHBOARD_EMBED_CONFIGS ||--o{ DASHBOARD_EMBED_CONFIG_ROLES : allows

    USERS {
        uuid id PK
        varchar email UK
        varchar username UK
        varchar password
        varchar role
        boolean active
        boolean can_upload_documents
    }
    STORAGE_CONNECTIONS {
        uuid id PK
        varchar nickname
        varchar provider
        varchar bucket
        text endpoint
        text access_key
        text secret_key
        text base_path
        boolean enabled
    }
    ITEMS {
        uuid id PK
        uuid user_id FK
        uuid storage_connection_id FK
        varchar title
        text description
        text storage_prefix
        boolean accessible
    }
    ITEM_TAGS {
        uuid item_id FK
        text tag
    }
    ITEM_VECTORS {
        uuid item_id PK,FK
        vector embedding
        jsonb payload
        varchar embedding_model
    }
    STORAGE_DOCUMENTS {
        uuid id PK
        uuid item_id FK
        text storage_path
        varchar file_name
        bigint size_bytes
        varchar processing_status
        varchar source_checksum
        varchar embedding_model
        timestamptz vectorized_at
    }
    DOCUMENT_VECTORS {
        uuid document_id PK,FK
        vector embedding
        jsonb payload
        varchar source_checksum
        varchar embedding_model
    }
    DOCUMENT_CHUNKS {
        uuid id PK
        uuid document_id FK
        int chunk_index
        text content
        vector embedding
        varchar source_checksum
        varchar embedding_model
    }
    APP_SETTINGS {
        uuid id PK
        varchar storage_provider
        text storage_bucket
        text storage_access_key
        text storage_secret_key
        text storage_root_path
        boolean vector_reindex_enabled
        varchar vector_reindex_schedule
        varchar vector_reindex_time
        varchar vector_reindex_day_of_week
        varchar vector_reindex_time_zone
        timestamptz vector_reindex_next_run_at
        boolean vector_reindex_pending_changes
        timestamptz vector_reindex_last_check_at
    }
    AI_SETTINGS {
        bigint id PK
        varchar source
        boolean search_enabled
        boolean launcher_enabled
        boolean enabled
        boolean index_content
        varchar chat_provider
        text chat_base_url
        varchar chat_model
        text chat_api_key
        varchar embedding_provider
        text embedding_base_url
        varchar embedding_model
        text embedding_api_key
        boolean embedding_send_dimensions
    }
    DASHBOARD_EMBED_CONFIGS {
        uuid id PK
        text label
        text dashboard_id
        text guest_token_resource_id
        int sort_order
        boolean enabled
    }
    DASHBOARD_EMBED_CONFIG_ROLES {
        uuid dashboard_embed_config_id FK
        text role_name
    }
```

Vector columns are `vector(384)`; the diagram omits timestamps and some descriptive fields for readability. Settings tables hold application-wide configuration rather than per-user preferences.

## Identity and storage

`users` contains login/profile data, the stored bcrypt password hash, role, activation state, and independent upload grant. The existing `ADMIN` role provides super-admin configuration access. Deactivation retains the record and denies authentication; the UI does not hard-delete users.

`items` retains the legacy name for shared folder roots. `title` is the in-app nickname, `storage_prefix` is the saved object prefix, and `accessible` controls publication. `user_id` records the creating user; visibility is shared rather than limited to that owner.

- A null `storage_connection_id` identifies an application-managed root on the default connection: `{app_settings.storage_root_path}/{folder-id}/`.
- A non-null reference identifies an explicitly selected prefix on a named S3/Azure connection. `base_path` limits the administrator's folder picker.
- Prefix uniqueness is scoped to each connection, with a separate partial unique index for legacy roots. Matching paths can therefore exist in different buckets/containers.
- Parent/child selections in one connection cannot overlap. A new connection shares no folder automatically.
- Hidden selections and disabled connections are checked before browse/upload/download and again in search SQL. Revocation does not require deleting cached vectors.

`storage_connections` stores nicknames, provider/bucket/container, optional endpoint/region, boundary, enablement, and encrypted credentials. Nicknames never rename cloud objects. Removing a mapped selection removes metadata/index rows and leaves original objects in storage. Location changes are blocked while that connection still has selected folders.

`item_tags` is the current JPA collection table. The initial PostgreSQL bootstrap also contains a legacy array column; application code uses the collection mapping.

## Document and vector state

`storage_documents` identifies each discovered object by unique `(item_id, storage_path)`. It records filename, content type, size, provider modification time, metadata fingerprint (`source_checksum`), model identity, processing state, and last successful vectorization time.

| Processing status | Meaning |
| --- | --- |
| `METADATA_ONLY` | Content indexing is off; metadata is indexed |
| `CONTENT_INDEXED` | Extracted text chunks were indexed |
| `CONTENT_TRUNCATED` | Only the bounded portion of the document was indexed |
| `UNSUPPORTED_FORMAT` | Filename format has no supported extractor; metadata remains searchable |
| `CONTENT_TOO_LARGE` | Size is unknown or above the 5 MiB content-read limit |
| `NO_EXTRACTABLE_TEXT` | Extraction returned no text, such as a scanned PDF needing OCR |
| `CONTENT_UNAVAILABLE` | Extraction/read failed; metadata indexing can still succeed |
| `INDEX_ERROR` | Vector/configuration indexing failed; a later reindex retries |

Three derived search tables use 384-dimensional vectors and HNSW cosine indexes:

- `item_vectors`: folder title/description/tags and identifying payload.
- `document_vectors`: per-object metadata vector and parent folder/path payload.
- `document_chunks`: ordered extracted content, original-byte SHA-256 checksum, model identity, and vector. `(document_id, chunk_index)` is unique.

Document vectors/chunks have cascading foreign keys to their document; documents belong to their selected folder. Full reindexing removes documents missing from storage. Browsing and uploading synchronize discovered objects; normal metadata change detection uses path/name/type/size/modification time. Run a full reindex if bytes changed without changed provider metadata.

Each vector carries an embedding-model fingerprint. Queries only compare matching fingerprints. Default local matching is `local-feature-hash-v2` inside Kotlin; earlier/local or different-provider vectors require reindexing. Keyword fallback can use current metadata and permitted existing chunks without comparing incompatible vectors. Content chunks are excluded immediately when content indexing is disabled.

Both vector and keyword queries join live folder visibility and connection enablement for all result types. AI generation uses content chunks and checks their access before and after the provider call. No event or audit collection is indexed.

## Configuration and schedule state

`app_settings` holds the default storage connection and operational job settings. The service uses the first settings row by creation time. Relevant job columns are:

| Column | Purpose |
| --- | --- |
| `vector_reindex_enabled` | Enables scheduled work; manual reindex remains available when false |
| `vector_reindex_schedule` | `INTERVAL`, `DAILY`, `WEEKLY`, or `ON_CHANGE` |
| `vector_reindex_interval_minutes` | Interval length, 15 to 10080 minutes |
| `vector_reindex_time` | Local `HH:mm`, initially `00:00` |
| `vector_reindex_day_of_week` | Weekly day, initially `FRIDAY` |
| `vector_reindex_time_zone` | Valid IANA timezone, initially `Africa/Johannesburg` |
| `vector_reindex_next_run_at` | Persisted due instant; null while an enabled change job awaits changes |
| `vector_reindex_pending_changes` | Coalesced work awaiting the selected change-job time |
| `vector_reindex_last_check_at` | Most recent change poll |
| `last_vector_reindex_at`, `last_vector_reindex_status` | Last outcome and timestamp |

`ai_settings` uses singleton ID `1`. `source` selects `ENVIRONMENT` or `ADMIN`. It stores independent search, launcher, AI-answer, and content-indexing switches plus separate chat/embedding endpoints, models, and encrypted keys. Environment values are read from server configuration and are not copied into saved admin keys.

`dashboard_embed_configs` and its role collection determine the enabled dashboards visible to each role. The linked Superset dashboard ID and backend-resolved embed UUID are distinct. Selection/order/labels are runtime-managed; the browser does not edit embed UUIDs.

## Credential storage and startup

Storage access/secret fields and AI API-key fields are encrypted TEXT using AES-256-GCM with a fresh nonce per write. JPA converts ciphertext to server-side values; API responses expose configured flags. Password hashes and credentials are excluded from entity JSON/debug output. Wrapping keys stay outside PostgreSQL in the server environment.

Hibernate currently uses `ddl-auto=update`; supplemental [schema.sql](../backend/src/main/resources/schema.sql) runs after JPA initialization to create pgvector objects, indexes, and compatibility changes. `StoredSecretMigration` then encrypts legacy plaintext/rewraps credentials before explicit account/storage bootstrap; scheduled work waits until application startup completes. This is the current startup mechanism, not a versioned Flyway/Liquibase migration system. Existing encrypted rows require the matching wrapping key. See [upgrade and key rotation](deployment.md#upgrading-an-existing-installation).

## MongoDB and Redis

MongoDB collections remain `events` (`userId`, `type`, `payload`, `createdAt`) and `activity_logs` (`userId`, `action`, resource/client metadata, `timestamp`). Existing event viewing, audit viewing, and admin export remain intact. There is no OCR-text collection or MongoDB vector-search integration in this change.

Redis remains a Superset cache/Celery concern. The application backend does not use it for indexing jobs, vector storage, or search sessions.

## Backups and recovery

| Artifact | Contains | Recovery limits |
| --- | --- | --- |
| Admin JSON export v2 | Selected user/profile metadata, folders/connections, document metadata/status, dashboard definitions, non-secret AI/settings/job configuration, events, audit logs | Omits password hashes, credentials/ciphertext, original objects, content chunks, vectors, pending-job runtime state, and per-user upload grants; no application import/restore endpoint |
| PostgreSQL database backup | Relational state, bcrypt hashes, encrypted credential fields, content chunks, vectors, and persisted job state | Stored credentials need the matching separately retained wrapping key |
| MongoDB backup | Event/audit history | Does not restore PostgreSQL configuration or original documents |
| Object-storage backup/versioning | Original files and prefixes | Does not restore application nicknames, publication rules, or login identities |
| Wrapping-key backup | Current/required previous server keys | Retain separately from database backups and with restricted access; losing the matching key prevents decryption |

The export identifies itself as `superset-docker-app-backup-v2` and `credentialsIncluded: false`. It is a useful metadata export, not a complete disaster-recovery backup. Derived vectors can be rebuilt from folder metadata and original objects, with extraction/embedding dependencies available. Do not delete primary database records just because vectors are rebuildable.

## Implementation sources

- [PostgreSQL entities](../backend/src/main/kotlin/com/starter/model/postgres/)
- [MongoDB document models](../backend/src/main/kotlin/com/starter/model/mongo/Documents.kt)
- [Supplemental schema](../backend/src/main/resources/schema.sql)
- [First-volume PostgreSQL bootstrap](../docker/postgres/init.sql)
- [MongoDB bootstrap](../docker/mongo/init.js)
- [Backup export](../backend/src/main/kotlin/com/starter/service/BackupExportService.kt)
- [Search and job guide](search-and-indexing.md)

### Document access settings

`document_access_settings` uses singleton ID `1`: `pdf_preview_enabled` defaults true, `download_mode` defaults `NORMAL` (`NORMAL`, `WATERMARKED`, or `DISABLED`), and `watermark_text` defaults `CONFIDENTIAL`. An absent row uses those defaults; the first admin save persists it. Hibernate creates the table on upgrade. These settings are not included in the existing JSON backup export; use database backups.

### Email settings

`email_settings` uses singleton ID `1`. It stores `source` (`ENVIRONMENT` or `ADMIN`), `enabled` (initially false), `provider`, sender/reply-to fields, SMTP host/port/security/authentication, API base URL, and Mailgun domain. `smtp_username`, `smtp_password` (also app password/OAuth2 token), and `api_key` are encrypted TEXT columns, never returned by settings APIs. Startup secret rotation includes all three columns. Environment credentials remain outside the row. Hibernate creates the table; an absent row selects the disabled environment defaults. Message bodies, delivery queues, and sent history are not persisted. The existing JSON export excludes this table; use PostgreSQL backups and preserve wrapping keys. See [email delivery](email-delivery.md).

### MFA, webhooks, and protected sharing

Hibernate also creates the following domain tables. They are excluded from JSON export and from search/AI retrieval. Preserve PostgreSQL backups and wrapping keys; MongoDB is unchanged.

| Table / columns | Purpose and protection |
| --- | --- |
| `users.session_version`, `mfa_prompt_dismissed` | Security version defaults `0`, optional primary-admin prompt dismissal defaults false. JWT validation checks the live version and MFA claim. |
| `mfa_credentials` | User ID primary key (`id`), encrypted `totp_secret`, `email_enabled`, last accepted TOTP step, JSON recovery-code hashes. |
| `mfa_challenges` | SHA-256 token hash as primary key, user/purpose/security version/expiry, attempts/consumed, encrypted `pending_secret`, HMAC email-code proof, last send time. Five-minute challenges; expired rows cleaned hourly after a grace hour. |
| `security_rate_limits` | Hashed scope/identity/time bucket, attempts and expiry. Atomic PostgreSQL upsert counters, hourly expiry cleanup. |
| `webhook_endpoints` | Name/direction/URL/enabled/authentication plus encrypted `secret`. |
| `webhook_deliveries` | Endpoint, unique delivery key per endpoint, timestamp/status/HTTP status and encrypted JSON `payload`. Maximum 100 per endpoint; seven-day hourly cleanup. |
| `file_share_settings` | Singleton ID `1`: enabled (default true), public origin override, maximum link lifetime (default 168 hours). |
| `file_shares` | SHA-256 link token, sender/folder/path/recipient, salted bcrypt password hash, source-byte fingerprint, filename/pages/created/expiry/revoked/delivery state. No raw PDF or recoverable password is stored. |
| `share_access_grants` | SHA-256 grant as primary key, share ID, expiry of at most 15 minutes. Expired grants cleaned hourly. |

The new MFA/webhook/share repository packages are explicitly included in PostgreSQL repository scanning. Startup wrapping-key migration includes authenticator secrets, pending enrollment secrets, webhook secrets, and webhook delivery payloads. Retention cleanup runs after startup. Email attachments are generated in memory; general email bodies are not retained. The share registry retains metadata, not the attachment bytes. See [MFA](mfa.md), [webhooks](webhooks.md), and [PDF sharing](file-sharing.md).

### Document generation and workflow tables

| Table | Contents |
| --- | --- |
| `document_templates` | Name/category/published flag, structured WeasyCanvas template JSON, typed field/calculation rules, creator/timestamps, optimistic revision. |
| `generated_documents` | Owner, template ID/revision/name, document name/filename, created time/pages/size, resolved values, template snapshot, immutable PDF bytes (`bytea`). Owner/created-time index. |
| `workflow_definitions` | Owner, name, state (`DRAFT`, `READY`, `ARCHIVED`), typed graph JSON, attached document UUIDs, created/updated times, optimistic revision. Owner/updated-time index. |

Hibernate creates these tables through the existing upgrade mechanism; there is no Flyway migration system. Regular users access their own generated documents/workflows; admins can manage all. Attachment IDs are validated against available documents on every workflow save, not accepted as proof of access. PDF bytes/snapshots/resolved values are excluded from metadata serialization; library queries fetch metadata only. Template revisions are optimistic concurrency tokens beginning at zero. Editing a template does not rewrite an existing PDF.

Generated PDFs live in PostgreSQL in this first version. They are not automatically mirrored into S3/Azure Blob or added to document search. Template/field data and workflow metadata are access-controlled application records, not credentials encrypted by the secret converter; protect database backups appropriately. PDF passwords are supplied per send and never persisted. The current admin JSON export omits these tables. Preserve PostgreSQL backups for document history and templates; MongoDB and existing object storage remain unchanged. See [documents and workflows](documents-and-workflows.md).
