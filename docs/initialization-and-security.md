# Initialization and configuration security

Third-party ZIP extensions default off. Only a super admin can change **Administration → People & security → Security → Allow third-party extensions** or upload a package. Turning the switch off blocks installed ZIP pages, APIs, and search while retaining their data and configuration; bundled plugins are unaffected. Packages require an author file (blank values allowed), strict archive validation, and code/permission review. See [ZIP extension security](plugins/zip-packages.md#isolation-and-trust).

Fresh databases start without demo users, folders, audit events, datasets, charts, or dashboards. The PostgreSQL initializer creates only the application database and required extensions. Hibernate and `backend/src/main/resources/schema.sql` own application schema setup; Docker no longer duplicates those table/index definitions. MongoDB's initializer retains its collection/index setup without inserting a sample event.

## Initial access

Create private local settings with Python 3:

```bash
python deploy/github/setup-instance.py --local --admin-email owner@example.com
```

The helper prompts privately for your password, creates a new `.env`, and generates database, JWT, Superset, wrapping, renderer, and development-storage secrets. It refuses to overwrite an existing file. For production, omit `--local`, provide `--domain`/`--acme-email`, and select `--output` when needed; see [GitHub deployment](github-deployment.md).

`APP_BOOTSTRAP_USERS_JSON` is an explicit, create-only account bootstrap mechanism. Empty configuration creates no accounts in any profile. New accounts need valid unique identities, `SUPER_ADMIN`/`ADMIN`/`USER`/`EXTERNAL` roles, and passwords between 14 characters and 72 UTF-8 bytes. An empty installation needs an admin or super admin; the initial active admin is promoted once to `SUPER_ADMIN`. See [access controls](access-controls.md). Passwords are hashed using the application's salted bcrypt encoder. Existing identities are skipped: startup does not reset passwords, roles, profiles, or deactivation state. After confirming initial access, clear this bootstrap setting and manage further users in Administration.

Superset also creates its explicitly configured authoring admin only when absent, using its security manager's password hashing. Restarting does not reset that account's password. Dashboards, datasets, and app links are authored/configured through the admin workspace; there is no automatic sample-dashboard service.

## Required runtime secrets

Compose requires explicit `POSTGRES_PASSWORD`, `MONGO_PASSWORD`, `JWT_SECRET`, `SUPERSET_SECRET_KEY`, `GUEST_TOKEN_JWT_SECRET`, and `SUPERSET_ADMIN_PASSWORD`. The application has no working fallback database/JWT/Superset passwords. JWT keys are validated at startup. Native backend runs also need `MONGO_URI` and the other required environment values; Spring does not load `.env` automatically.

`.env.example` and `.env.production.example` are blank references, not runnable credential sets. The setup helper generates safe values and preserves the server wrapping key across updates. MinIO/Azurite use generated credentials, and their host ports bind to loopback. Production exposes only Caddy; Compose 2.24.4 or later is required for the port reset.

AWS/Azure cloud-init calls `provision-secrets.py` once to add missing database/wrapping/rendering fields to its private environment file. Existing values, including blank assignments, are retained for deliberate operator review. It does not rotate credentials for an existing database.

## Existing installations

### Browser sessions and transport

Authentication now uses a server-recorded browser session in `user_browser_sessions`. The JWT and a separate random browser proof are sent only in HttpOnly cookies. Only proof/CSRF hashes are stored server-side, and the signed JWT binds the session ID to the proof hash. Copying the JWT or old local-storage data alone cannot authenticate another browser. Logout revokes the server record; expiry and account/MFA session-version checks still apply. Copying every cookie from a compromised browser can still compromise that session; this is not hardware-bound authentication.

Login responses no longer expose a usable JWT. The frontend clears legacy JWT storage and retains only user display data and a non-secret, user-specific UI marker. Unsafe authenticated requests require the session's CSRF header; cross-site write/login requests are rejected. Authenticated responses use `Cache-Control: no-store`, and referrer headers are suppressed. The API client removes Authorization headers and rejects requests to another origin or non-HTTPS APIs outside localhost before transmission.

Production requires HTTPS, `APP_AUTH_COOKIE_SECURE=true` (the default), and an exact trusted `CORS_ALLOWED_ORIGINS` configuration. Production Compose sets that origin to `https://${APP_DOMAIN}`. Secure cookies use the `__Host-` prefix, Path `/`, no Domain and SameSite Strict; authentication/proof cookies are HttpOnly. Serve the frontend and API through the same origin so the CSRF cookie is available to the frontend. Only loopback development should set the secure-cookie flag false. The frontend also supplies HSTS for HTTPS clients. Existing unbound tokens are rejected: users must sign in again after deploying this update. Rebuild/restart the backend and frontend to activate these changes; the schema update must include the new session table. Rebuild the WeasyPrint service as well to enable the new report pages and cover designer.

See the [OWASP session guidance](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) and [browser storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html).

These code changes do not delete existing users, folders, events, dashboards, or database volumes. Removing startup seeds cannot erase old seeded records. Back up your data, create/verify an intended admin, deactivate unwanted accounts, and remove unwanted content through the relevant administration tools. Review old Superset authoring users too.

Set environment secrets to match the credentials already used by existing databases. Changing a Compose variable alone does not change a stored database password. Rotate old known passwords deliberately with the corresponding service's administrative tools and update dependent connections together. Preserve the current wrapping key and JWT secrets unless intentionally rotating them. Never delete volumes just to apply this update.

## Verification

Search/AI crawling is disallowed globally by robots.txt, HTML metadata, and response headers on frontend/backend/proxy paths. These directives complement authenticated access and do not guarantee that an uncooperative scraper stays away. See [crawler exclusion](crawler-exclusion.md) for deployment, verification, and existing-index limitations.

Regression checks cover empty bootstrap behavior, salted hashes, unchanged existing accounts, invalid configuration redaction, Superset account preservation, required Compose secrets, and generated environment settings. Configuration tests generate temporary credentials and never use a real `.env`.

The duplicated SQL definitions were removed rather than excluded from analysis. Run SonarCloud again after publishing the changes to verify its updated duplication and security findings. Local tests do not establish a new Sonar rating or a successful live database/cloud deployment.

## Optional external sign-in

Microsoft Entra ID and Google can be enabled independently through backend environment variables. Existing invite-only users connect their provider identity from Security & MFA after signing in; matching email claims never grant access automatically. See [external login setup and account lifecycle](external-login.md).
