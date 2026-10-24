# Initialization and configuration security

Fresh databases start without demo users, folders, audit events, datasets, charts, or dashboards. The PostgreSQL initializer creates only the application database and required extensions. Hibernate and `backend/src/main/resources/schema.sql` own application schema setup; Docker no longer duplicates those table/index definitions. MongoDB's initializer retains its collection/index setup without inserting a sample event.

## Initial access

Create private local settings with Python 3:

```bash
python deploy/github/setup-instance.py --local --admin-email owner@example.com
```

The helper prompts privately for your password, creates a new `.env`, and generates database, JWT, Superset, wrapping, renderer, and development-storage secrets. It refuses to overwrite an existing file. For production, omit `--local`, provide `--domain`/`--acme-email`, and select `--output` when needed; see [GitHub deployment](github-deployment.md).

`APP_BOOTSTRAP_USERS_JSON` is an explicit, create-only account bootstrap mechanism. Empty configuration creates no accounts in any profile. New accounts need valid unique identities, `ADMIN`/`USER` roles, and passwords between 14 characters and 72 UTF-8 bytes. An empty installation needs an admin. Passwords are hashed using the application's salted bcrypt encoder. Existing identities are skipped: startup does not reset passwords, roles, profiles, or deactivation state. After confirming initial access, clear this bootstrap setting and manage further users in Administration.

Superset also creates its explicitly configured authoring admin only when absent, using its security manager's password hashing. Restarting does not reset that account's password. Dashboards, datasets, and app links are authored/configured through the admin workspace; there is no automatic sample-dashboard service.

## Required runtime secrets

Compose requires explicit `POSTGRES_PASSWORD`, `MONGO_PASSWORD`, `JWT_SECRET`, `SUPERSET_SECRET_KEY`, `GUEST_TOKEN_JWT_SECRET`, and `SUPERSET_ADMIN_PASSWORD`. The application has no working fallback database/JWT/Superset passwords. JWT keys are validated at startup. Native backend runs also need `MONGO_URI` and the other required environment values; Spring does not load `.env` automatically.

`.env.example` and `.env.production.example` are blank references, not runnable credential sets. The setup helper generates safe values and preserves the server wrapping key across updates. MinIO/Azurite use generated credentials, and their host ports bind to loopback. Production exposes only Caddy; Compose 2.24.4 or later is required for the port reset.

AWS/Azure cloud-init calls `provision-secrets.py` once to add missing database/wrapping/rendering fields to its private environment file. Existing values, including blank assignments, are retained for deliberate operator review. It does not rotate credentials for an existing database.

## Existing installations

These code changes do not delete existing users, folders, events, dashboards, or database volumes. Removing startup seeds cannot erase old seeded records. Back up your data, create/verify an intended admin, deactivate unwanted accounts, and remove unwanted content through the relevant administration tools. Review old Superset authoring users too.

Set environment secrets to match the credentials already used by existing databases. Changing a Compose variable alone does not change a stored database password. Rotate old known passwords deliberately with the corresponding service's administrative tools and update dependent connections together. Preserve the current wrapping key and JWT secrets unless intentionally rotating them. Never delete volumes just to apply this update.

## Verification

Search/AI crawling is disallowed globally by robots.txt, HTML metadata, and response headers on frontend/backend/proxy paths. These directives complement authenticated access and do not guarantee that an uncooperative scraper stays away. See [crawler exclusion](crawler-exclusion.md) for deployment, verification, and existing-index limitations.

Regression checks cover empty bootstrap behavior, salted hashes, unchanged existing accounts, invalid configuration redaction, Superset account preservation, required Compose secrets, and generated environment settings. Configuration tests generate temporary credentials and never use a real `.env`.

The duplicated SQL definitions were removed rather than excluded from analysis. Run SonarCloud again after publishing the changes to verify its updated duplication and security findings. Local tests do not establish a new Sonar rating or a successful live database/cloud deployment.
