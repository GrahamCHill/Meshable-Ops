# Meshable Ops licensing

Copyright (C) 2026 Graham Hill and the Meshable Ops contributors.

## Two licensing paths

Original Meshable Ops code is offered under either:

1. **GNU Affero General Public License, version 3 only (`AGPL-3.0-only`)**, whose complete, unmodified text is in [LICENSE](LICENSE); or
2. **A separate commercial license**, if you obtain an express written agreement from the relevant copyright holder or an authorized commercial licensor.

The AGPL option is available immediately. This repository does not grant a commercial license, publish commercial contract terms, or promise a premium edition. A future commercial agreement can provide alternative permissions for the Meshable Ops code covered by that agreement. Merely paying for hosting, support, or a subscription does not grant those permissions unless the agreement expressly says so.

Contact the [project maintainer](https://github.com/GrahamCHill) to discuss commercial licensing. A future premium offering can include separately licensed original modules and services; its scope and terms must be stated in its own agreement. Existing AGPL permissions for released copies remain available under the AGPL; a commercial offering does not revoke them.

Package metadata identifies the currently published public license as `AGPL-3.0-only`. There is no public commercial license text to add to an SPDX `OR` expression.

## Code covered by this choice

Except where an upstream or file-specific notice says otherwise, this choice covers the original application frontend/backend, original plugins, deployment tooling, documentation, and original rendering-service integration in this repository.

It also covers the project owner's imported **WeasyCanvas v0.8.0 source snapshot** in `frontend/src/vendor/weasycanvas/` and `weasy-renderer/backend/`. That source is included with the owner's authorization under this repository's licensing choice. This does not change the license of WeasyPrint or any other dependency, or edit the separate WeasyCanvas source repository.

Copyright stays with each author unless a separate agreement assigns it. The root license cannot grant rights that an author or upstream project has not supplied. Third-party dependencies, container images, copied upstream notices, trademarks, and separately identified assets retain their own terms. See [third-party notices](THIRD_PARTY_NOTICES.md).

## What the AGPL option means

The AGPL is a strong copyleft **open source** license. It permits commercial use and charging for software, hosting, or support while imposing its source and redistribution requirements. It is not a prohibition on competitors, commercial use, or particular industries.

Among other obligations, distributing covered software requires satisfying the AGPL's license/notice and Corresponding Source provisions. Under section 13, if you modify the program and users interact with that version remotely over a network, that version must prominently offer those users its Corresponding Source at no charge. Internal network users can also be users of a modified deployment. A source offer should identify the actual running revision and include the necessary covered source and build/install material; a link to an unrelated or outdated public branch is insufficient.

Corresponding Source concerns covered software. Ordinary business records, uploaded client files, credentials, and generated documents do not become AGPL-licensed merely because they are processed by Meshable Ops. Material copied from licensed code or assets into an output can have separate obligations. The license itself controls the exact requirements.

LGPL would allow more proprietary linking, and ordinary GPL does not add the AGPL's network-interaction condition. AGPLv3 is the public-license choice here because this application is used through a browser and server.

## Apache Superset compatibility

Apache Superset and its embedded SDK retain **Apache-2.0** licensing. Apache-2.0 code can be included in GPLv3-compatible copyleft works while preserving the Apache license and applicable notices; this project uses the AGPLv3 family and keeps Superset as a separate service. Meshable Ops does **not** relicense Superset, claim Apache endorsement, or grant proprietary rights to Apache or other upstream code through its commercial option.

The project's licensing choice does not make Meshable Ops code eligible for inclusion in an Apache-licensed upstream project on Apache terms. Nor does compatibility with Superset establish that every dependency or future plugin has been audited. Redis, MongoDB, development storage, and other services have independent version-specific terms; preserve their notices and assess the exact distribution and hosting model.

## Plugins and external-service integrations

**Plugins that form a covered work with Meshable Ops inherit the application's licensing.** This includes plugins that incorporate or adapt app code, or combine with it as a single program. Under the community licensing path, the covered application/plugin combination must be available under **AGPL-3.0-only**, with the applicable notices, Corresponding Source, and network-source-offer obligations. App-derived plugin code cannot be made proprietary merely by packaging it as a plugin. React, Kotlin, Python, ZIP packaging, dynamic loading, a separate directory, or a separate process do not by themselves determine the licensing boundary.

For plugins accepted into this repository, original Meshable Ops plugin functionality must be made available under the application's community license, subject to the integration-component allowance below and any separately identified upstream material. The existing contributor agreement and commercial licensing rules still apply. A commercial alternative applies only where an executed agreement expressly supplies the necessary rights for the relevant application and plugin code; it does not automatically cover third-party plugins or dependencies.

### Allowance for service-integration components

Independently authored, separable code whose role is communicating with an external service may retain another license **compatible with AGPL-3.0-only for the actual combination and distribution**. This can include a reusable protocol client, an external-service adapter, or an upstream SDK under compatible terms such as MIT, BSD-3-Clause, or Apache-2.0. Preserve its own license, copyright and required notices, and include its source where the applicable Corresponding Source requirements call for it. The covered application/plugin combination remains subject to the AGPL.

This allowance does not relicense copied or modified Meshable Ops code, or exempt the plugin's covered pages, business logic, permissions, persistence, or workflow functionality just because they communicate with a service. It does not permit combining an incompatible proprietary SDK with covered code without the necessary separate permissions from all relevant rights holders. A Meshable Ops commercial agreement cannot waive an upstream owner's terms.

An independent external service accessed through an ordinary API or protocol may keep its own license or commercial service terms. Communicating with it does not, by itself, relicense that service under the AGPL. Conversely, moving covered plugin functionality behind HTTP, RPC, or a worker process does not automatically turn it into an independent work. Assess the actual code, dependencies, and degree of integration.

### Scope and examples

These statements explain the project's licensing scope and contribution policy; they do not modify the standard [AGPL text](LICENSE), impose additional restrictions on rights it grants, or purport to relicense genuinely independent works or upstream components. License compatibility and required source/notice obligations must be assessed for the exact components and versions. See [plugin licensing examples](docs/plugins/licensing.md).

## Contributions and future commercial licensing

Future commercial licensing requires control of the relevant copyrights or sufficient permission from their holders. AGPL permission alone does not authorize a maintainer to relicense someone else's contribution as proprietary software.

Before merging a copyrightable contribution, maintainers require explicit acceptance of the [contributor license agreement](CLA.md), including any necessary employer or organizational authorization. The agreement keeps contributor ownership while granting the project permission to offer the contribution under AGPL and separate commercial terms. Submitting a pull request, signing off a commit, or opening an issue is **not**, by itself, acceptance of that additional grant. Existing contributions are not retroactively covered: their rights must be established separately.

Plugin contributions follow the [plugin and integration policy](#plugins-and-external-service-integrations). Independently licensed upstream components must be identified, and contributors cannot grant commercial relicensing rights they do not hold. See the [plugin guide](docs/plugins/README.md).

Commercial contracts, contribution acceptance, and release-specific third-party obligations should receive legal review before a commercial release. This guide explains the project's intended licensing structure; the applicable license texts and executed agreements govern.

## Primary references

- [GNU AGPLv3, including network interaction in section 13](https://www.gnu.org/licenses/agpl-3.0.en.html)
- [GNU license compatibility list, including Apache-2.0 and AGPLv3](https://www.gnu.org/licenses/license-list.html)
- [Apache Software Foundation: Apache-2.0 / GPLv3 compatibility](https://www.apache.org/licenses/GPL-compatibility.html)
- [GNU FAQ: a copyright holder can release code under different licenses](https://www.gnu.org/licenses/gpl-faq.html#HeardOtherLicense)
- [GNU: selling separate licensing exceptions](https://www.gnu.org/philosophy/selling-exceptions.html)
- [GNU FAQ: plugins and combined works](https://www.gnu.org/licenses/gpl-faq.en.html#GPLAndPlugins)
- [GNU FAQ: separate programs and aggregation](https://www.gnu.org/licenses/gpl-faq.en.html#MereAggregation)
