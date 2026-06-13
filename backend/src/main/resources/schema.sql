ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE users
SET active = TRUE
WHERE active IS NULL;

ALTER TABLE IF EXISTS users
    ALTER COLUMN active SET DEFAULT TRUE;

ALTER TABLE IF EXISTS users
    ALTER COLUMN active SET NOT NULL;

CREATE TABLE IF NOT EXISTS item_tags (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_tags_item_id ON item_tags (item_id);

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
