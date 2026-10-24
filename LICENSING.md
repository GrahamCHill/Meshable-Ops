# WeaveOps licensing

Copyright (C) 2026 Graham Hill and the WeaveOps contributors.

## Two licensing paths

Original WeaveOps code is offered under either:

1. **GNU Affero General Public License, version 3 only (`AGPL-3.0-only`)**, whose complete, unmodified text is in [LICENSE](LICENSE); or
2. **A separate commercial license**, if you obtain an express written agreement from the relevant copyright holder or an authorized commercial licensor.

The AGPL option is available immediately. This repository does not grant a commercial license, publish commercial contract terms, or promise a premium edition. A future commercial agreement can provide alternative permissions for the WeaveOps code covered by that agreement. Merely paying for hosting, support, or a subscription does not grant those permissions unless the agreement expressly says so.

Contact the [project maintainer](https://github.com/GrahamCHill) to discuss commercial licensing. A future premium offering can include separately licensed original modules and services; its scope and terms must be stated in its own agreement. Existing AGPL permissions for released copies remain available under the AGPL; a commercial offering does not revoke them.

Package metadata identifies the currently published public license as `AGPL-3.0-only`. There is no public commercial license text to add to an SPDX `OR` expression.

## Code covered by this choice

Except where an upstream or file-specific notice says otherwise, this choice covers the original application frontend/backend, original plugins, deployment tooling, documentation, and original rendering-service integration in this repository.

It also covers the project owner's imported **WeasyCanvas v0.8.0 source snapshot** in `frontend/src/vendor/weasycanvas/` and `weasy-renderer/backend/`. That source is included with the owner's authorization under this repository's licensing choice. This does not change the license of WeasyPrint or any other dependency, or edit the separate WeasyCanvas source repository.

Copyright stays with each author unless a separate agreement assigns it. The root license cannot grant rights that an author or upstream project has not supplied. Third-party dependencies, container images, copied upstream notices, trademarks, and separately identified assets retain their own terms. See [third-party notices](THIRD_PARTY_NOTICES.md).

## What the AGPL option means

The AGPL is a strong copyleft **open source** license. It permits commercial use and charging for software, hosting, or support while imposing its source and redistribution requirements. It is not a prohibition on competitors, commercial use, or particular industries.

Among other obligations, distributing covered software requires satisfying the AGPL's license/notice and Corresponding Source provisions. Under section 13, if you modify the program and users interact with that version remotely over a network, that version must prominently offer those users its Corresponding Source at no charge. Internal network users can also be users of a modified deployment. A source offer should identify the actual running revision and include the necessary covered source and build/install material; a link to an unrelated or outdated public branch is insufficient.

Corresponding Source concerns covered software. Ordinary business records, uploaded client files, credentials, and generated documents do not become AGPL-licensed merely because they are processed by WeaveOps. Material copied from licensed code or assets into an output can have separate obligations. The license itself controls the exact requirements.

LGPL would allow more proprietary linking, and ordinary GPL does not add the AGPL's network-interaction condition. AGPLv3 is the public-license choice here because this application is used through a browser and server.

## Apache Superset compatibility

Apache Superset and its embedded SDK retain **Apache-2.0** licensing. Apache-2.0 code can be included in GPLv3-compatible copyleft works while preserving the Apache license and applicable notices; this project uses the AGPLv3 family and keeps Superset as a separate service. WeaveOps does **not** relicense Superset, claim Apache endorsement, or grant proprietary rights to Apache or other upstream code through its commercial option.

The project's licensing choice does not make WeaveOps code eligible for inclusion in an Apache-licensed upstream project on Apache terms. Nor does compatibility with Superset establish that every dependency or future plugin has been audited. Redis, MongoDB, development storage, and other services have independent version-specific terms; preserve their notices and assess the exact distribution and hosting model.

## Contributions and future commercial licensing

Future commercial licensing requires control of the relevant copyrights or sufficient permission from their holders. AGPL permission alone does not authorize a maintainer to relicense someone else's contribution as proprietary software.

Before merging a copyrightable contribution, maintainers require explicit acceptance of the [contributor license agreement](CLA.md), including any necessary employer or organizational authorization. The agreement keeps contributor ownership while granting the project permission to offer the contribution under AGPL and separate commercial terms. Submitting a pull request, signing off a commit, or opening an issue is **not**, by itself, acceptance of that additional grant. Existing contributions are not retroactively covered: their rights must be established separately.

Compiled-in plugins can form part of a combined work. Do not assume that placing a proprietary plugin in a separate folder or giving it a manifest creates an exemption from copyleft. Separately operated services and combined/distributed modules require assessment of their actual relationship and licenses. See the [plugin guide](docs/plugins/README.md).

Commercial contracts, contribution acceptance, and release-specific third-party obligations should receive legal review before a commercial release. This guide explains the project's intended licensing structure; the applicable license texts and executed agreements govern.

## Primary references

- [GNU AGPLv3, including network interaction in section 13](https://www.gnu.org/licenses/agpl-3.0.en.html)
- [GNU license compatibility list, including Apache-2.0 and AGPLv3](https://www.gnu.org/licenses/license-list.html)
- [Apache Software Foundation: Apache-2.0 / GPLv3 compatibility](https://www.apache.org/licenses/GPL-compatibility.html)
- [GNU FAQ: a copyright holder can release code under different licenses](https://www.gnu.org/licenses/gpl-faq.html#HeardOtherLicense)
- [GNU: selling separate licensing exceptions](https://www.gnu.org/philosophy/selling-exceptions.html)
