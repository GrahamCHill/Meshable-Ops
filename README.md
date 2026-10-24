# WeaveOps

**Documents, workflows, data, and insights in one business workspace.**

WeaveOps brings everyday operations together for small and medium businesses and larger organizations. Connect shared files, create documents from reusable templates, map processes, and explore dashboards through one application.

## What it does

- **Shared storage:** named S3-compatible buckets and Azure Blob containers, selected folder access, uploads, and in-app PDF viewing.
- **Documents:** WeasyCanvas template editor, typed and calculated fields, quote/invoice starters, and a generated PDF library.
- **Workflows:** a visual flowchart builder with document, review, condition, and email blocks. This version saves and validates designs; email sends are manual.
- **Search and AI:** storage similarity search, scheduled indexing, and optional document chat with configurable providers. Ordinary search works without AI.
- **Insights:** multiple embedded Apache Superset dashboards with role-based visibility and an admin authoring workspace.
- **Communication and control:** SMTP/email services, protected PDF attachments, incoming/outgoing webhooks, optional MFA, encrypted credentials, and admin controls.
- **Plugins:** optional pages, submenus, database roles, and searchable records, with a Kanban example. [Developer guide](docs/plugins/README.md).

Built with React/TypeScript, Kotlin/Spring Boot, PostgreSQL/pgvector, and a private WeasyPrint renderer. Docker Compose brings the services together.

## Quick start

Install Python 3, Docker Engine, and Docker Compose. Create private settings and choose your initial admin password:

```bash
python deploy/github/setup-instance.py --local --admin-email owner@example.com
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d --remove-orphans
```

Open [localhost:3000](http://localhost:3000) and sign in with the email and password you chose. No demo accounts, folders, or dashboards are created.

See [development setup](docs/getting-started.md) for PowerShell, Azurite, local credentials, and service checks. Follow the [deployment guide](docs/deployment.md) for production configuration, account setup, HTTPS, and backups.

## Documentation

Start with the [documentation index](docs/README.md).

| Guide | Covers |
| --- | --- |
| [Administration](docs/administration.md) | Settings, users, permissions, and security |
| [Template deployment](docs/github-deployment.md) · [Branding](docs/branding.md) | GitHub Actions, your own VM, logo, and favicon |
| [Storage](docs/storage-connections.md) | Connections, shared folders, and uploads |
| [Documents and workflows](docs/documents-and-workflows.md) | Templates, generated PDFs, email, and flowcharts |
| [Search and indexing](docs/search-and-indexing.md) | Feature controls, schedules, and optional AI |
| [Dashboards](docs/dashboards.md) | Superset authoring and shared insights |
| [Architecture](docs/architecture.md) · [Developer guide](docs/code-organization.md) · [API](docs/api.md) | Services, source layout, checks, and integrations |
