# Meshable Ops licensing

Original Meshable Ops code uses the **Meshable Ops Community License 1.1**, a custom **source-available** license. Read the complete [LICENSE](LICENSE). It is not AGPL, an OSI-approved open source license, or a general permission to sell commercial forks.

## Community source sharing and commercial exceptions

Businesses and individuals may run, modify, fork and improve Meshable Ops without individual approval, provided they comply with the same-license, source-sharing and upstream grant-back conditions below. This applies to companies of every size, including changes to bundled plugins. Staff, contractors acting on their behalf and customers accessing the business's own records/processes can participate. Ordinary business revenue is allowed. Branding beyond exposed customization and commercial/proprietary exceptions remain separately controlled.

**Hosting and deployment services are permitted, including paid services.** Hosting providers may host the application on AWS, Google Cloud Platform (GCP), Microsoft Azure or any other provider, or on premises. Paying or charging for compute, storage, networking, installation, setup, management, backups, support and maintenance does not breach the license. Providers may make a profit, charge recurring or bundled service fees, and use infrastructure controlled by themselves or the customer. Those service fees are permitted even when needed to access the hosted deployment.

**Easy installers and automated setup are permitted.** They may build/package unchanged application code, use supported configuration and change installation/deployment scripts, manifests, containers, infrastructure definitions and runtime configuration as reasonably necessary to deploy the application. Examples include paths, domains, ports, TLS, databases, storage, secrets and resource settings. Installing official updates is permitted. Changes beyond deployment needs must independently comply with the community modification and source-sharing rules, or an express written exception; the installer allowance alone does not authorize them.

**Paywalling the application itself requires a separate written agreement.** Examples include selling application licenses or copies, paid application subscriptions, or charging to unlock its features. Hiding those charges in a bundle does not avoid the restriction. The charge's substance determines whether it is for permitted services or the application itself. Normal authentication and access permissions are not paywalls. Permission for a paid offering does not waive source-sharing conditions unless the agreement expressly says so.

**Closed-source versions require an express written commercial exception.** A company cannot distribute or host a proprietary fork, withhold covered premium/enterprise features, or publish only the base edition's source while keeping its covered improvements closed. All covered additions, modifications and plugins must follow the same community terms and source-sharing rules. Moving them into a plugin, worker or service does not automatically avoid those obligations. Truly independent third-party software and external services retain their own terms as described below.

**Extra branding still requires a separate written agreement.** Using the built-in name, logo, favicon, colours, themes, sign-in images and other exposed customization features is allowed. Rebranding or white-labeling beyond those features needs approval, even if the source is shared. Identifying a modified version and its author without changing product branding is allowed and does not imply endorsement.

Commercial or branding exceptions must come from the original creator or a current maintainer expressly authorized by the rights holder. A fork's maintainers cannot waive the inherited terms for the application or other authors' code. An agreement must identify any permitted paywalls, proprietary licensing, source exceptions and extra branding separately. See [agreement requirements](COMMERCIAL-LICENSE.md) and [licensing authority](MAINTAINERS.md).

| Scenario | Separate agreement required? |
| --- | --- |
| Run a business's own operations using the unmodified app | No |
| Pay AWS, GCP, Azure or another provider's hosting bill | No |
| Charge for hosting, installation, deployment management or maintenance | No, when the charge is for the permitted services |
| Supply a paid or free one-click installer with necessary deployment changes | No |
| Installer changes application features or runtime code beyond deployment needs | No, if those changes independently comply with the community modification/source rules; otherwise an express exception is needed |
| Set the business name, logo, colours or sign-in images through built-in settings | No |
| Modify/fork the app or bundled plugins with the upstream grant-back and required same-license source sharing | No |
| Rebrand bundled assets or white-label beyond exposed customization features | Yes |
| Distribute or host a closed-source fork or withhold covered premium features | Yes, with an express proprietary/source-sharing exception |
| Charge for application licenses, subscriptions or feature access | Yes |
| Sell a separately identifiable plugin using the exposed plugin interfaces | No, subject to section 4 and source/notice conditions |

## Strong same-license and network source requirements

Distributions, including binaries, containers and installers, must provide recipients the exact version's complete source at no extra charge and preserve this license, notices and modification information. The covered version as a whole, including covered improvements and plugins, must remain under the same community terms. Source must include build/deployment inputs, dependency manifests and the covered feature code needed to modify, build and run it. A base-edition repository, compiled/minified output or a future promise is insufficient.

Every user interacting with a covered network deployment must receive a prominent, usable download offer for that version's complete source at no extra charge. This applies to modified and unmodified deployments, hosted/managed/SaaS offerings, internal users and covered plugins. Normal account access may be required; a separate source subscription, NDA or proprietary EULA may not remove the user's community rights. Users may inspect, modify, fork and redistribute covered code under the same terms. Ordinary account controls still protect business data.

Changes that are neither distributed nor made available to users over a network do not require public disclosure just because they were made. Internal network use requires source availability to those users, rather than publication to the general public. The upstream grant and source-on-request duty below still cover private/internal fork changes. Business records, uploads, credentials and generated documents are excluded from covered source; use configuration placeholders instead of secrets.

## Mandatory upstream grant-back for forks

Making changes in a fork or modified application is conditional on granting the project licensor a permanent, irrevocable, nonexclusive, worldwide, royalty-free right to reuse, modify, merge, distribute and sublicense those changes in Meshable Ops and its derivatives. This includes community and separate commercial/proprietary releases, with the relevant patent permission. Authors retain copyright, may continue their own permitted uses and are not owed automatic royalties. The project need not accept or maintain their work.

