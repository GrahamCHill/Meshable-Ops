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

The project uses the custom source-available [Meshable Ops Community License](LICENSE), with [separate commercial, proprietary and extra-branding exceptions](COMMERCIAL-LICENSE.md). Community modifications and forks do not require individual approval when they preserve the same community terms, meet recipient/network-user source duties and provide the mandatory upstream grant-back in LICENSE section 3.1. Fork authors retain copyright but must grant the project rights to reuse, modify, merge and sublicense fork changes in community and commercial/proprietary releases, and supply their source within 30 days of a written request. The grant applies without submitting a PR or signing the separate CLA. Obtain employer/client/contributor authority before incorporating changes, and identify third-party material and its license.

For a direct copyrightable submission, the repository additionally requires explicit [CLA acceptance](CLA.md) for the identified revisions before merging. The CLA documents contributor authority and commercial sublicensing; a PR submission or DCO sign-off alone is not acceptance. Extra branding beyond exposed customization, application paywalls and proprietary/source-sharing exceptions still need a written agreement from the licensor or an expressly authorized Licensing Maintainer.

Original plugin functionality submitted for inclusion follows the app's community license. Independently authored service-integration components may retain a license compatible with the actual combination with appropriate notices; independent external services retain their own terms. Identify those portions explicitly and follow the [plugin licensing policy](LICENSING.md#plugins-and-external-service-integrations). The allowance does not exempt covered plugin functionality merely because it calls an external API.

Maintainers must retain the applicable license/grant-back version, source provenance, rights-holder authority and explicit CLA acceptance for directly submitted revisions. Existing work without adequate ownership, grant-back, CLA or separate permission cannot simply be included in a proprietary release. See [licensing](LICENSING.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

Community plugin developers may sell copies, offer paid subscriptions, or charge for hosting/support and keep their revenue. Covered plugins retain the same-license source/notice conditions and permitted recipient rights; closed-source covered plugins need an express exception. Publishing an independently developed plugin outside the definition of fork changes does not automatically accept the CLA or grant relicensing rights. Plugins originally developed as part of a fork follow section 3.1. See the [paid-plugin allowance](CLA.md#paid-community-plugins).
