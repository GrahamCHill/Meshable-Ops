# Third-party software and notices

Meshable Ops integrates upstream libraries and separately operated services. The project's AGPL/commercial licensing choice applies only to rights held or licensed by the Meshable Ops copyright holders. It does not replace upstream terms, remove attribution requirements, or grant upstream trademarks.

This is a guide to major components, not an exhaustive dependency inventory or a release-specific legal certification. Check exact package versions, lockfiles, image digests, transitive dependencies, and the license/notice files shipped with the artifacts you distribute.

## Apache Superset

This product uses Apache Superset software developed at The Apache Software Foundation. Apache Superset and `@superset-ui/embedded-sdk` are Apache-2.0 components. Preserve the applicable license, NOTICE, attribution, and modification notices when redistributing them or derived code.

- [Apache-2.0 license text](LICENSES/Apache-2.0.txt)
- [Superset 4.0.2 upstream license, including its component notice](LICENSES/Apache-Superset-4.0.2-LICENSE.txt)
- [Superset 4.0.2 upstream NOTICE](LICENSES/Apache-Superset-4.0.2-NOTICE.txt)
- [Superset source and release-specific notices](https://github.com/apache/superset/tree/4.0.2)

The checked-in Superset copies correspond to the `apache/superset:4.0.2` image selected in Compose. When changing that image or SDK, review the relevant version's notices. Superset's own distribution and dependencies retain additional notices. Meshable Ops is an independent project, not an Apache Software Foundation project or endorsed product. Apache and Apache Superset are trademarks of The Apache Software Foundation.

## Other components

| Component | Relationship and licensing boundary |
| --- | --- |
| React, TypeScript, Spring Boot, Kotlin, PDFBox, storage SDKs, and other application dependencies | Retain their package-specific licenses and notices. Root Meshable Ops terms do not substitute for a dependency inventory. |
| WeasyPrint and its Python/native dependencies | Separate upstream packages used by the private PDF renderer; retain their respective terms and font notices. |
| Imported WeasyCanvas source snapshot | The project owner's imported editor/model/generator code is covered by this repository's AGPL/commercial choice. Third-party packages used by it remain under their own terms. See [licensing scope](LICENSING.md#code-covered-by-this-choice). |
| PostgreSQL and pgvector | Separately distributed database/extension software with their upstream PostgreSQL-family licenses and notices. |
| MongoDB Community Server 7 | Separately operated SSPL-licensed service, not relicensed by Meshable Ops. MongoDB distinguishes applications using it as a database from offering MongoDB itself as a service. See its [licensing FAQ](https://www.mongodb.com/legal/licensing/server-side-public-license/faq). |
| Redis | Separately operated Superset cache/queue. Redis 7.2 and earlier use BSD-3-Clause; 7.4 uses RSALv2/SSPLv1; Redis 8 adds an AGPLv3 option. The `redis:7-alpine` tag can resolve to a 7.4 release: inspect the image actually shipped. See [Redis licensing](https://redis.io/legal/licenses/). |
| MinIO, Azurite, nginx, Caddy, base images, fonts, and OS packages | Development storage and runtime components retain their own version-specific licenses. A Meshable Ops commercial license does not waive them. |
| External AI, email, messaging, calendar, Microsoft, and source-control services | Optional connections are subject to each service's terms and the credentials/account rights configured by the operator. |

Covered plugins follow the app's community license. Independently authored, separable service-integration components may retain compatible licenses with their own notices, while independent external services retain their own terms. An SDK incorporated into covered plugin code must be compatible with the actual combination; API communication does not create a blanket linking exception. See [plugin licensing](LICENSING.md#plugins-and-external-service-integrations).

MongoDB SSPL and Redis's source-available options are not OSI-approved open source licenses. Calling Meshable Ops an open source application is not a claim that every optional service, upstream image, or connected provider uses the same license.

## Redistribution

Keep upstream license files, NOTICE files, required attribution, modification notices, and any applicable source offers with the relevant artifacts. Preserve this repository's [LICENSE](LICENSE), [licensing guide](LICENSING.md), and [NOTICE](NOTICE). Review dependencies and native/container packages for each release; these selected notice copies are not a substitute for that work.
