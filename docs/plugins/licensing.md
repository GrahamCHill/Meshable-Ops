# Plugin licensing and external integrations

The authoritative project policy is [Plugins and external-service integrations](../../LICENSING.md#plugins-and-external-service-integrations). The public application uses **AGPL-3.0-only**, with an alternative commercial path available only through a separate executed agreement.

Plugins inherit the app's community licensing when they form a covered extension or combined work. Original plugin functionality accepted into this repository must be made available under AGPL-3.0-only, with the limited compatible-license allowance for independently authored service-integration components described below. A plugin manifest, upload, sandbox, programming language, or deployment boundary does not itself create a copyleft exemption. Copyright holders may supply additional permissions for their own material; no project notice grants rights over someone else's code.

## Examples

| Component | Licensing treatment |
| --- | --- |
| Meshable Ops-derived React plugin pages, Kotlin services, or Python worker functionality | Follow the app's AGPL community terms for the covered code/combination, including applicable source and network-source-offer obligations. |
| A ZIP containing plugin functionality that forms a covered work with the app | Same covered-work analysis; uploading HTML or running in a sandbox does not itself create an exception. |
| An independently authored reusable HTTP/protocol client used to connect a plugin to a service | May retain a compatible license, such as MIT, BSD-3-Clause or Apache-2.0, with its notices preserved. The covered combination remains subject to AGPL. |
| A third-party SDK linked or bundled into a covered plugin | Its exact license must permit the intended combination. Preserve its terms and include required source/notices; a proprietary SDK is not automatically permitted because it accesses a remote API. |
| An independent Git host, messaging API, calendar service, or storage service | Keeps its own software license/service terms. Ordinary API communication alone does not relicense the external service. |
| App-derived plugin logic moved to a separate Python process or HTTP service | Its actual relationship to the app still determines coverage; relocating the code does not automatically exempt it. |

The integration allowance applies to independently authored, separable communication components. It does not let the plugin's covered UI, business rules, RBAC, database logic or workflows adopt a different license simply because an external service is involved. Upstream compatible components retain their own notices and license; their use does not replace the AGPL obligations of the covered application/plugin combination.

For example, a source-control plugin's covered project/board integration follows the app's license. An independently authored Apache-2.0 HTTP client can retain its Apache terms when included compatibly. The remote Git provider keeps its own terms. A proposed binary-assets plugin would follow the same separation between its covered checkout/locking functionality, compatible protocol components and independent external services.

## Author and maintainer responsibilities

Identify the license and copyright holder for each independently licensed portion using file/directory notices and the relevant third-party inventory. Supply applicable license texts, attribution/NOTICE files and Corresponding Source. Review the exact license versions and dependency relationships; a programming-language package or container image is not proof of compatibility.

Original community contributions accepted into this repository need explicit [CLA acceptance](../../CLA.md). That agreement does not relicense upstream material or unrelated plugins. A premium/commercial license applies only to material and rights expressly covered by its executed agreement; preserve third-party obligations in either licensing path.

This guide explains scope and project contribution policy. It leaves the standard [AGPL license](../../LICENSE) intact and does not add restrictions to its granted rights or claim ownership of independent works. For compatibility background, see the [GNU plugin FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#GPLAndPlugins), [aggregation guidance](https://www.gnu.org/licenses/gpl-faq.en.html#MereAggregation), and [license compatibility list](https://www.gnu.org/licenses/license-list.html).
