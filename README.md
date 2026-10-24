<img src="frontend/public/brand-mark.svg" alt="WeaveOps" width="56" height="56" />

# WeaveOps

### Bring your documents, workflows, and business data together.

[![CI: GitHub Actions](https://img.shields.io/badge/CI-GitHub_Actions-2088FF.svg)](.github/workflows/pipeline.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Self hosted](https://img.shields.io/badge/Deployment-Self_hosted-334155.svg)](docs/deployment.md)

**WeaveOps is an extensible, self-hosted business operations workspace.** Give your team one place to work with client files, create documents, follow processes, find information, and explore dashboards—with access controls that follow the data.

Built for growing businesses and teams building their own internal platforms. Start with the core workspace, enable the plugins you need, and make it your own with your business name, branding, and integrations.

[Get started](#quick-start) · [Explore the architecture](#architecture) · [Read the docs](docs/README.md) · [Build a plugin](docs/plugins/README.md) · [Licensing](#licensing)

## One workspace, connected capabilities

| Capability | What your team can do |
| --- | --- |
| **Files and client access** | Connect named S3-compatible buckets or Azure Blob containers. Grant specific directories or files, exclude protected material, and preview PDFs. [Access controls](docs/access-controls.md) |
| **Documents and processes** | Design reusable WeasyCanvas templates with typed and calculated fields. Generate PDFs and guide members through published workflows with saved progress, conditions, reviews, and email steps. [Guide](docs/documents-and-workflows.md) |
| **Search and optional AI** | Search permitted storage by meaning or keywords, schedule reindexing, and add document chat with configurable providers. Local search works without an AI service. [Search](docs/search-and-indexing.md) |
| **Business insights** | Bring multiple Apache Superset dashboards into the workspace, with clear navigation, role visibility, and an admin authoring area. [Dashboards](docs/dashboards.md) |
| **Communication** | Configure SMTP or transactional email, per-method notification templates and member preferences, protected PDF attachments, and incoming/outgoing webhooks. [Notifications](docs/notifications.md) |
| **Plugins that work together** | Enable boards, repository links, calendars, private member sync, messaging, and Microsoft file integrations. Install runtime ZIP extensions with pages, submenus, settings, managed data, and search. [Plugin ecosystem](docs/plugins/README.md) |
| **Workspace control** | Delegate administration, restrict member views, enable optional authenticator/email MFA, and customize branding. Saved provider secrets are encrypted; account passwords use salted bcrypt. [Administration](docs/administration.md) |

## Quick start

Install **Python 3**, **Docker Engine**, and **Docker Compose**. From a fresh clone, create private settings and choose your initial super-admin password:

```bash
git clone https://github.com/GrahamCHill/Contract-Management-Template.git
cd Contract-Management-Template
python deploy/github/setup-instance.py --local --admin-email owner@example.com
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d --remove-orphans
```

Open [localhost:3000](http://localhost:3000) and sign in with your chosen email and password. The local profile supplies MinIO storage; business records and dashboards start empty. External members have no file access until granted, and optional plugins start disabled.

Follow [development setup](docs/getting-started.md) for PowerShell and Azurite. For production, use the [deployment guide](docs/deployment.md) or [GitHub Actions deployment](docs/github-deployment.md): template releases, checks, and opt-in deployment to your Linux VM over SSH.

## Architecture

The browser uses one application entrypoint. The backend enforces view, role, ownership, and storage-path permissions; original files stay in object storage, and generated PDFs and application state live in PostgreSQL.

```mermaid
flowchart LR
    web["React workspace<br/>Members, admins, clients"]
    api["Kotlin / Spring Boot API<br/>Access controls, documents, workflows, plugins"]
    data[("PostgreSQL + pgvector<br/>Application state and search")]
    files[("S3 / Azure Blob<br/>Original files")]
    pdf["Private document services<br/>PDF rendering and optional extraction"]
    insights["Apache Superset<br/>Dashboards and authoring"]
    providers["Optional providers<br/>AI, email, calendars, messaging"]
    web --> api
    web --> insights
    api --> data & files & pdf
    api -.-> providers
```

<details>
<summary><strong>Expand the complete application and integration diagram</strong></summary>

```mermaid
flowchart TB
    people["Internal members and delegated admins<br/>Restricted external client accounts"]
    recipients["Share recipients<br/>Password-guarded PDF links and protected attachments"]
    pipeline["GitHub Actions<br/>Checks, template releases, opt-in SSH deployment"]

    subgraph runtime["Self-hosted deployment — Docker Compose / Linux VM"]
        ingress["Application entrypoint<br/>Caddy HTTPS in production"]
        web["React + TypeScript workspace<br/>nginx API and Superset proxy<br/>Branding, visible views, PDF viewer, search launcher"]

        subgraph host["Kotlin / Spring Boot application"]
            api["Application APIs and authentication<br/>JWT sessions, optional TOTP/email MFA<br/>Password-guarded share links"]
            policy["Access-control boundary<br/>Super admin / Admin / Member / External<br/>Feature caps, member views, directory and file rules"]
            files["Storage services<br/>Named connections, selected roots, uploads<br/>Filtered browsing, downloads, ZIPs, PDF policies"]
            documents["Document and workflow services<br/>Template publication, calculated fields, generated PDFs<br/>Visual designs, private guided runs, document/email steps"]
            search["Search and indexing<br/>Local matching or provider embeddings<br/>Vector/keyword retrieval, schedules, optional grounded chat"]
            communication["Notifications, sharing, and webhooks<br/>Method templates, member preferences, delivery history<br/>Watermarked/password-protected PDF email attachments"]
            plugins["Plugin host: bundled modules + runtime ZIPs<br/>Pages and submenus, encrypted settings<br/>Owned tables, role/row checks, optional search providers"]
            zipruntime["Uploaded ZIP extensions<br/>Required author metadata, super admin security switch<br/>Sandboxed pages and managed data; no server code"]
        end

        subgraph modules["Optional plugin modules"]
            boards["Kanban + development links<br/>Boards/cards with GitHub, GitLab, Gitea repos and PRs"]
            calendars["Calendar + provider plugins<br/>Daily, weekly, monthly views<br/>Internal member two-way Microsoft / Google / CalDAV sync"]
            connectors["Messaging and file plugins<br/>Telegram, Teams, WhatsApp<br/>SharePoint and OneDrive"]
        end

        subgraph workers["Private document services"]
            render["WeasyCanvas / WeasyPrint renderer<br/>Validated templates and bounded PDF generation"]
            extract["Optional content extraction service<br/>Bounded text, PDF, and DOCX processing"]
        end

        subgraph persistence["Persistent data"]
            appdb[("PostgreSQL app_db + pgvector<br/>Accounts, access policies, encrypted secrets<br/>Templates, PDF bytes, workflow runs, plugin tables<br/>Document metadata, extracted chunks, search vectors")]
            events[("MongoDB<br/>Events and audit history<br/>Excluded from search and AI retrieval")]
            objects[("S3-compatible / Azure Blob storage<br/>Original files and real directory prefixes<br/>MinIO or Azurite for local development")]
        end

        subgraph analytics["Apache Superset analytics"]
            superset["Superset web application<br/>Dashboard embeds and protected admin authoring"]
            supersetdb[("Superset metadata database<br/>Same PostgreSQL cluster in default Compose")]
            redis[("Redis<br/>Superset cache and task broker")]
            celery["Superset Celery worker + beat<br/>Analytics background tasks"]
        end
    end

    subgraph optional["Configured external services"]
        ai["Optional AI providers<br/>OpenAI-compatible, Ollama, Groq<br/>Azure OpenAI, Anthropic"]
        mail["SMTP / transactional email providers"]
        connected["Source-control, calendar, messaging<br/>and Microsoft file-service APIs"]
        hooks["Webhook consumers and signed senders"]
        sources["Configured analytics data sources"]
    end

    people --> ingress --> web
    pipeline -.->|"Tested source over SSH"| runtime
    web -->|"Application API"| api
    web -->|"Dashboard embeds / authorized authoring"| superset
    api --> policy
    policy --> files & documents & search & communication & plugins
    files --> objects
    files -->|"Discovered metadata and file changes"| search
    documents --> render
    render -->|"Generated PDF bytes"| documents
    api & policy & files & documents & search & communication & plugins --> appdb
    api -->|"Activity records"| events
    search -.->|"Content indexing when enabled"| extract
    search -.->|"Permitted excerpts / optional embeddings"| ai
    plugins --> boards & calendars & connectors
    plugins --> zipruntime
    zipruntime -->|"Scoped host bridge"| appdb
    boards -.->|"Card deadlines"| calendars
    plugins -->|"Opt-in authorized records"| search
    communication --> mail --> recipients
    recipients -.->|"Protected online link"| ingress
    communication -.->|"Registered delivery adapters"| connectors
    communication <-->|"Authenticated JSON"| hooks
    boards & calendars & connectors -.-> connected
    api -->|"Guest tokens and embed configuration"| superset
    superset --> supersetdb & redis
    superset -.-> sources
    celery --> supersetdb & redis
```

Solid lines show primary service/data paths; dotted lines show optional connections or deployment. PostgreSQL and object storage serve different purposes; MongoDB is not a search source. Redis/Celery support Superset, while application indexing and calendar jobs run in the Kotlin backend. Bundled plugins are reviewed source modules; [ZIP extensions](docs/plugins/zip-packages.md) load dynamically in sandboxed frames after super admin approval.

See [architecture](docs/architecture.md), [database layout](docs/database-layout.md), and [plugin composition](docs/plugins/composition.md) for boundaries and operational details.

</details>

## Documentation

[All guides](docs/README.md) · [Access controls and external sharing](docs/access-controls.md) · [Storage](docs/storage-connections.md) · [AI and secrets](docs/ai-search-and-secrets.md) · [API reference](docs/api.md) · [Deployment](docs/deployment.md) · [Branding](docs/branding.md)

The core is implemented and evolving. Workflows are guided member processes; external invitations and MongoDB/OCR search integration are not implemented. Provider integrations need your own configuration and credentials. Feature guides describe current behavior and limits.

## Contribute and extend

Report reproducible issues, improve the guides, or add a focused plugin. Start with [CONTRIBUTING.md](CONTRIBUTING.md), the [developer guide](docs/code-organization.md), and the [plugin authoring guides](docs/plugins/README.md). Copyrightable contributions require explicit [CLA acceptance](CLA.md) so the project can maintain both community and future commercial licensing paths.

## Licensing

**AGPL-3.0-only, with an alternative commercial licensing path.** The public license covers original WeaveOps code and the project owner's imported WeasyCanvas snapshot. Commercial use is allowed under the AGPL; a different grant requires a separate written commercial agreement. Premium commercial terms are reserved for a future offering and are not granted by this repository.

Read [LICENSE](LICENSE), the [licensing guide](LICENSING.md), and [commercial licensing notice](COMMERCIAL-LICENSE.md). Apache Superset remains Apache-2.0; other components keep their own licenses. See [third-party notices](THIRD_PARTY_NOTICES.md).
