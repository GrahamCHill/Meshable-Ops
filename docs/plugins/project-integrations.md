# Jira, Figma and Mermaid project extensions

These optional bundled plugins each start at **1.0.0**. Enable them in Administration → Plugins and grant the intended plugin/table roles. They extend Project Overview and have their own project selector pages. Project members can read linked resources; project managers, team leads and administrators can change them when plugin table policy also allows it. Disabling a plugin retains its data. Clearing its declared tables removes the local links/snapshots or diagrams, not the project or external service content.

## Jira

Jira lets teams keep work items in Jira and extract a delivery snapshot into this workspace. Configure:

- **Jira API base URL**: an HTTPS Jira site, or `https://api.atlassian.com/ex/jira/{cloudId}` for scoped Cloud API tokens. Do not append `/rest/api`.
- **Jira browser URL**: the human-facing site. Required when the API base is Atlassian's API gateway.
- **Deployment type**: `CLOUD` (default) or `DATA_CENTER`.
- **Jira account email / API token**: Cloud uses email plus an API token; Data Center uses a personal access token as Bearer authentication. Use read-only access to the intended projects.
- **Approved project keys**: a comma-separated allowlist local project managers may link.
- **Story points field**: optional custom field ID such as `customfield_10016`. Blank omits point totals.

On a project, enter an approved Jira project key and optionally a display name. Linking verifies project metadata. **Refresh metrics** reads issues and saves total, done, in-progress, to-do, overdue, completed in the last 30 days, status/type breakdowns and optional point totals. Counts use Jira status categories; issues are deduplicated by ID. A refresh reads at most 50 pages / 5,000 issues and explicitly marks a partial snapshot if more remain. Refresh timestamps are displayed. No issue descriptions, comments or individual assignee profiles are stored, and no Jira issues are created or edited. Unlinking affects only the local connection.

Cloud uses cursor pagination at `POST /rest/api/3/search/jql`; Data Center uses offset pagination at `POST /rest/api/2/search`. Provider requests use administrator-configured endpoints, fixed paths, bounded responses, public DNS validation and no redirects. API tokens stay in encrypted server configuration. Project/table access, configuration changes and optimistic revisions are checked again before a fetched snapshot is saved.

After linking a project, choose **Choose Jira board** and select a board to create a direct shortcut. The selector reads up to 250 visible boards filtered to that approved project using Jira's Agile API; the token needs board-read permission. Saving verifies the choice against the filtered list and the link revision. The shortcut appears near the top of Project Overview and in the Jira panel, opens the actual board in a separate browser window/tab and survives metric refreshes. Clearing the shortcut preserves metrics and the project link. Existing links can choose a board without relinking their project.

| API | Purpose |
| --- | --- |
| `GET/POST /api/plugins/jira/projects/{projectId}/links` | List or link an approved key (`key`, optional `label`) |
| `POST .../links/{id}/sync` | Refresh with `{revision}` |
| `GET .../links/{id}/boards` | List up to 250 visible boards for the approved Jira project |
| `PUT .../links/{id}/board` | Select/clear a direct board shortcut with `{revision,boardId?}` |
| `DELETE .../links/{id}?revision=...` | Unlink locally |

Snapshots and links use `plugin_jira_projects`.

Provider references: [Jira enhanced issue search](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-search/) and [Atlassian API token endpoint formats](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account).

## Figma

Link a named HTTPS Figma design, file, prototype, FigJam board or slides URL. Links work without credentials, and users open the original in Figma with their existing Figma access.

For **Refresh metadata**, configure an optional Figma personal access token with `file_metadata:read` and **Approved file keys**. Knowing a file URL does not bypass the allowlist. The plugin reads `GET /v1/files/{key}/meta` from Figma's fixed API origin and retains only file name, modification time, version and editor type. It does not import file content, contacts or comments. Private tokens never reach the browser. Unlinking leaves the original design intact.

| API | Purpose |
| --- | --- |
| `GET/POST /api/plugins/figma/projects/{projectId}/files` | List or link (`label`, `url`) |
| `POST .../files/{id}/refresh` | Refresh approved metadata with `{revision}` |
| `DELETE .../files/{id}?revision=...` | Unlink locally |

References and metadata use `plugin_figma_files`. See [Figma file metadata](https://developers.figma.com/docs/rest-api/file-endpoints/#get-file-metadata).

## Mermaid

Create a named project diagram, edit Mermaid text and preview it locally. Supported diagrams come from the bundled Mermaid renderer. Save when syntax is valid; closing the editor retains its page draft. Open a saved diagram to view it, edit it, download its `.mmd` source or remove it.

Sources are limited to 20,000 characters and the renderer limits supported graph edges to 200. Runtime configuration directives, front matter, embedded images/HTML resources and external styles are rejected before rendering. Rendering uses Mermaid strict mode and DOMPurify, then an isolated sandbox frame with a content security policy that blocks external resource requests. Source is not sent to an external rendering service. Invalid previews show an error and keep the source editable.

| API | Purpose |
| --- | --- |
| `GET/POST /api/plugins/mermaid/projects/{projectId}/diagrams` | List or create (`title`, `source`, `revision=-1`) |
| `PUT .../diagrams/{id}` | Revision-checked edit |
| `DELETE .../diagrams/{id}?revision=...` | Remove the local diagram |

Sources use `plugin_mermaid_diagrams`. See [Mermaid usage](https://mermaid.js.org/config/usage.html).

All three plugins limit a project to 100 records each. Stale edits and removals return `409`; unknown project resources return `404`. No live provider calls or notifications are sent by automated tests or the sample preview.
