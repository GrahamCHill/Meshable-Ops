-- Superset uses the 'superset' DB (created by POSTGRES_DB env var).
-- This creates a separate app DB for the Java backend.

CREATE USER app_user WITH PASSWORD 'app_pass';
CREATE DATABASE app_db OWNER app_user;

\connect app_db

SET ROLE app_user;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    username   VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    tags        TEXT[],
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS item_tags (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_tags_item_id ON item_tags (item_id);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER items_updated_at BEFORE UPDATE ON items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Seed admin user (password: admin123)
INSERT INTO users (email, username, password, role)
VALUES ('admin@app.local', 'admin',
        '$2a$12$5hHKz9z7P3EivIV3xYkppu.LIEbUY6GHYiMpI1S0N1HLJVfKjBmES', 'ADMIN')
ON CONFLICT DO NOTHING;

-- Seed some sample items so Superset has data to visualise
INSERT INTO items (user_id, title, description, tags)
SELECT id, 'Sample Item ' || g, 'Description for item ' || g, ARRAY['demo', 'seed']
FROM users, generate_series(1, 20) g
WHERE email = 'admin@app.local';

RESET ROLE;
