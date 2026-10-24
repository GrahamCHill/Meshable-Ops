# Plugin compatibility and application updates

Installed ZIP plugins are persisted in PostgreSQL, including their descriptors, author metadata, HTML pages, and normalized artwork. Enablement, roles, table policies, search choices, encrypted configuration, and business records are stored separately in the same database. Rebuilding/replacing application containers does not reinstall, delete, or reset them. Keep the same database/Compose volumes, project name, and secret-wrapping key during updates.

Bundled plugins ship with the application image; their saved settings/data persist in PostgreSQL too. Custom compiled source modules must remain in the source/image you build. A replacement image cannot retain code that was deliberately omitted from that image. Uploaded plugins do not depend on files inside an old release directory.

## Declare compatible application versions

Add `compatibleVersions` to the plugin manifest. It is an array of strings, including when there is only one entry:

```json
{
  "id": "client-notes",
  "name": "Client notes",
  "version": "2.1.0",
  "compatibleVersions": [
    "1.0.0",
    "1.2.3",
    "1.4.0 - 1.9.9",
    ">=2.0.0 <3.0.0"
  ]
}
```

This fragment declares the plugin's own version as `2.1.0`; its array refers to **Meshable Ops application releases**, not the plugin release or `apiVersion`. The server advertises its packaged application version in plugin responses and the admin catalogue. It is generated from `backend/pom.xml` by Maven's build-info goal; branding/settings cannot override it. Bundled authors set the same manifest field in Kotlin, for example `compatibleVersions = listOf("1.0.0")`.

| Entry | Matching |
| --- | --- |
| `["1.0.0"]` | One exact application release |
| `["1.0.0", "1.0.2", "1.2.0"]` | Any listed exact release |
| `["1.0.0 - 1.4.2"]` | Inclusive lower and upper bounds |
| `[">=1.0.0 <2.0.0"]` | All comparators in that entry must match |
| A mixture of entries | Any matching entry declares support |

Comparators are `>`, `>=`, `<`, `<=`, and `=`. Separate combined comparators with spaces; hyphen ranges require spaces around the separating hyphen. Versions require major/minor/patch. Numeric components and prereleases use [SemVer precedence](https://semver.org/); build metadata does not change precedence. These are the supported forms, not the full npm range language: wildcards, caret/tilde ranges, and `||` are rejected. Limits are 50 entries, 200 characters per entry, and four comparators per entry. Malformed declarations in new uploads are rejected as invalid metadata.

## Warnings and continued use

- A matching declaration reports `COMPATIBLE`.
- An omitted or empty array reports `UNKNOWN`, warning that the plugin may not be compatible and recommending discretion and a backup.
- An out-of-range application release reports `INCOMPATIBLE`, with **proceed with caution** and backup advice. **The plugin remains usable.** This status is advisory and does not switch off saved enablement, remove pages/data, or exclude its records from search.
- Missing build information or an invalid declaration in already persisted metadata reports unverified compatibility with caution/backup advice. Existing records are retained.

Warnings appear after installation and in the admin plugin catalogue/detail view. There is no new approval checkbox or forced acknowledgement. Newly installed plugins still start disabled under the normal installation policy; an admin enables them as before. Compatibility is an author's statement, not a guarantee that every feature/provider works.

Existing RBAC, activation, third-party extension security, dependencies, and supported plugin API rules still apply. `apiVersion` is an executable host-bridge contract and remains distinct from this advisory release declaration. A newly bundled module colliding with an installed ZIP's ID is also blocked from ambiguous execution; the ZIP descriptor/package and data remain available for inspection/removal instead of being silently replaced by bundled code.

The API exposes `applicationVersion`, `versionCompatibility`, and `compatibilityWarnings` on `PluginView`. `manifest.compatibleVersions` carries the author's entries. `compatibilityIssues` continues to describe blocking plugin API and ID/conflict conditions; required supporting plugins remain listed in `missingDependencies`. These are separate from advisory version warnings.

## Updating safely

Back up PostgreSQL, original object storage, and the secret-wrapping key before upgrading. PostgreSQL backups must include `workspace_plugin_packages`, `plugin_settings`, `workspace_plugin_configuration`, extension security/access policies, and each plugin's owned tables. The metadata JSON export is not a complete backup and does not contain everything needed to restore an installed plugin. Test database restores against a compatible application version and retain original ZIPs/source as recovery material.

Use the [normal application upgrade procedure](../deployment.md#upgrading-an-existing-installation), preserving database volumes and credentials. The app reloads installed descriptors from PostgreSQL after restart and evaluates them against its new packaged release. Version warnings can change after an upgrade or rollback while plugin code, saved settings, and records remain intact. Do not use `down -v`, a fresh Compose project/database, clear-data, or uninstall as a normal update step.

Replacing an installed ZIP is still an explicit super-admin operation: back up, remove the installed package, and upload the reviewed replacement with the same ID and table names. Removal retains business tables/configuration; reinstall starts disabled and reuses retained managed tables. A new package declaring different table names does not automatically migrate existing records. See [removal and restoration](presentation-and-lifecycle.md#removal-and-restoration).

Cloud infrastructure changes that replace a VM/database require deliberate data migration; Terraform does not carry VM Docker volumes to another host. See [Terraform migration notes](../terraform-deployment.md#existing-installations).

## Publishing application releases

Update `backend/pom.xml` and `frontend/package.json` together, update the frontend lockfile, and review bundled compatibility declarations. Tests require bundled declarations to support the packaged release. The template-release workflow checks that its requested version matches both application package versions before creating refs. Run `python deploy/github/check-release-version.py` to check the source versions locally, or pass the intended release version as its argument.
