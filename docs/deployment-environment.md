# Deployment settings and secrets

This page explains which machine/file receives each setting. Use the [step-by-step Terraform guide](terraform-deployment.md) for AWS/Azure/GCP, [Linux VM walkthrough](linux-vm-deployment.md) for manual Compose, or [GitHub deployment](github-deployment.md) for the single-host automated release layout.

## Locations

| Deployment | Application settings file | Who reads it |
| --- | --- | --- |
| Local development | Repository root `.env` | Development Compose |
| Manual single Linux VM | Repository root `.env.production` | Production Compose, with explicit `--env-file` |
| GitHub SSH releases | `/srv/weaveops/shared/.env.production` on the VM | Release helper; retained across code releases |
| Terraform split stack | `/opt/contract-app/.env.production` on **each** VM | That VM's app/services/runtime role |
| Terraform inputs | `infra/aws`, `infra/azure` or `infra/gcp`: `terraform.tfvars` and `bootstrap.auto.tfvars.json` | Terraform on your operator machine/CI |
| GitHub deployment connection | Repository's **production environment secrets** | Deploy job: host, user, SSH key and verified known-hosts entry |

A Terraform input variable configures infrastructure or first-boot bootstrap. A Compose environment value configures a running service. GitHub deployment connection secrets let the runner reach the server; they do not replace its application settings file.

The Terraform `bootstrap.auto.tfvars.json` and role environments contain plaintext secrets. Terraform's `sensitive` flag hides normal output; it does not remove them from state or VM metadata. Use private files, encrypted state/backups and trusted cloud access.

## Generate fresh settings

For a manual VM, from its cloned repository:

```bash
python3 deploy/github/setup-instance.py \
  --domain workspace.example.com --admin-email owner@example.com \
  --acme-email ops@example.com --output .env.production
chmod 600 .env.production
```

For GitHub releases, omit `--output`: the helper writes `/srv/weaveops/shared/.env.production`. For Terraform, run `python3 ../../deploy/github/setup-terraform-secrets.py --admin-email owner@example.com` **inside the selected cloud module**, before planning.

All helpers privately prompt for a password and refuse to overwrite an existing file. Use them for fresh installations. Retain settings for upgrades and rotate credentials deliberately with the corresponding database/provider.

## Required settings

| Variable | Purpose and owner |
| --- | --- |
| `APP_DOMAIN` | Public hostname only, for example `workspace.example.com` |
| `ACME_EMAIL` | Address for HTTPS certificate notices |
| `APP_PUBLIC_BASE_URL` | Full HTTPS public origin, used by links/auth flows |
| `APP_BOOTSTRAP_USERS_JSON` | Explicit initial account list; missing accounts are created, existing passwords are preserved |
| `JWT_SECRET` | Application signing key; keep stable across updates |
| `APP_SECRETS_ENCRYPTION_KEY` | Base64 32-byte wrapping key for stored API/storage/plugin secrets; keep a separate recovery copy |
| `POSTGRES_PASSWORD` / `MONGO_PASSWORD` | Database credentials; keep consistent with existing databases |
| `SUPERSET_SECRET_KEY` / `GUEST_TOKEN_JWT_SECRET` | Superset metadata/session and dashboard guest-token keys |
| `SUPERSET_ADMIN_PASSWORD` | Separate Superset authoring administrator password |
| `APP_DOCUMENT_RENDER_TOKEN` | Shared app/renderer private-service token |
| `APP_PLUGIN_RUNNER_TOKEN` | Shared app/runner private-service token |

The single-host helper generates service/database keys automatically. The Terraform helper generates matching secrets once and the modules distribute only the secrets each host role needs. In split deployments, `POSTGRES_ADMIN_PASSWORD` is the managed database bootstrap account, `POSTGRES_PASSWORD` is the app database account, and `SUPERSET_DATABASE_PASSWORD` is the separate Superset metadata account. Do not substitute one for another.

