# GitHub, GitLab, and Gitea

These are three independent plugins with encrypted access tokens and their own settings menus. Configure a provider in **Administration → Plugins → Settings → Configuration**, then enable it in **General**. Enable Kanban as well to use board interactions. Nothing is sent to a repository and no webhooks are registered automatically.

| Plugin | Default/API base setting | Repository path |
| --- | --- | --- |
| `github` | Blank uses `https://api.github.com`; GitHub Enterprise uses its HTTPS API base including `/api/v3` | `owner/repo` |
| `gitlab` | Blank uses `https://gitlab.com`; self-managed base excludes `/api/v4` | `group/repo` or `group/subgroup/repo` |
| `gitea` | Required HTTPS server base, including any installation prefix; exclude `/api/v1` | `owner/repo` |

Save the provider URL/access token, then choose **All repositories accessible to this token** or **Selected repositories**. Selected is the default and preserves existing allowlists. **Load repositories from provider** queries GitHub's user repositories, GitLab membership projects, or Gitea's user repositories, with 100 results per page and a Load more action. Choose at most 100 paths within 4096 characters. Every selected-mode lookup checks the allowlist before a network request. All mode permits repository lookups only through the saved token's provider permissions; it does not make private repositories public. Admin discovery works before activation so a connection can be configured safely.

Use a read-only token scoped to intended repositories. GitHub fine-grained tokens need repository metadata and pull-request read permissions. For GitLab, use a suitable read-API token; Gitea token scopes depend on its installed version. Discovery lists resources exposed by the provider's user/membership endpoint and token type; narrowly scoped installation/project tokens may need an existing manually configured path. Account consent and repository permissions are managed at the provider.

Each provider defaults to administrator access. The administrator may explicitly enable all signed-in users. Enabling member access shares metadata for the approved repositories using the saved server account. It does not use individual members' Git credentials. Members still need Kanban table access and ownership of the board; administrators can manage all boards.

## Board and card links

Save a board before adding links. Under **Repository and pull request links**, select the provider, an approved repository, and either the repository or an open pull/merge request. An existing request number can also select a closed request. Attach it to the board or a specific saved card. A board holds at most 100 links. The selector fetches the first 30 open requests; it does not load all pages.

Links store the provider's title, HTTPS URL, state, and check time. Opening a saved board reads these snapshots. **Refresh** fetches current provider metadata. **Remove link** only removes the local relationship. Deleting a card removes its relationships on the next successful board save. This version does not move cards automatically, modify repositories/PRs, receive development webhooks, clone code, or synchronize issues. Provider responses cannot supply executable UI or arbitrary navigation schemes.

Owned tables: `plugin_kanban_development_links` (child) and `plugin_kanban_boards` (parent). Both have independent member read/write policies in Kanban settings; board ownership applies to links too. Clearing Kanban deletes both tables' rows in child-first order, retaining settings/schema. Disabling a source provider retains links but hides their sensitive metadata and blocks refresh/add until access returns.

## Project links

Open a saved project's **Repositories** section to link a repository without requiring Kanban. A project manager, team lead, or administrator can add, refresh, and remove links; project membership is required to read them. Only active, allowed Git providers appear. Links store a sanitized title, HTTPS URL and check time. Disabling a provider, removing repository approval, or changing provider configuration hides saved metadata until a manager refreshes it under the new connection. Removal remains available when the provider is disabled. Repository links do not grant project membership or provider access.

Project Overview places **Project boards** immediately before the delivery summary. Each visible Kanban board opens directly; a linked Jira project opens its selected board. Repository shortcuts appear beside this summary and above Kanban's view tabs. Repository links use a new browser window/tab with opener access disabled.

Under **Development → Development settings**, project managers can independently toggle repository links, branch tracking, pull/merge-request tracking and deployment tracking. Repository links and requests default on; new branch/deployment tracking defaults off. Repository links are the parent switch. Disabling a feature hides its saved links and blocks add/refresh through the API without deleting data. Settings also apply to project boards' existing repository/request links. Private boards keep their existing policies.

