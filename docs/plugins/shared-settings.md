# Split and shared plugin settings

Settings are native administration pages. `adminMenu` places the main settings page; `settingsSections` moves selected local fields into additional existing or author-defined categories. Fields stay in the same encrypted plugin configuration and use partial updates and its optimistic revision. One field can belong to only one extra section; unassigned fields stay on the main page.

Manifest fragment (retain your plugin identity and backend declarations):

```json
{
  "extensions": ["API", "SETTINGS"],
  "hasAdminSettings": true,
  "adminMenu": {"category": "operations", "label": "Invoice options"},
  "settingsFields": [
    {"key": "currency", "label": "Currency", "defaultValue": "USD"},
    {"key": "readReplies", "label": "Process replies", "type": "BOOLEAN"}
  ],
  "settingsSections": [{
    "id": "email-replies", "label": "Invoice email replies",
    "category": "communications", "categoryLabel": "Communications",
    "description": "Choose a shared incoming mailbox.", "fields": ["readReplies"]
  }],
  "sharedSettings": [{
    "id": "incoming-email", "schemaId": "communications.imaps",
    "label": "Incoming email connection", "sectionId": "email-replies",
    "fields": ["host", "port", "username", "password", "folder"]
  }]
}
```

The main page is `/admin/sections/{category}/plugins/{id}`. Extra sections are `/admin/sections/{category}/plugins/{id}/{sectionId}`. Disabled, removed or unavailable plugins expose neither. A shared schema's owning plugin must also remain enabled; consumers retain their selections while it is off. Declare `requires` when the provider is essential, or `extends` for optional integration, as well as the shared reference.

## Named reusable profiles

The host provides `communications.imaps` under **Communications → Incoming email (IMAPS)**. SMTP/API sending remains the common application email delivery service, not a second copy of SMTP credentials in each plugin. [Email configuration](../email-delivery.md) explains the protocols and Billing's migration.

Plugins may also provide schemas in `sharedSettingsSchemas`. Schema IDs must use `plugin.{owner-id}.…`. The host adds their profile manager to the declared admin category. Other plugins declare a `sharedSettings` reference with the schema ID and an explicit field allowlist. An administrator must select a profile for each reference; a declaration alone grants no connection. Multiple plugins can select the same profile. Links are stored with the consumer's encrypted configuration, not as copied credentials.

For example, a plugin with ID `mail-tools` can provide:

```json
"sharedSettingsSchemas": [{
  "id": "plugin.mail-tools.account", "label": "Mail service",
  "description": "Shared service credentials for reviewed integrations.",
  "adminMenu": {"category": "communications", "label": "Mail service connections", "icon": "mail"},
  "fields": [
    {"key": "origin", "label": "HTTPS origin", "required": true},
    {"key": "token", "label": "API token", "type": "SECRET", "required": true, "maxLength": 4096}
  ],
  "identityFields": ["origin"], "httpOriginField": "origin"
}]
```

`identityFields` are non-secret server/account identifiers. Changing one clears omitted stored credentials so an old password is not sent to a new destination. Defaults are persisted with the profile so later plugin versions cannot silently change its destination. `httpOriginField`, when supplied, must be a required text identity field containing a canonical HTTPS origin without a path/query. Omit it for non-HTTP connections such as IMAPS; those credentials cannot be injected into HTTP calls. The host checks the origin and credential from the same profile snapshot during concurrent edits. Use schemas with stable meanings and never change a secret into a public field; the profile also retains historical secret-field names to avoid exposing old values after schema changes.

Admins can save disabled incomplete drafts, enable complete profiles, rename them, replace/clear secrets, and disable them without losing links. The profile manager does not test or contact a service on save. Incoming connections require the `admin.notifications` feature; plugin-owned schemas and consumer selections require `admin.plugins` plus the schema's permissions. Plugin-only admins cannot bypass Communications restrictions through the alternate API path.

## Runtime access

Reviewed in-process Kotlin code calls `PluginConfigurationService.shared(pluginId, referenceId)` after its ordinary plugin/record permission checks. This server-only map is limited to the reference's declared fields. Never return or log it.

Uploaded Python/Kotlin workers use `settings.read` route permission for **non-secret** values:

```python
account = host.shared_settings('account')
# Kotlin: host.sharedSettings("account")
```

For a shared HTTP token, the consumer must declare `origin`/`token` in its reference and the exact destination in `backend.allowedOrigins`. Its route needs `http`. The host injects the credential only at the profile's configured origin:

```python
result = host.http('POST', 'https://api.example.com/jobs', body={'title': 'Review'},
    credential={'sharedReference': 'account', 'key': 'token',
                'header': 'Authorization', 'prefix': 'Bearer '})
```

Neither browser bundles nor workers receive raw shared secrets. Workers still have no direct network/IMAP access; uploaded reply processors need an appropriate reviewed host adapter rather than downloading a mailbox password. Shared settings do not grant arbitrary cross-plugin table access or new host operations.

## APIs and persistence

All endpoints require an administrator and the appropriate feature permissions. Profile IDs are UUIDs.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/admin/email/shared-settings/schemas/{schemaId}` | Schema metadata and encryption readiness |
| `GET …/schemas/{schemaId}/profiles` | Named profiles with non-secret values, configured-secret names and revisions |
| `POST …/schemas/{schemaId}/profiles` | Create `{name, enabled, values, clearSecrets: [], revision: -1}` |
| `PUT …/schemas/{schemaId}/profiles/{id}` | Partial field update with the saved revision; blank secrets preserve, explicit clears remove |
| `GET /api/admin/plugins/{id}/shared-settings` | Declared references, available redacted profiles, current selections/revision |
| `PUT /api/admin/plugins/{id}/shared-settings/{reference}` | `{profileId: "uuid-or-null", revision: 0}`; `null` disconnects |

Plugin-owned profile managers use `/api/admin/plugins/shared-settings` as the equivalent profile API prefix. Each prefix still enforces schema permissions. Stale writes return `409`. The binding revision is the consumer's configuration revision, so changing a binding and editing local settings cannot silently overwrite each other. At most 100 profiles per schema are supported. Disable unused profiles; deletion is not exposed.

`workspace_shared_settings` stores schema ID, name, enabled flag, encrypted configuration JSON, historical secret-field names and revision. Entire JSON uses AES-GCM with the application's persistent wrapping key and participates in startup key rotation. Profiles and selections survive upgrades and plugin deactivation/removal; clearing a plugin's business tables does not delete shared profiles used by other plugins. They are excluded from search and metadata JSON exports. Back up PostgreSQL and wrapping keys together. 
