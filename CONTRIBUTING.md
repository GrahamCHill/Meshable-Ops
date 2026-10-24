# Contributing to Meshable Ops

Help improve business workflows, documentation, accessibility, integrations, and the plugin ecosystem. Use the [issue tracker](https://github.com/GrahamCHill/Contract-Management-Template/issues) to describe a reproducible problem or discuss a substantial change before implementation.

## Start locally

Follow [development setup](docs/getting-started.md), then read the [architecture](docs/architecture.md), [code organization](docs/code-organization.md), and [access-control model](docs/access-controls.md). Build optional functionality through the [plugin contracts](docs/plugins/README.md) when it belongs outside the core.

Keep pull requests focused, update the relevant guide, and explain the changed behavior and validation. Exercise disabled features, direct API access, row ownership, storage exclusions, and stale writes when changing permission-sensitive paths. Never include `.env` files, credentials, client documents, database exports, or live access tokens.

Run checks appropriate to your change:

```bash
# Java 21 and Maven, from backend/
mvn --batch-mode --no-transfer-progress verify

# Node.js 22, from frontend/
npm ci --ignore-scripts
npm run build
```

Python services have separate dependency environments and tests; see [code organization](docs/code-organization.md). Documentation-only changes need working relative links and valid Mermaid diagrams. Keep the README architecture diagram consistent with the runtime. GitHub Actions runs the shared [checks workflow](.github/workflows/checks.yml) for pull requests; deployment is opt-in and is not run for pull requests.

## Licensing contributions

The project uses the custom source-available [Meshable Ops Community License](LICENSE), with a [separate commercial licensing path](COMMERCIAL-LICENSE.md). Copyright stays with contributors. Before a copyrightable contribution is merged, its rights holder must explicitly accept the [CLA](CLA.md), which permits community publication and separate commercial sublicensing. A PR submission or commit sign-off alone does not grant those additional rights. Obtain employer/client permission where required, and identify upstream material and its license.

Original plugin functionality submitted for inclusion follows the app's community license. Independently authored service-integration components may retain a license compatible with the actual combination with appropriate notices; independent external services retain their own terms. Identify those portions explicitly and follow the [plugin licensing policy](LICENSING.md#plugins-and-external-service-integrations). The allowance does not exempt covered plugin functionality merely because it calls an external API.

Maintainers must retain a record of explicit acceptance for the covered revisions. Existing work without that grant cannot simply be included in a proprietary release. See [licensing](LICENSING.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

Community plugin developers may sell copies, offer paid subscriptions, or charge for hosting/support and keep their revenue. Covered plugins retain the community source/notice conditions and permitted recipient rights. Publishing an independent plugin does not automatically accept the CLA or transfer relicensing rights. See the [paid-plugin allowance](CLA.md#paid-community-plugins).
