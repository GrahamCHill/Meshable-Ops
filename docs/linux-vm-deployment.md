# Deploy manually to an existing Linux VM

This walkthrough runs the production Compose stack on one existing VM. The [AWS/Azure/GCP Terraform deployments](terraform-deployment.md) use managed PostgreSQL and three separate hosts instead. The [GitHub Actions guide](github-deployment.md) provides automated single-host releases with a different directory layout.

## 1. Prepare the host

Use a dedicated Ubuntu 24.04 LTS VM with capacity for the full stack and image builds; review [sizing](deployment.md#sizing-guidance). Create a normal deployment account with SSH key access and sudo. Restrict SSH to your administrator/runner network. Allow public TCP 80/443, and leave database/API/Python service ports closed.

On Ubuntu, install the prerequisites:

```bash
sudo apt-get update
sudo apt-get install -y git python3 curl ca-certificates docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Log out and reconnect so the new group takes effect. Membership of the Docker group grants control over the host. Run `docker compose version` and confirm **2.24.4 or later**; see [Docker installation](https://docs.docker.com/engine/install/ubuntu/) if your distribution packages are older. Keep the host updated.

## 2. Check out your release

As the deployment account, choose a permanent application directory:

```bash
sudo install -d -o "$(id -u)" -g "$(id -g)" -m 700 /srv/meshable-ops
git clone https://github.com/YOUR-ORG/YOUR-REPO.git /srv/meshable-ops/app
cd /srv/meshable-ops/app
git checkout YOUR_RELEASE_TAG_OR_BRANCH
```

Replace the repository/ref before running. Authenticate private repositories using your normal Git account/deploy-key mechanism; do not embed tokens in URLs. Keep the directory, Compose project name and environment file consistent for future updates.

## 3. Save runtime credentials on the VM

From `/srv/meshable-ops/app`:

```bash
python3 deploy/github/setup-instance.py \
  --domain workspace.example.com \
  --admin-email owner@example.com \
  --acme-email ops@example.com \
  --output .env.production
chmod 600 .env.production
```

The helper privately prompts for the app admin password, creates distinct database/service keys and refuses to overwrite an existing file. The file is **`/srv/meshable-ops/app/.env.production` on this VM**. Keep an encrypted recovery copy before starting. The administrator you choose becomes `SUPER_ADMIN`; the Superset authoring password is generated separately in the private file.

Optional email, AI and file-storage settings can be saved through Administration after login, or added here deliberately. See [exact environment locations and variable groups](deployment-environment.md). Do not generate fresh keys for an existing database.

## 4. Set DNS and start services

In your DNS provider, point `workspace.example.com` at the VM's stable public IPv4 address. Remove an unrelated AAAA record unless IPv6 is configured. Confirm no other host service binds ports 80/443, then run:

```bash
docker compose --project-name meshable-ops --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose --project-name meshable-ops --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml up --build -d --wait --wait-timeout 900
```

The first build/initialization can take time. Use the same `--project-name` on every later command. Caddy obtains the HTTPS certificate once DNS and ports 80/443 reach the VM. All stateful services in this manual layout use local Docker volumes; arrange backups and host monitoring.

## 5. Verify and configure

```bash
docker compose --project-name meshable-ops --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml ps --all
curl --fail https://workspace.example.com/api/health
```

Open the HTTPS hostname and sign in with the email/password chosen during setup. Enroll MFA, set branding/access controls, connect named file locations and enable the plugins you need. AI, outgoing email and schedules remain optional until configured. Verify a real email delivery before enabling email MFA or automatic notifications.

For failed HTTPS issuance, inspect Caddy logs using the same Compose options and `logs --tail=100 caddy`. For startup failures, inspect the failed service's logs privately. Do not post resolved environment output or credentials in support issues.

## 6. Update and recover

Back up PostgreSQL, original files and `.env.production` before updating. Preserve the wrapping key and existing database passwords. Check out a reviewed release, then rerun the same Compose build/start command. Do not delete volumes during an upgrade. Read [upgrade and recovery](deployment.md#upgrading-an-existing-installation), and use the [GitHub release deployment guide](github-deployment.md) if you want automated releases rather than manual Git/Compose updates.
