-- Vector columns allow NULL while an existing index awaits dimension migration and reindexing.
CREATE EXTENSION IF NOT EXISTS vector;

-- Generated PDF locations and deletion outbox. These objects never enter storage_documents or the search index.
CREATE TABLE IF NOT EXISTS generated_pdf_files (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('DOCUMENT','REPORT')),
    provider VARCHAR(32) NOT NULL,
    source TEXT NOT NULL,
    location_json TEXT NOT NULL,
    credentials_json TEXT NULL,
    locator_json TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('UPLOADING','ACTIVE','DELETE_PENDING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_delete_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_generated_pdf_deletions ON generated_pdf_files (next_delete_at) WHERE state='DELETE_PENDING';
CREATE INDEX IF NOT EXISTS idx_generated_pdf_orphans ON generated_pdf_files (created_at) WHERE state IN ('UPLOADING','ACTIVE');

-- Export retention is opt-in. Only explicit template derivatives may lose their cached PDF bytes.
CREATE TABLE IF NOT EXISTS generated_pdf_cleanup_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    schedule VARCHAR(16) NOT NULL DEFAULT 'NEVER' CHECK (schedule IN ('NEVER', 'DAILY', 'WEEKLY')),
    retention_days INTEGER NOT NULL DEFAULT 30 CHECK (retention_days BETWEEN 1 AND 3650),
    next_run_at TIMESTAMPTZ NULL,
    last_run_at TIMESTAMPTZ NULL,
    last_reports BIGINT NOT NULL DEFAULT 0,
    last_documents BIGINT NOT NULL DEFAULT 0,
    last_bytes BIGINT NOT NULL DEFAULT 0,
    last_status TEXT NULL
);
INSERT INTO generated_pdf_cleanup_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
ALTER TABLE generated_pdf_cleanup_settings ADD COLUMN IF NOT EXISTS destination VARCHAR(32) NOT NULL DEFAULT 'AUTO';
ALTER TABLE generated_pdf_cleanup_settings ADD COLUMN IF NOT EXISTS connection_id UUID NULL;
ALTER TABLE generated_pdf_cleanup_settings ADD COLUMN IF NOT EXISTS last_storage_status TEXT NULL;
ALTER TABLE generated_pdf_cleanup_settings ADD COLUMN IF NOT EXISTS last_storage_at TIMESTAMPTZ NULL;
CREATE INDEX IF NOT EXISTS idx_project_reports_cleanup ON workspace_project_reports (created_at);
CREATE INDEX IF NOT EXISTS idx_generated_documents_cleanup ON generated_documents (created_at) WHERE pdf_regenerable = TRUE;

-- Section IDs remain in category, preserving existing assignments while allowing custom sections.
ALTER TABLE IF EXISTS workspace_projects DROP CONSTRAINT IF EXISTS workspace_projects_category_check;

ALTER TABLE IF EXISTS plugin_settings ADD COLUMN IF NOT EXISTS search_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS plugin_settings ADD COLUMN IF NOT EXISTS table_access_json TEXT NOT NULL DEFAULT '{}';

-- Compiled Kanban plugin owns this table; vectors are local and need no AI key.
ALTER TABLE IF EXISTS plugin_kanban_boards ADD COLUMN IF NOT EXISTS search_text TEXT NOT NULL DEFAULT '';
ALTER TABLE IF EXISTS plugin_kanban_boards ADD COLUMN IF NOT EXISTS search_embedding vector(1536);
CREATE INDEX IF NOT EXISTS idx_plugin_kanban_search ON plugin_kanban_boards USING hnsw (search_embedding vector_cosine_ops);

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE users
SET active = TRUE
WHERE active IS NULL;

ALTER TABLE IF EXISTS users
    ALTER COLUMN active SET DEFAULT TRUE;

ALTER TABLE IF EXISTS users
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS can_upload_documents BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE IF EXISTS items
    ADD COLUMN IF NOT EXISTS storage_prefix TEXT;

