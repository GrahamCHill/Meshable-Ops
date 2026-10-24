# Meshable Ops licensing

Original Meshable Ops code uses the **Meshable Ops Community License 1.0**, a custom **source-available** license. Read the complete [LICENSE](LICENSE). It is not AGPL, an OSI-approved open source license, or a general permission to sell commercial forks.

## Community and commercial paths

Businesses and individuals may run and customize Meshable Ops for their own internal operations without a commercial license. Staff, contractors acting on their behalf and customers accessing the business's own records/processes can participate. Ordinary revenue from those activities is allowed.

Selling/licensing the application or a fork, charging for distribution, or offering it as a commercial hosted/managed/SaaS product requires a **separate written license from the original creator or a current maintainer expressly authorized by the rights holder**. This includes renamed and unmodified copies, bundled charges and indirect monetization. A fork's own maintainers cannot authorize themselves. Paid installation/consulting/maintenance on a customer's own system remains allowed; a paid application-hosting offering needs permission. See [commercial licensing](COMMERCIAL-LICENSE.md) and [licensing authority](MAINTAINERS.md).

Noncommercial redistribution preserves the terms, notices, modifications and complete source. Modified network deployments offer their users complete source at no extra charge. Internal modifications need not be published to everyone on the internet. Business records, uploads, credentials and generated documents do not become licensed software merely because the app processes them.

A donation or upstream pull request does not automatically earn commercial permission. An executed commercial agreement can specify development/maintenance contributions, fees and permitted use. The repository supplies no such agreement and promises no premium edition. Package metadata uses `LicenseRef-Meshable-Ops-Community-1.0`; no public commercial text exists for an SPDX `OR` choice.

## Code covered by this choice

Except where a file-specific/upstream notice says otherwise, this covers original frontend/backend code, original plugins, deployment tools, documentation, rendering integration and the owner's imported **WeasyCanvas v0.8.0** snapshot in `frontend/src/vendor/weasycanvas/` and `weasy-renderer/backend/`. The owner authorized its inclusion; dependencies and the separate WeasyCanvas repository are not relicensed here.

Authors retain copyright. The project needs ownership or sufficient relicensing rights for code published under these terms. See [third-party notices](THIRD_PARTY_NOTICES.md). A commit or DCO sign-off does not grant commercial relicensing authority; explicit [CLA acceptance](CLA.md) is required for identified new contributions.

## Earlier private versions

The owner intends this notice to cover their current and earlier **unreleased** versions, branches and snapshots when supplied under it, subject to existing rights. The owner reports sole control and no other recipients. Keep a release/licensing record before publication and verify any collaborator, contractor or customer access.

Private visibility does not undo a license grant. Anyone who already legitimately received an earlier copy under AGPL or another valid license may retain those rights. Replacing this file cannot retroactively revoke them or relicense independent third-party contributions. The unchanged AGPL text under `LICENSES/` is a historical reference, **not an alternative grant for the current release**.

## Plugins and external-service integrations

Covered extensions inherit the community license with an **express paid-plugin allowance**. Authors may sell plugin copies, place their plugin/service behind a paywall, charge subscriptions/support and keep revenue without automatic maintainer royalties. They retain ownership. Independent publication, installation or sale does not accept the CLA or grant the project relicensing rights over that plugin.

Covered plugin code retains source/notice conditions and recipients' permitted rights. Supply required source with paid copies at no additional cost and to users of modified covered network versions. Plugin revenue does not authorize selling or commercially hosting Meshable Ops itself. Moving the core into a plugin or bundling the app into a paid plugin offer cannot evade the application restriction.

Independently authored, separable external-service clients/adapters may keep a license that permits the actual combination, including suitable MIT/BSD/Apache-2.0 components. Preserve their notices/obligations. Independent external services keep their own terms. React, Kotlin, Python, ZIP packaging, dynamic loading and process boundaries do not establish independence by themselves. GPL/AGPL components cannot be assumed compatible with this commercial restriction: review exact dependencies and obtain required rights/exceptions before combining them. See [plugin examples](docs/plugins/licensing.md).

## Apache Superset and other upstream software

Superset and its embedded SDK remain **Apache-2.0**. This license neither relicenses them nor restricts rights granted by Apache. Preserve their license, NOTICE, modification and attribution material when redistributing them. Superset is a separate service; the SDK has its own notices. No Apache endorsement is claimed.

This is not a certification of every dependency/image. Review exact dependencies, native libraries, fonts, services and distributions before release. A commercial Meshable Ops agreement cannot waive upstream terms.

## Why this is source available

AGPL/GPL permit commercial distribution and forks, without requiring donations or upstream maintenance contributions. Restricting commercial application offerings requires a different license; the project must not advertise this license as OSI open source. See the [OSI definition](https://opensource.org/osd), [GNU licensing FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#CanDeveloperThirdParty), and [Apache license](https://www.apache.org/licenses/LICENSE-2.0).

The actual licenses/agreements govern. Obtain qualified legal review of these custom terms, contributor authority and prior-release history before public or commercial distribution.
