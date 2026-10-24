# Contributing to WeaveOps

Help improve business workflows, documentation, accessibility, integrations, and the plugin ecosystem. Use the [issue tracker](https://github.com/GrahamCHill/Contract-Management-Template/issues) to describe a reproducible problem or discuss a substantial change before implementation.

## Start locally

Follow [development setup](docs/getting-started.md), then read the [architecture](docs/architecture.md), [code organization](docs/code-organization.md), and [access-control model](docs/access-controls.md). Build optional functionality through the [plugin contracts](docs/plugins/README.md) when it belongs outside the core.

Keep pull requests focused, update the relevant guide, and explain the changed behavior and validation. Exercise disabled features, direct API access, row ownership, storage exclusions, and stale writes when changing permission-sensitive paths. Never include `.env` files, credentials, client documents, database exports, or live access tokens.

Run checks appropriate to your change:

```bash
# Java 21 and Maven, from backend/
mvn --batch-mode --no-transfer-progress verify

# Node.js 22, from frontend/
npm ci
npm run build
```

Python services have separate dependency environments and tests; see [code organization](docs/code-organization.md). Documentation-only changes need working relative links and valid Mermaid diagrams. Keep the README architecture diagram consistent with the runtime. GitHub Actions runs the shared [checks workflow](.github/workflows/checks.yml) for pull requests; deployment is opt-in and is not run for pull requests.

## Licensing contributions

The public project license is [AGPL-3.0-only](LICENSE), with a [separate commercial licensing path](COMMERCIAL-LICENSE.md). Copyright stays with contributors. Before a copyrightable contribution is merged, its rights holder must explicitly accept the [CLA](CLA.md), which permits both AGPL publication and future commercial sublicensing. A PR submission or commit sign-off alone does not grant those additional rights. Obtain employer/client permission where required, and identify upstream material and its license.

Maintainers must retain a record of explicit acceptance for the covered revisions. Existing work without that grant cannot simply be included in a proprietary release. See [licensing](LICENSING.md) and [third-party notices](THIRD_PARTY_NOTICES.md).