The grant takes effect when the changes are made using the license's modification permission. It does not depend on a PR, publication, CLA signature or upstream acceptance. Within 30 days of the licensor's written request, the fork operator must supply the complete source of those changes and the covered build/integration material needed to use them, without additional fees or restrictions. An exact source download link is sufficient. This includes private/internal forks, with secrets and ordinary business data excluded.

Fork changes include covered features and plugins originally developed as part of a fork. Ordinary configuration values, uploaded branding assets, business content, unchanged third-party code and independently developed plugins merely installed unchanged are excluded. Fork authors must obtain the necessary employer, client and contributor authority for every incorporated fork change; they cannot grant rights in independent third-party material they do not control. See LICENSE section 3.1.

A donation or upstream pull request does not automatically authorize commercial/proprietary or branding exceptions. An executed agreement can specify development/maintenance contributions, fees and permitted use, including an express exception to source sharing. The repository supplies no such agreement and promises no premium edition. Package metadata uses `LicenseRef-Meshable-Ops-Community-1.1`; no public commercial text exists for an SPDX `OR` choice.

## Code covered by this choice

Except where a file-specific/upstream notice says otherwise, this covers original frontend/backend code, original plugins, deployment tools, documentation, rendering integration and the owner's imported **WeasyCanvas v0.8.0** snapshot in `frontend/src/vendor/weasycanvas/` and `weasy-renderer/backend/`. The owner authorized its inclusion; dependencies and the separate WeasyCanvas repository are not relicensed here.

Authors retain copyright. The project needs ownership or sufficient relicensing rights for code published under these terms. See [third-party notices](THIRD_PARTY_NOTICES.md). LICENSE section 3.1 supplies the grant-back for authorized fork changes; retain its applicable version, source/provenance and authority records. Direct submissions also require explicit [CLA acceptance](CLA.md) for identified new contributions under the contribution policy. A commit or DCO sign-off alone is not that separate agreement.

## Earlier private versions

The owner intends this notice to cover their current and earlier **unreleased** versions, branches and snapshots when supplied under it, subject to existing rights. The owner reports sole control and no other recipients. Keep a release/licensing record before publication and verify any collaborator, contractor or customer access.

Private visibility does not undo a license grant. Anyone who already legitimately received an earlier copy under community license 1.0, AGPL or another valid license may retain those rights. Version 1.1's branding, source-sharing and grant-back conditions do not retroactively narrow an earlier grant. Replacing this file cannot revoke those grants or relicense independent third-party contributions. Earlier community license, CLA and AGPL texts under `LICENSES/` are historical references, **not alternative grants for the current release**.

## Plugins and external-service integrations

Covered extensions inherit the community license with an **express paid-plugin allowance**. Authors may sell plugin copies, place their plugin/service behind a paywall, charge subscriptions/support and keep revenue without automatic maintainer royalties. They retain ownership. Independent publication, installation or sale does not accept the CLA. Independently developed plugins outside the definition of fork changes do not grant the project commercial relicensing rights merely by being installed or published; plugins originally developed as part of a fork are subject to section 3.1.

Plugins using the application-specific plugin interfaces and application-derived extensions must use the same community terms, including their original covered code. Supply complete source with paid copies at no additional cost and to every user of a covered network deployment. The paid-plugin allowance does not authorize closed-source plugins, withhold covered premium features, remove recipients' community rights or permit paywalling Meshable Ops itself. Community modifications and permitted hosting/services remain allowed alongside paid plugins. Moving the core into a plugin or hiding application-access charges in a paid plugin offer cannot evade the restrictions.

Community authors retain copyright. Section 3.1 gives the licensor commercial reuse rights over authorized fork changes. Independent plugins and other code outside that grant still need sufficient rights over each included contribution, such as a contribution-specific CLA grant or separate permission, before proprietary relicensing. Merely publishing such an independent plugin does not supply those rights.

Independently authored, separable external-service clients/adapters may keep a license that permits the actual combination, including suitable MIT/BSD/Apache-2.0 components. Preserve their notices/obligations. Independent external services keep their own terms. React, Kotlin, Python, ZIP packaging, dynamic loading and process boundaries do not establish independence by themselves. GPL/AGPL components cannot be assumed compatible with this commercial restriction: review exact dependencies and obtain required rights/exceptions before combining them. See [plugin examples](docs/plugins/licensing.md).

## Apache Superset and other upstream software

Superset and its embedded SDK remain **Apache-2.0**. This license neither relicenses them nor restricts rights granted by Apache. Preserve their license, NOTICE, modification and attribution material when redistributing them. Superset is a separate service; the SDK has its own notices. No Apache endorsement is claimed.

This is not a certification of every dependency/image. Review exact dependencies, native libraries, fonts, services and distributions before release. A commercial Meshable Ops agreement cannot waive upstream terms.

## Why this is source available

These are strong **copyleft-style source-sharing conditions within a custom source-available license**, rather than an AGPL grant. AGPL/GPL allow commercial distribution and forks without individual approval; this license additionally reserves application paywalls and extra branding for written agreements and requires an upstream grant-back. It must not be advertised as OSI-approved open source. See the [OSI definition](https://opensource.org/osd), [GNU licensing FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#CanDeveloperThirdParty), and [Apache license](https://www.apache.org/licenses/LICENSE-2.0).

The actual licenses/agreements govern. Obtain qualified legal review of these custom terms, contributor authority and prior-release history before public or commercial distribution.