Use **Link development work** to choose a linked project repository and enter a branch name, request number (including closed requests), or deployment ID. On a Kanban board, optionally attach the resource to a specific ticket. It then appears on Project Overview with a link back to that board/ticket, and on the matching board. Project-level resources appear across the project's boards. Resources attached to a restricted board remain visible only to members who can read that board and its Kanban table. Existing board/card repository and PR links also appear under **Linked from Kanban boards**, with the same live board and provider access checks.

Branch refresh reads the provider's branch metadata, including protected state. Request refresh reads open/closed/merged state. GitHub deployment refresh reads the deployment and latest status; GitLab reads deployment status, environment and ref. A run URL within the approved repository is used when supplied; otherwise the link opens that repository's deployment/environment view. Grant the token branch/content and deployment read access as needed. Gitea deployment runs are linked manually within the approved repository, with an explicit environment/status; managers can update their status or **Recheck link**. No deployment is started and no external repository data is changed.

Project development links are bounded to 200 per project. Duplicate relationships, cross-project repositories/boards, unavailable tickets, stale revisions and provider changes are rejected. Refresh remains an explicit action; the displayed check time identifies the saved snapshot.

## API

- `GET /api/plugins/{github|gitlab|gitea}/repository-options?page=1` returns `{repositories,page,hasMore}`. Selected mode reads the allowlist locally; All mode queries the provider. The legacy `/repositories` route returns only the first page.
- `GET /api/admin/plugins/{github|gitlab|gitea}/repository-options?page=1` discovers repositories using saved credentials before activation; requires plugin administration.
- `GET/POST /api/projects/{projectId}/repositories` lists/adds project repository links. Add accepts `{providerId,repository}`. `POST /{linkId}/refresh?revision=N` and `DELETE /{linkId}?revision=N` use optimistic revisions.
- `GET/PUT /api/projects/{projectId}/development/settings` reads/saves `{features:{repositories,branches,pullRequests,deployments},revision}`; only project managers/leads/admins can save.
- `GET/POST /api/projects/{projectId}/development/links` lists/adds `{repositoryId,kind,reference,boardId?,cardId?}`; `kind` is `BRANCH`, `PULL_REQUEST` or `DEPLOYMENT`. Gitea deployments also accept `manualUrl`, `environment` and `state`.
- `POST .../development/links/{id}/refresh?revision=N`, `DELETE .../development/links/{id}?revision=N`, and `PUT .../development/links/{id}/status` (`{revision,state}` for manual deployments) use optimistic revisions.
- `GET /api/projects/{projectId}/development/board-links` aggregates up to 200 existing repository/request links from authorised project boards; board/ticket scope is applied before the result limit.
- `GET /api/plugins/{github|gitlab|gitea}/pulls?repository=owner%2Frepo` returns up to 30 open requests (30 calls/minute/user/provider).
- `GET /api/plugins/kanban/boards/{id}/development-links` lists owner-visible snapshots.
- `POST` to that route accepts `{ "providerId":"github", "repository":"owner/repo", "number":12, "cardId":null }`. Omit/null `number` for the repository and `cardId` for the whole board.
- `POST /api/plugins/kanban/boards/{id}/development-links/{linkId}/refresh` refreshes one link.
- `DELETE /api/plugins/kanban/boards/{id}/development-links/{linkId}` unlinks it locally.

Add/refresh is limited to 30 calls/minute/user. Plugin/table/ownership checks also apply to direct API requests. Endpoint bases are HTTPS admin configuration; credentials in URLs, traversal, redirects, and private DNS destinations are rejected. To connect a trusted self-hosted Git or CalDAV server on a private network, explicitly set `APP_PLUGIN_ALLOW_PRIVATE_TARGETS=true` and recreate the backend. This deployment-wide setting permits private network targets for the integration HTTP client; keep it off unless needed. Responses are bounded to 1 MiB with timeouts, and errors omit tokens, request URLs, and provider bodies.

Provider references: [GitHub pull requests](https://docs.github.com/en/rest/pulls/pulls), [GitLab merge requests](https://docs.gitlab.com/api/merge_requests/), and [Gitea API](https://docs.gitea.com/api/).

Deployment references: [GitHub deployments](https://docs.github.com/en/rest/deployments/deployments), [GitLab deployments](https://docs.gitlab.com/api/deployments/) and [GitLab branches](https://docs.gitlab.com/api/branches/).
