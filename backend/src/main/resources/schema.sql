CREATE TABLE IF NOT EXISTS item_tags (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_tags_item_id ON item_tags (item_id);
