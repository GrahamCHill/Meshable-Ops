# Kanban boards 1.2.0

The optional Kanban plugin provides a board-card directory and Jira-style **Board**, **Backlog**, **Tickets**, and **Summary** views. Calendar is version **1.1.1**; other bundled plugins use **1.0.0-a** alpha labels. Plugin versions are separate from the host release used by `compatibleVersions`.

## Boards and columns

Open **Boards**, find a board card, or choose **New board**. Boards can be private to an owner/administrators or linked to a project with optional team restrictions. Project/team access and plugin table permissions apply to every view and API.

Board managers use **Configure board** to add, name, reorder and remove empty columns, map reporting status, define epics/sprints, and configure project/team access. Managers can also drag column headings to reorder them. Private owners/administrators manage private boards; project managers and team leads manage project boards only when they have access. Creator IDs never bypass current project access.

Columns scroll horizontally on smaller screens; ticket lists scroll independently. Limits remain 20 columns, 200 tickets and 100 sprints per board, with up to 100 epics. Move or remove tickets before deleting their column.

## Ticket movement

Drag tickets between columns or before other tickets to change order. For keyboard or touch use, open a ticket and choose its **Column**. Moves save immediately using the board revision; concurrent edits return `409` and require reloading. Ticket and configuration changes save when submitted.

Every column has independent **move into** and **move out of** rules:

| Rule | Ordinary editors allowed |
| --- | --- |
| All board editors | Members with board write access |
| Ticket assignee | The saved assignee |
| Ticket reviewers | The saved reviewers |
| Selected members | Explicitly chosen internal board members |
| Board managers only | No ordinary members |

Both departure and destination rules must permit a move. Managers retain an override, while plugin/table and project/team access always apply. Empty selected-member lists allow managers only. The server checks saved policies and ticket identity; changing an assignee, reviewers or rules in the same request cannot grant a move. Deleting tickets also checks departure rules. Full-board writes and the dedicated move API enforce the same policy.

The destination column determines reporting status where configured. Entering In progress fills an omitted start date; completion fills an omitted completion date. Reopening clears completion. Legacy columns without a reporting status preserve ticket status.

## Tickets and summaries

Tickets support permanent references, Task/Story/Bug/Subtask types, priority, title, description, assignee, reviewers, dates, story points, blocked state, sprint, epic and multiple tags. Tags are board-local: up to 20 unique labels of 40 characters each. Epics have a name, description and color. Subtasks require an existing top-level parent on the same board; dangling, nested and cyclic relationships are rejected.

Combine text, assignee, epic, tag and sprint filters. Backlog shows tickets without a sprint. Cards show assignee initials, references, points, due state, tags, epic and subtask completion. Saved ticket dialogs include comments and explicit mentions; existing notification preferences, comment-table permissions and provider availability control delivery. Supporting Git plugins still supply repository/PR links.

**Summary** charts tickets by actual column, assignee, epic and tag, with completion, open/overdue totals and story points. Project Overview aggregates the same information across only the caller's visible boards. Restricted boards are excluded before aggregation; unavailable former assignees' names are masked.

## Dialog drafts and banners

Escape, Close, or clicking the backdrop dismisses dialogs. Pointer-down and pointer-up must both be outside, so selecting text does not dismiss them. Pending saves/sends prevent backdrop dismissal.

Ordinary form drafts remain in memory while staying on the same page/account: tickets, board configuration, calendar events, sharing/email recipients and add-user details. Successful saves clear their drafts. Navigating to another path or query resets them. Draft retention uses neither browser storage nor server persistence. Passwords in sharing/new-user/email dialogs are cleared on dismissal. PDF viewers remember their current page while staying on the same app page.

Gallery artwork remains visible in Administration → Plugins. Workspace banners are optional via `manifest.showPageBanner`, default `false`; Kanban leaves it off.

## APIs and retained data

| API | Purpose |
| --- | --- |
| `GET /api/plugins/kanban/boards?projectId=...` | Permitted board cards with ticket/completed counts |
| `GET /api/plugins/kanban/boards/{id}` | Content, revision, `canManage`, and permitted destination IDs per ticket in `moves` |
| `POST /api/plugins/kanban/boards` | Create a board |
| `PUT /api/plugins/kanban/boards/{id}` | Revision-checked edit with structure/movement guards |
| `POST /api/plugins/kanban/boards/{id}/cards/{cardId}/move` | `{columnId, revision, beforeId?}`; locked movement/reordering |
| `GET /api/projects/{id}/work-summary` | Authorized stage/assignee/epic/tag aggregates |

Columns add `enter`/`leave` objects (`mode`, `userIds`); content adds `epics`; cards add `tags`, `epicId`, `type`, `priority`, `parentId`. Defaults preserve existing JSON. Records stay in `plugin_kanban_boards`; no business data is seeded. Search indexing includes tags and epic text when enabled; existing boards adopt that text on their next save.

## Bulk plugin activation

In **Administration → Plugins**, tick **Enabled** on cards, then **Save activation changes**. Select/deselect all stages choices without sending them. Saving patches changed flags in a single transaction and retains role, table, search, credential and business-data settings. Restore removed plugins first. Dependencies, conflicts and global extension security still control effective availability.

`PUT /api/admin/plugins/activation` accepts `{"changes":[{"id":"kanban","enabled":true,"expectedRevision":0}]}`. Use the admin catalogue's `settingsRevision` (`-1` before a settings row exists). Up to 100 unique changes are accepted. Stale revisions return `409`; an invalid/removed choice rejects the entire batch. `plugin_settings.revision` supplies optimistic locking; existing rows start at zero during schema update. Admin authorization and delegated `admin.plugins` access apply.

Tests cover direct moves/full-board writes, saved-ticket authority, manager overrides, stale writes, tag/epic/subtask validation, summary aggregation and atomic activation. Browser checks use isolated sample data rather than changing the running workspace.
