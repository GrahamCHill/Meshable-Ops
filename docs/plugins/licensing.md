# Plugin licensing and commercial activity

The authoritative terms are [LICENSE](../../LICENSE), [LICENSING.md](../../LICENSING.md#plugins-and-external-service-integrations) and the contribution-specific [CLA](../../CLA.md).

Meshable Ops uses a custom **source-available community license** with strong same-license source sharing and an upstream grant-back, not AGPL or an OSI-approved open source license. Community modifications and forks, including bundled-plugin changes, are allowed under those conditions. Business use, hosting, paid setup/deployment/maintenance services, necessary installer/deployment changes and built-in customization remain allowed. Application paywalls, closed-source offerings, extra branding and exceptions to the grant-back require express written agreements from the creator or a rights-holder-authorized [Licensing Maintainer](../../MAINTAINERS.md). See the [licensing examples](../../LICENSING.md).

## Paid plugins

Authors may sell copies, charge subscriptions, use paywalls and offer paid hosting/support/services for their separately identifiable plugins, retaining proceeds and ownership without automatic maintainer royalties. Application-derived extensions and plugins using the application-specific interfaces must use the same community terms, including their original covered code, the paid-plugin exception and source/notice conditions. Complete source accompanies paid copies at no extra charge, and every covered network deployment offers users the source of the exact version and covered features they use. A covered plugin cannot be closed source or withhold its premium features' source without an express proprietary exception.

The exception permits developing and modifying separately identifiable plugins through the exposed interfaces. It does not authorize application paywalls, extra branding, proprietary covered features or repackaging the core as a plugin to evade restrictions. Compliant community modifications, hosting and services remain allowed alongside paid plugins.

Plugins originally developed as part of a fork, and other fork changes, must grant the project licensor permanent, royalty-free rights to reuse, merge, modify and sublicense those changes in community and commercial/proprietary releases under LICENSE section 3.1. Source must be supplied within 30 days of the licensor's written request. This grant does not depend on a PR or CLA signature. Independently developed plugins merely installed unchanged are outside that grant; publishing or selling them does not accept the CLA or automatically confer commercial relicensing rights. Expressly contributing identified code under the CLA is a separate decision.

| Component | Treatment |
| --- | --- |
| Separately identifiable React pages, Kotlin services, Python routes, and covered ZIP plugins using exposed interfaces | Same community terms, paid-plugin exception and complete-source/notice conditions; section 3.1 applies when developed as fork changes. |
| Changes to app code or bundled plugins | Allowed under the community source-sharing and mandatory upstream grant-back conditions. |
| Independently authored separable HTTP/protocol client | May retain a license permitting the actual combination; preserve upstream terms/notices. |
| Independent remote Git, chat, calendar or storage service | Its own license/service terms; API communication alone does not relicense it. |
| Core app renamed or moved behind a worker/API and sold as a plugin | Does not avoid the commercial application restriction. |

ZIP packaging, process boundaries, language and manifests alone do not determine independence. GPL/AGPL code cannot be assumed combinable with this custom restriction without adequate permissions. Identify upstream material, exact terms and rights holders and retain source/notices required for the actual release. A project commercial agreement cannot waive upstream rights.

Existing valid recipient licenses and earlier accepted agreements are not retroactively revoked. Direct contributors must expressly accept CLA 2.1 for identified submissions under the repository contribution policy; fork changes independently carry section 3.1's grant-back. Maintainers must retain the applicable license, authority, source provenance and acceptance records. Obtain legal review before commercial release.
