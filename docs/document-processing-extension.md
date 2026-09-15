# Document Folder and Processing Extension

## Implemented now

The existing `items` table and `/api/items` route are retained for compatibility, but each item now represents an application-managed document folder.

Each folder has:

- a PostgreSQL row containing its title, description, tags, owner, and derived `storage_prefix`
- an object-storage prefix in the form `{configured-storage-view-path}/{folder-id}/`
- one pgvector row built from the folder's title, description, and tags
- a live document count derived from objects below its storage prefix

Each discovered object also has a `storage_documents` PostgreSQL row and a separate
`document_vectors` pgvector row. The current document embedding uses filename, relative path,
and content type. A metadata checksum prevents unchanged documents from being embedded again;
full reindexing also removes database rows for objects that no longer exist.

The default development profile uses MinIO through the S3 adapter. The alternate `docker-compose.dev.azurite.yml` profile uses the Azure Blob adapter against Azurite. `NONE` remains valid for deployments that have not configured object storage.

Object stores do not have conventional directories, so both adapters create a zero-byte marker ending in `/`. The storage-root list is the joined application view: PostgreSQL supplies identity and searchable metadata, while object storage supplies the root marker and live object count. Opening a root uses provider-native delimiter listing to navigate immediate child folders and objects recursively. A root containing child content cannot be deleted.

The persisted `storage_root_path` setting is a security and product boundary: browser APIs
validate registered roots beneath it and never offer container-wide listing. Changing the
setting repoints registered roots; it does not migrate objects between prefixes.

## Planned extension boundary (not implemented)

Document processing should be added behind a processor interface instead of inside the folder service. A future processor can declare the media types it supports and emit normalized document metadata and text chunks. This keeps these concerns independent:

- object storage: durable original files
- PostgreSQL: folder/document identity, processing state, provenance, and errors
- pgvector: derived embeddings and searchable payloads
- processors: text extraction, image classification, or OCR

The existing `storage_documents` table is the extension point for fields such as:

- `id`, `folder_id`, `object_key`, `media_type`, `size_bytes`, and object `etag`/version
- processing status, selected processor kind/version, attempt timestamps, and error details
- normalized metadata (`jsonb`) and references to extracted artifacts

Document vectors already use `document_vectors` rather than overloading folder vectors, and store
an embedding model/version plus stable source checksum. Future extracted chunks should use their
own child table with processor and source provenance.

The eventual flow should be asynchronous:

1. Discover an uploaded object under a known folder prefix (implemented for browsing/reindexing).
2. Persist its document row and metadata vector (implemented); enqueue content work using the object version as the idempotency key (planned).
3. Select a processor by media type (plain-text extraction, image classification, or OCR).
4. Persist normalized results and chunk provenance.
5. Embed changed chunks and atomically publish their searchable state.

Text extraction, image classification, OCR, uploads, and background jobs are deliberately outside the current implementation.