ALTER TABLE IF EXISTS items ADD COLUMN IF NOT EXISTS accessible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE IF EXISTS items DROP CONSTRAINT IF EXISTS items_storage_prefix_key;
DROP INDEX IF EXISTS idx_items_storage_prefix;
CREATE UNIQUE INDEX IF NOT EXISTS idx_items_legacy_storage_prefix
    ON items (storage_prefix) WHERE storage_prefix IS NOT NULL AND storage_connection_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_items_connection_storage_prefix
    ON items (storage_connection_id, storage_prefix) WHERE storage_connection_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS item_tags (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_tags_item_id ON item_tags (item_id);

CREATE TABLE IF NOT EXISTS item_vectors (
    item_id UUID PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
    embedding vector(1536),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_item_vectors_embedding_hnsw
    ON item_vectors USING hnsw (embedding vector_cosine_ops);

ALTER TABLE item_vectors ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128) NOT NULL DEFAULT 'legacy';

CREATE TABLE IF NOT EXISTS storage_documents (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NULL,
    size_bytes BIGINT NULL,
    storage_last_modified VARCHAR(255) NULL,
    processing_status VARCHAR(64) NOT NULL DEFAULT 'METADATA_ONLY',
    source_checksum VARCHAR(64) NULL,
    embedding_model VARCHAR(255) NULL,
    vectorized_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_storage_documents_item_path UNIQUE (item_id, storage_path)
);

CREATE INDEX IF NOT EXISTS idx_storage_documents_item_id
    ON storage_documents (item_id);

CREATE TABLE IF NOT EXISTS document_vectors (
    document_id UUID PRIMARY KEY REFERENCES storage_documents(id) ON DELETE CASCADE,
    embedding vector(1536),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_checksum VARCHAR(64) NULL,
    embedding_model VARCHAR(255) NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_document_vectors_embedding_hnsw
    ON document_vectors USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES storage_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    source_checksum VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    UNIQUE(document_id, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- Page provenance for OCR and existing searchable PDFs.
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS page_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_document_chunks_fulltext ON document_chunks USING gin(to_tsvector('simple', content));
CREATE TABLE IF NOT EXISTS document_pages (
    document_id UUID NOT NULL REFERENCES storage_documents(id) ON DELETE CASCADE,
    page_index INTEGER NOT NULL,
    page_number INTEGER,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY(document_id, page_index)
);

CREATE TABLE IF NOT EXISTS document_processing_jobs (
    document_id UUID PRIMARY KEY REFERENCES storage_documents(id) ON DELETE CASCADE,
    source_checksum VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    attempts INTEGER NOT NULL DEFAULT 0,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_document_processing_due ON document_processing_jobs(state, next_attempt_at);

CREATE TABLE IF NOT EXISTS dashboard_embed_configs (
    id UUID PRIMARY KEY,
    label TEXT NOT NULL,
    description TEXT NULL,
    dashboard_id TEXT NOT NULL,
    guest_token_resource_id TEXT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE dashboard_embed_configs
    DROP CONSTRAINT IF EXISTS dashboard_embed_configs_dashboard_id_key;

CREATE INDEX IF NOT EXISTS idx_dashboard_embed_configs_sort_order
    ON dashboard_embed_configs (sort_order, created_at);

CREATE TABLE IF NOT EXISTS dashboard_embed_config_roles (
    dashboard_embed_config_id UUID NOT NULL REFERENCES dashboard_embed_configs(id) ON DELETE CASCADE,
    role_name TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dashboard_embed_config_roles_config_id
    ON dashboard_embed_config_roles (dashboard_embed_config_id);

CREATE TABLE IF NOT EXISTS app_settings (
    id UUID PRIMARY KEY,
    storage_provider VARCHAR(32) NOT NULL DEFAULT 'NONE',
    storage_bucket TEXT NULL,
    storage_region TEXT NULL,
    storage_endpoint TEXT NULL,
    storage_access_key TEXT NULL,
    storage_secret_key TEXT NULL,
    storage_root_path TEXT NOT NULL DEFAULT 'folders',
    vector_reindex_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vector_reindex_interval_minutes INTEGER NOT NULL DEFAULT 1440,
    last_vector_reindex_at TIMESTAMPTZ NULL,
    last_vector_reindex_status TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE IF EXISTS app_settings
    ADD COLUMN IF NOT EXISTS storage_root_path TEXT NOT NULL DEFAULT 'folders';

ALTER TABLE app_settings ALTER COLUMN storage_access_key TYPE TEXT;
ALTER TABLE app_settings ALTER COLUMN storage_secret_key TYPE TEXT;
ALTER TABLE storage_connections ALTER COLUMN access_key TYPE TEXT;
ALTER TABLE storage_connections ALTER COLUMN secret_key TYPE TEXT;

-- JPA creates ai_settings before supplemental schema initialization. Secret fields are encrypted by the entity converter.

-- Shared documentation. No business pages or export grants are seeded.
CREATE TABLE IF NOT EXISTS plugin_knowledge_pages (
    id UUID PRIMARY KEY, title VARCHAR(255) NOT NULL, parent_id UUID,
    markdown TEXT NOT NULL, assets_json TEXT NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_plugin_knowledge_parent ON plugin_knowledge_pages(parent_id);
ALTER TABLE plugin_knowledge_pages DROP CONSTRAINT IF EXISTS fk_plugin_knowledge_parent;
ALTER TABLE plugin_knowledge_pages ADD CONSTRAINT fk_plugin_knowledge_parent FOREIGN KEY (parent_id) REFERENCES plugin_knowledge_pages(id);
CREATE TABLE IF NOT EXISTS plugin_knowledge_settings (
    id INTEGER PRIMARY KEY, exporter_ids_json TEXT NOT NULL DEFAULT '[]',
    cover_html TEXT NOT NULL DEFAULT '', cover_assets_json TEXT NOT NULL DEFAULT '{}', revision BIGINT NOT NULL DEFAULT 0
);

