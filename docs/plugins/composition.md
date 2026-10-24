# Connecting supporting plugins

Plugins declare capabilities they provide, required capabilities, required plugin IDs (`requires`), optional relationships (`extends`), and author-defined incompatibility reasons (`incompatibleWith`). See [relationship and presentation metadata](presentation-and-lifecycle.md). `extends` documents integration and does not activate a provider or implement an adapter. **Administration → Plugins → Settings → Connections** shows what each plugin provides, which features are available, and missing support. The status is calculated from activation and role access; it does not prove that provider credentials work.

```kotlin
override val manifest = PluginManifest(
    "planning", "Planning", "1.0.0", "Planning tools",
    capabilities = mapOf("planning.tasks" to "Planning tasks"),
    requiresCapabilities = setOf("calendar.events"),
    features = listOf(PluginFeature("development", "Repository links", setOf("source.repositories"))))
```

Required capabilities prevent activation until an enabled plugin supplies them for the current role. Dependency chains resolve from independent active plugins; cycles do not activate themselves. Optional features leave the base plugin usable when their providers are missing. Neither mechanism automatically enables modules or changes roles. Capability IDs are stable contracts; plugin authors must coordinate compatible behavior and test their integration. A capability label alone is not an executable adapter.

Call `registry.requireFeature("planning", "development", actor.role)` and `registry.requireCapability("planning", "source.repositories", providerId, actor.role)` at the operation boundary. These checks confirm that the named provider is active for this role. Then call a reviewed service/interface, applying its table policies, ownership rules, provider allowlist, configuration validation, and rate limits. Recheck access after slow remote reads before returning sensitive results or saving metadata. Do not resolve arbitrary class names or execute scripts from a manifest.

`GET /api/plugins` returns only active plugins for the current user. The admin catalogue also returns `enabled`, effective admin `active`, `missingDependencies`, and optional `features` (`available`, provider display names, missing capability labels). `PluginView.manifest` includes these relationships, `capabilities`, and `requiresCapabilities`; `compatibilityIssues` explains author-declared conflicts or an unsupported host API version. A provider restricted to administrators cannot satisfy a member's dependencies.

## Included connections

| Consumer | Supporting capability | Behavior |
| --- | --- | --- |
| Workflows | Documents `documents.pdf` | Optional PDF/email steps; basic workflows remain usable without Documents and saved PDF runs pause while support is unavailable |
| Kanban | GitHub/GitLab/Gitea `source.repositories` | Link approved repositories and PRs/MRs to boards or individual cards; explicitly refresh snapshots |
| Calendar | Kanban `kanban.boards` | Show saved card due dates as virtual all-day events, restricted to visible boards |
| Calendar | `calendar.provider` | Read selected Microsoft, Google, or CalDAV calendars; explicitly publish saved local events |
| Calendar | `calendar.personal-sync` | Opt-in per-member two-way appointment sync, with independent encrypted credentials and owner-only imported events |
| Microsoft Calendar | SharePoint/OneDrive `microsoft.graph.credentials` | Optionally reuse the explicitly selected enabled plugin's tenant/client credentials; calendar permission still needs separate consent |
| Projects | Kanban `kanban.boards` | Attach boards with project/team ACLs, generate scoped PDF reports, and build recipient-specific work digests |
| Projects / Clients | Manifest `contextTargets` | Authorized extension links and compiled computed panels; context does not grant record access |
| Card @mentions | Registered member notification adapters | Deliver through recipient preferences; private events exclude shared fixed destinations |

Disabling support blocks connected operations without deleting local events, board links, or credentials. Unavailable source-control links have their title, repository, URL, status, and check time redacted; an authorized board owner/admin can still remove the local link. The Calendar page and Kanban base functionality work independently. Provider metadata and calendar events are not ingested into workspace search. Kanban's existing search opt-in indexes board/card text only.

`CalendarProvider` is the compiled adapter contract for new calendar implementations. `NotificationChannelAdapter` remains the separate delivery contract for messaging integrations. Both are Spring beans reviewed with the application; no downloaded plugin code or untrusted runtime execution is introduced.
