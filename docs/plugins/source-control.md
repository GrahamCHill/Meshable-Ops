# GitHub, GitLab, and Gitea

These are three independent plugins with encrypted access tokens and their own settings menus. Configure a provider in **Administration → Plugins → Settings → Configuration**, then enable it in **General**. Enable Kanban as well to use board interactions. Nothing is sent to a repository and no webhooks are registered automatically.

| Plugin | Default/API base setting | Repository path |
| --- | --- | --- |
| `github` | Blank uses `https://api.github.com`; GitHub Enterprise uses its HTTPS API base including `/api/v3` | `owner/repo` |
| `gitlab` | Blank uses `https://gitlab.com`; self-managed base excludes `/api/v4` | `group/repo` or `group/subgroup/repo` |
| `gitea` | Required HTTPS server base, including any installation prefix; exclude `/api/v1` | `owner/repo` |

Set **Allowed repositories** to at most 20 comma-separated paths. Every lookup is checked against this allowlist before a network request. Use an appropriate read-only token scoped to the selected repositories. GitHub fine-grained tokens need repository metadata and pull-request read permissions. For GitLab, use a suitable project/group/personal read-API token; Gitea token scopes depend on its installed version. Account consent and repository permissions are managed at the provider, not granted by Meshable Ops.

Each provider defaults to administrator access. The administrator may explicitly enable all signed-in users. Enabling member access shares metadata for the approved repositories using the saved server account. It does not use individual members' Git credentials. Members still need Kanban table access and ownership of the board; administrators can manage all boards.

## Board and card links

Save a board before adding links. Under **Repository and pull request links**, select the provider, an approved repository, and either the repository or an open pull/merge request. An existing request number can also select a closed request. Attach it to the board or a specific saved card. A board holds at most 100 links. The selector fetches the first 30 open requests; it does not load all pages.

Links store the provider's title, HTTPS URL, state, and check time. Opening a saved board reads these snapshots. **Refresh** fetches current provider metadata. **Remove link** only removes the local relationship. Deleting a card removes its relationships on the next successful board save. This version does not move cards automatically, modify repositories/PRs, receive development webhooks, clone code, or synchronize issues. Provider responses cannot supply executable UI or arbitrary navigation schemes.

Owned tables: `plugin_kanban_development_links` (child) and `plugin_kanban_boards` (parent). Both have independent member read/write policies in Kanban settings; board ownership applies to links too. Clearing Kanban deletes both tables' rows in child-first order, retaining settings/schema. Disabling a source provider retains links but hides their sensitive metadata and blocks refresh/add until access returns.

## API

- `GET /api/plugins/{github|gitlab|gitea}/repositories` returns the configured repository allowlist without contacting the provider.
- `GET /api/plugins/{github|gitlab|gitea}/pulls?repository=owner%2Frepo` returns up to 30 open requests (30 calls/minute/user/provider).
- `GET /api/plugins/kanban/boards/{id}/development-links` lists owner-visible snapshots.
- `POST` to that route accepts `{ "providerId":"github", "repository":"owner/repo", "number":12, "cardId":null }`. Omit/null `number` for the repository and `cardId` for the whole board.
- `POST /api/plugins/kanban/boards/{id}/development-links/{linkId}/refresh` refreshes one link.
- `DELETE /api/plugins/kanban/boards/{id}/development-links/{linkId}` unlinks it locally.

Add/refresh is limited to 30 calls/minute/user. Plugin/table/ownership checks also apply to direct API requests. Endpoint bases are HTTPS admin configuration; credentials in URLs, traversal, redirects, and private DNS destinations are rejected. To connect a trusted self-hosted Git or CalDAV server on a private network, explicitly set `APP_PLUGIN_ALLOW_PRIVATE_TARGETS=true` and recreate the backend. This deployment-wide setting permits private network targets for the integration HTTP client; keep it off unless needed. Responses are bounded to 1 MiB with timeouts, and errors omit tokens, request URLs, and provider bodies.

Provider references: [GitHub pull requests](https://docs.github.com/en/rest/pulls/pulls), [GitLab merge requests](https://docs.gitlab.com/api/merge_requests/), and [Gitea API](https://docs.gitea.com/api/).