Cloud modules additionally set private host IPs, managed PostgreSQL host/admin user and TLS configuration. Keep these generated connection fields consistent with Terraform. They are not settings to guess from the public web IP.

## Optional providers

Configure through Administration or use the following environment groups on the app host. The selected configuration source matters: choosing Admin settings means the saved configuration is used instead of environment provider settings.

| Feature | Environment settings | Guide |
| --- | --- | --- |
| SMTP email | `APP_EMAIL_ENABLED`, provider `SMTP`, sender/name, `APP_EMAIL_SMTP_HOST/PORT/SECURITY/AUTH/USERNAME/PASSWORD` | [Email](email-delivery.md) |
| Transactional email API | Email provider, authorized sender, `APP_EMAIL_API_BASE_URL/API_KEY` and provider-specific fields | [Email](email-delivery.md) |
| AI chat / embeddings | `AI_ENABLED`, `AI_CHAT_*`, `AI_EMBEDDING_*`; local search works without an AI account | [AI and secrets](ai-search-and-secrets.md) |
| Storage bootstrap | `APP_SEED_STORAGE_PROVIDER/BUCKET/REGION/ENDPOINT/ACCESS_KEY/SECRET_KEY/ROOT_PATH` | [Storage](storage-connections.md) |
| Member identity | `APP_AUTH_ENTRA_*` / `APP_AUTH_GOOGLE_*` | [MFA and account security](mfa.md) |
| Search UI/content | `APP_SEARCH_ENABLED`, `APP_SEARCH_LAUNCHER_ENABLED`, `AI_INDEX_CONTENT` | [Search](search-and-indexing.md) |

Use the complete names/examples in [`.env.example`](../.env.example) and [production example](../.env.production.example); the grouped names above are not literal variable names. App passwords, provider tokens and IMAPS mailboxes can also be saved in encrypted Administration settings. SMTP sends mail; IMAPS reads a mailbox for incoming replies. They are separate connections.

AWS and Azure Terraform seed object storage through the app VM's identity. GCP starts with no file location selected. Optional provider settings and schedules stay disabled/unconfigured until an operator enables them.

## Edit and apply

On a Terraform app host:

```bash
sudoedit /opt/contract-app/.env.production
sudo chmod 600 /opt/contract-app/.env.production
cd /opt/contract-app
sudo python3 deploy/terraform/start.py --role app
```

For manual single-host Compose, edit its environment file, then recreate the affected service using the same project name and Compose files:

```bash
docker compose --project-name meshable-ops --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml up -d backend
```

For GitHub releases, edit the shared server file, then use its deployment workflow/release procedure. Changing a shell variable on your laptop does not change the server file. Changing environment values requires recreating containers; a plain container restart retains its old configuration. Admin-saved settings generally apply through their own save actions.

Use literal single-quoted dotenv values when needed, particularly for `$` in passwords. The setup helpers format them correctly. Do not `source` a Compose dotenv file into a shell: dotenv and shell syntax are different. Never print resolved `docker compose config` output into CI logs; use `config --quiet` for routine validation.

## Preserve and recover

Back up the role environment files, Terraform secret inputs/state, PostgreSQL, MongoDB and original objects to encrypted storage with restricted access. For a split stack, keep the services/runtime files as well as the app file; service tokens must agree between the app and its workers. Cloud-init retains an original environment copy under `/etc/meshable-ops/environment`; handle it with the same care.

Retain `APP_SECRETS_ENCRYPTION_KEY` during rebuilds and restores. Losing it prevents decryption of stored provider credentials and plugin settings. Changing database environment passwords does not update existing database accounts. The initialization job creates missing roles; it deliberately does not reset existing credentials.

Do not store secrets in frontend `VITE_*` values, Git commits, Docker build arguments, shared screenshots or repository variables. Use GitHub secrets for deployment credentials and the server's private environment for runtime credentials. Follow the [explicit wrapping-key rotation procedure](ai-search-and-secrets.md#stored-credentials-and-passwords) when rotation is needed.
