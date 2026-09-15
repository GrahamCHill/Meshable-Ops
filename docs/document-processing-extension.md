# Document Folder and Processing Extension

## Implemented now

The existing `items` table and `/api/items` route are retained for compatibility, but each item now represents an application-managed document folder.

Each folder has:

- a PostgreSQL row containing its title, description, tags, owner, and immutable `storage_prefix`
- an S3-compatible object-storage prefix in the form `folders/{folder-id}/`
- one pgvector row built from the folder's title, description, and tags
- a live document count derived from objects below its storage prefix

The default development profile uses MinIO through the S3 adapter. The alternate `docker-compose.dev.azurite.yml` profile uses the Azure Blob adapter against Azurite. `NONE` remains valid for deployments that have not configured object storage.

Object stores do not have conventional directories, so both adapters create a zero-byte marker ending in `/`. The storage-root list is the joined application view: PostgreSQL supplies identity and searchable metadata, while object storage supplies the root marker and live object count. Opening a root uses provider-native delimiter listing to navigate immediate child folders and objects recursively. A root containing child content cannot be deleted.

## Planned extension boundary (not implemented)

Document processing should be added behind a processor interface instead of inside the folder service. A future processor can declare the media types it supports and emit normalized document metadata and text chunks. This keeps these concerns independent:

- object storage: durable original files
- PostgreSQL: folder/document identity, processing state, provenance, and errors
- pgvector: derived embeddings and searchable payloads
- processors: text extraction, image classification, or OCR

A future `folder_documents` table can track fields such as:

- `id`, `folder_id`, `object_key`, `media_type`, `size_bytes`, and object `etag`/version
- processing status, selected processor kind/version, attempt timestamps, and error details
- normalized metadata (`jsonb`) and references to extracted artifacts

Document and chunk vectors should use their own table rather than overloading the current folder vector. Store the embedding model/version and a stable source checksum so processing and embedding are idempotent when jobs are retried.

The eventual flow should be asynchronous:

1. Register or discover an uploaded object under a known folder prefix.
2. Persist a pending document row and enqueue work using the object version as the idempotency key.
3. Select a processor by media type (plain-text extraction, image classification, or OCR).
4. Persist normalized results and chunk provenance.
5. Embed changed chunks and atomically publish their searchable state.

Text extraction, image classification, OCR, uploads, and background jobs are deliberately outside the current implementation.
