# Template versions and GitHub deployment

Each copy of Meshable Ops can run its own checks and deploy to an existing Linux VM over SSH. Actions sends the tested source revision and Docker builds it on the VM; no registry account is needed. Deployment stays off until the new repository's owner configures it.

To provision the host and its network first, use [AWS or Azure Terraform](terraform-deployment.md). The bundled Actions deployment expects direct SSH reachability from its runner. For Azure's private VM, use a Linux runner with private connectivity and set `DEPLOY_RUNNER` to its label; the public application load balancer does not forward SSH, and this workflow does not automatically create a Bastion tunnel. AWS's trusted SSH source must also include the deployment runner. Terraform bootstrap deploys the app independently of Actions. The shared checks validate both cloud modules with mocked providers and never apply cloud infrastructure.

## Create your copy

The upstream owner enables **Settings > General > Template repository** once. Users can then choose **Use this template > Create a new repository**. GitHub copies files, not deployment secrets or host configuration. See [GitHub's template guide](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

To publish a stable starting point, run **Actions > Release template version** from the default branch and enter a version such as `1.0.0`. Checks must pass before it creates tag/release `template-v1.0.0` and matching branch `template/v1.0.0`. Published refs are never moved by the workflow; choose a new version for updates.

To use a particular version, choose **Include all branches** when creating from the template. Create your own working branch from the desired version:

```bash
git clone git@github.com:YOUR-ORG/YOUR-REPO.git
cd YOUR-REPO
git switch -c production origin/template/v1.0.0
git push -u origin production
```

Set `DEPLOY_BRANCH=production` below. Branch histories copied from a template are independent; use your working branch for subsequent changes. Alternatively, fork the repository and branch from a release tag. Creating a repository does not provision a server or copy upstream secrets.

## Prepare a dedicated Linux VM once

Install Docker Engine, Docker Compose **2.24.4 or later**, Bash, Python 3, `curl`, `flock`, and OpenSSH. The SSH account must own `/srv/weaveops` and be able to run Docker, which grants control over the host. Containers and volumes use Compose project names. Separate installations need distinct project names, release/data directories, and published ports. Builds run here too; allow memory/disk space beyond the [runtime sizing guide](deployment.md#sizing-guidance).

Point your hostname at the VM. Allow inbound HTTP/HTTPS on `80/443` and SSH from your runner/network. Set up a dedicated deployment SSH key and put its public key in the account's `authorized_keys`.

Clone your repository on the VM once to access the setup helper. Have an administrator create `/srv/weaveops` and give the SSH account ownership, then run as that account:

```bash
install -d -m 700 /srv/weaveops/shared
python3 deploy/github/setup-instance.py \
  --domain workspace.example.com \
  --admin-email owner@example.com \
  --acme-email ops@example.com
```

The helper privately prompts for the initial app admin password, generates distinct application/database/rendering secrets, and writes `/srv/weaveops/shared/.env.production` with mode `600`. It refuses to overwrite an existing file and does not start services. Back up this file and its wrapping key. SMTP, AI, and storage are optional; configure them in Administration or edit the server environment file deliberately.

Every fresh deployment uses the same PostgreSQL initializer without development users or sample folders. The app creates the configured admin with a salted password hash and preserves it on later starts. The separate Superset authoring password is in the private server file. No sample dashboards or events are inserted. Common Compose configuration supplies generated database credentials; no separate GitHub database initializer/overlay is needed.

This setup targets a fresh, independent installation. Do not apply generated credentials to existing volumes: environment changes do not rotate existing database passwords. Migrating an installation requires retaining its keys, matching database credentials, reviewing existing accounts, and choosing its existing Compose project/volumes deliberately.

## Configure GitHub

Create a **production** environment under **Settings > Environments**. Restrict it to the deployment branch and optionally add required reviewers. Availability of protection features depends on your plan; see [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

Add these **environment secrets**:

| Secret | Value |
| --- | --- |
| `DEPLOY_HOST` | VM DNS name or IPv4 address, without a scheme |
| `DEPLOY_USER` | Linux deployment account |
| `DEPLOY_SSH_KEY` | Dedicated private SSH key without an interactive passphrase |
| `DEPLOY_KNOWN_HOSTS` | Verified OpenSSH known-hosts entry for the VM |

Verify the server's SSH host-key fingerprint through the provider console or another trusted channel. For a nonstandard port, use a `[host]:port` known-hosts entry. Deployment enforces verification and never accepts a newly discovered host key automatically.

Add these **repository variables** under **Settings > Secrets and variables > Actions > Variables**:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DEPLOY_BRANCH` | Repository default branch | Branch allowed to deploy; use `production` for the versioned example |
| `AUTO_DEPLOY` | Off | Set exactly `true` to deploy successful pushes on that branch |
| `DEPLOY_PATH` | `/srv/weaveops` | Absolute host directory; letters, numbers, `_`, `-`, `/` only |
| `DEPLOY_PORT` | `22` | SSH port |
| `DEPLOY_RUNNER` | `ubuntu-24.04` | Deployment runner label; use a dedicated self-hosted Linux label for private connectivity or a trusted fixed outbound IP |

`DEPLOY_BRANCH`, `AUTO_DEPLOY`, and `DEPLOY_RUNNER` must be repository variables: they control job scheduling before entering the environment. Deployment secrets are read only by the deploy job. Application/database secrets stay on the server. The selected runner needs Bash, Git, OpenSSH, and an actual network path to the target; a label does not provision connectivity.

## Deploy and update

For a first manual deployment, run **Actions > Test and deploy > Run workflow**, selecting the deployment branch. For automatic deployments, set `AUTO_DEPLOY=true`, then push changes to that branch.

Pull requests and pushes run frontend compilation, backend tests/package verification, Python tests, workflow/script checks, and Compose validation. Deployment requires every check to pass and the configured branch to match. Pull requests cannot deploy. Packaging uses the same tested commit. Official Actions are pinned; Dependabot proposes updates.

Only tracked runtime sources are shipped, excluding `.git`, local environments, build outputs, and local tools. The server:

1. Extracts source into `releases/<commit>-<run>-<attempt>`.
2. Validates secrets, production settings, private service ports, and account initialization.
3. Builds/pulls images and waits for services and public HTTPS health.
4. Advances `current`, `previous`, and `deployed-release` after success.

Named volumes use the stable `weaveops` Compose project. Updates never replace/print the shared environment file, run `down -v`, or delete prior releases/data. Actions and the host serialize deployments.

If startup fails, the run fails and `current` is not advanced. Containers or schemas may already have changed; there is no automatic database rollback. Inspect service status without dumping environment values and use backups for recovery. To deliberately reapply an older retained source revision:

```bash
bash /srv/weaveops/releases/RELEASE-ID/deploy/github/deploy.sh /srv/weaveops RELEASE-ID
```

Keep the same secrets and volumes. Review [upgrade/backup guidance](deployment.md#upgrading-an-existing-installation) before schema changes. Configuration checks do not replace testing your VM's SSH, DNS, TLS, and database startup.
