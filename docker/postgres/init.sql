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

-- Seed some realistic items so vector search has more meaningful content.
-- Only seed them when the items table is still empty.
INSERT INTO items (user_id, title, description, tags)
SELECT
    u.id,
    seed.title,
    seed.description,
    seed.tags
FROM users u
CROSS JOIN (
    VALUES
        (
            'Customer Support Knowledge Base',
            'Internal documentation for refunds, billing corrections, password resets, and account recovery workflows.',
            ARRAY['support', 'docs', 'billing']
        ),
        (
            'Warehouse Inventory Audit',
            'Quarterly stock reconciliation for laptops, monitors, docking stations, keyboards, and office peripherals.',
            ARRAY['inventory', 'operations', 'hardware']
        ),
        (
            'Sales Pipeline Forecast',
            'Regional opportunity tracking for enterprise deals, renewals, upsells, and quarterly revenue forecasting.',
            ARRAY['sales', 'forecast', 'revenue']
        ),
        (
            'HR Onboarding Checklist',
            'New employee onboarding tasks covering payroll setup, laptop provisioning, benefits enrollment, and policy acknowledgement.',
            ARRAY['hr', 'onboarding', 'people']
        ),
        (
            'Marketing Campaign Performance',
            'Ad campaign results for paid search, social media conversions, email click-through rates, and landing page leads.',
            ARRAY['marketing', 'campaigns', 'analytics']
        ),
        (
            'Security Incident Runbook',
            'Step-by-step response plan for phishing reports, credential compromise, malware isolation, and post-incident review.',
            ARRAY['security', 'incident', 'runbook']
        ),
        (
            'Product Roadmap Notes',
            'Upcoming feature planning for dashboards, vector search quality, admin permissions, and embedded analytics improvements.',
            ARRAY['product', 'roadmap', 'planning']
        ),
        (
            'Finance Expense Review',
            'Monthly review of travel reimbursements, software subscriptions, infrastructure spending, and budget variance notes.',
            ARRAY['finance', 'expenses', 'budget']
        ),
        (
            'Supplier Contract Tracker',
            'Renewal dates, pricing terms, support commitments, and procurement contacts for major software and hardware vendors.',
            ARRAY['procurement', 'vendors', 'contracts']
        ),
        (
            'Engineering Release Checklist',
            'Pre-release verification for Docker builds, environment variables, migrations, monitoring, and rollback readiness.',
            ARRAY['engineering', 'release', 'devops']
        )
) AS seed(title, description, tags)
WHERE u.email = 'admin@app.local'
  AND NOT EXISTS (SELECT 1 FROM items);

RESET ROLE;
