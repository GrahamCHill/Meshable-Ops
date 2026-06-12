CREATE TABLE IF NOT EXISTS item_tags (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_tags_item_id ON item_tags (item_id);

CREATE TABLE IF NOT EXISTS dashboard_embed_configs (
    id UUID PRIMARY KEY,
    label TEXT NOT NULL,
    description TEXT NULL,
    dashboard_id TEXT NOT NULL UNIQUE,
    guest_token_resource_id TEXT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dashboard_embed_configs_sort_order
    ON dashboard_embed_configs (sort_order, created_at);
