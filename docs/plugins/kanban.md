# Kanban boards 1.6.0

The optional Kanban plugin provides a board-card directory and Jira-style **Board**, **Backlog**, **List**, and **Summary** views. The separate [Jira, Figma and Mermaid project plugins](project-integrations.md) start at **1.0.0**. Plugin versions are separate from the host release used by `compatibleVersions`.

## Boards and columns

Open **Boards**, find a board card, or choose **New board**. Boards can be private to an owner/administrators or linked to a project with optional team restrictions. Project/team access and plugin table permissions apply to every view and API.

Board managers use **Configure board** to add, name, reorder and remove empty columns, map reporting status, define epics/sprints, and configure project/team access. Managers can also drag column headings to reorder them. Private owners/administrators manage private boards; project managers and team leads manage project boards only when they have access. Creator IDs never bypass current project access.

New boards start with To Do, In Progress, Review, Testing and Done. Review and Testing report as In progress; managers can change that mapping. Existing boards retain their columns. Columns scroll horizontally on smaller screens; ticket lists scroll independently. Limits remain 20 columns, 200 tickets and 100 sprints per board, with up to 100 epics. Move or remove tickets before deleting their column.

## Ticket movement

Drag tickets between columns or above/below other tickets to change order. For keyboard or touch use, use a card's move up/down controls or open a ticket and choose its **Column**. Moves save immediately using the board revision; concurrent edits return `409` and require reloading. Ticket and configuration changes save when submitted. Each column also supports inline creation: Enter saves, Shift+Enter inserts a line, and Escape closes the draft.

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

### Visual workflow graph

In **Configure board → Workflow graph**, drag from a column's right handle to another column's left handle. Select an arrow to name the transition and choose who may use it. Keyboard users can use the From/To selectors and transition list. Node positions are saved with the board. Columns themselves remain managed under Columns & permissions.

Turn on **Enforce this workflow** to restrict movement to the connected arrows. Entry, exit and transition permissions all apply, using the saved ticket identity and saved workflow. Managers can override role restrictions but must still follow a connected path. Reordering within a column remains possible. An enforced graph without arrows prevents transfers. Existing boards default to enforcement off and keep their movement behavior.

## Tickets and summaries

Tickets support permanent references, Task/Story/Bug/Subtask types, priority, title, description, assignee, reviewers, dates, story points, blocked state, sprint, epic and multiple tags. Tags are board-local: up to 20 unique labels of 40 characters each. Epics have a name, description and color. Subtasks require an existing top-level parent on the same board; dangling, nested and cyclic relationships are rejected. Subtasks inherit their parent's sprint. Expand the card footer to open child tickets individually, or create a subtask from the parent ticket editor. A parent cannot move to Done while any subtask is unfinished.

Combine text, multiple assignees, epic, tag, sprint, type, priority and flagged filters. Avatar buttons toggle assignees; Clear filters resets the selection. Group the board into collapsible assignee or epic swimlanes. A matching subtask surfaces its parent once on the board. Cards show assignee initials, references, points, due warnings, tags, epic and expandable subtask completion. Saved ticket dialogs include comments and explicit mentions; existing notification preferences, comment-table permissions and provider availability control delivery. Supporting Git plugins still supply repository/PR links.

### Linked tickets and discussions

Ticket dialogs separate **Details** from **Discussion**. **Link ticket** adds Relates to, Blocks or Is blocked by relationships to another ticket on the same board. The other ticket shows the inverse relationship automatically. Edit an incoming relationship from its source ticket. Self-links, missing targets, duplicates and blocking cycles are rejected; related-ticket cycles are allowed. Up to 50 outgoing relationships are supported per ticket. A blocking link records a dependency; it does not itself prevent a status transition.

In Discussion, type `@` and choose a board member by name or email. Arrow keys and Enter select a suggestion; Escape dismisses suggestions and Ctrl+Enter posts. Selected mentions retain the member ID even with duplicate names. Replies attach to a specific comment and remain nested. Each comment has a permanent link that opens the ticket, highlights that comment and prepares a reply. Comments support up to 4,000 stored characters, 20 mentions and 200 comments per ticket. Drafts are separate for each reply target.

Mentions and replies to another person's comment publish private notifications after the comment commits. Messages include the comment content and its reply link. Existing notification rules, member preferences, provider availability and current board/comment permissions determine delivery. Teams requires a linked private Graph chat; Discord requires the member's bot identity link. Shared webhooks never receive these private notifications. Long messages are split to fit channel limits without dropping content, and each part has a deduplication key.

Set **Workspace URL for notification links** in Kanban's plugin configuration, or configure `APP_PUBLIC_BASE_URL`, so external messages contain a usable absolute link. Enable **Card mentions** and **Ticket discussion replies** on the intended notification methods and configure members' private provider identities.

### Estimation preferences

Open **Administration → Plugins → Kanban boards → Settings** to toggle **Enable story points** and **Enable T-shirt sizes** independently. Settings open within the administration portal. T-shirt sizes are XS, S, M, L, XL and XXL. New installations default to points on and sizes off. Projects inherit these organisation defaults. Administrators can select a project under **Project estimates** on the same settings page to override them. Estimation configuration is available only in Administration, with no project overview panel or board-configuration tab. The API requires an administrator with `admin.plugins` permission, project access, and the plugin's table permissions. Project managers and team leads cannot change estimation configuration through direct API calls. Members can still assign ticket estimates when the administrator enables that estimate type.

Both systems, either system, or neither can be enabled. Hidden estimates remain stored for later use; changing the setting does not erase existing values. Project settings apply across that project's boards; private boards use organisation defaults.

## Sprint planning

**Backlog** separates active and planned sprints from unscheduled work, with ticket/point totals. Drag a ticket between sections or use its sprint selector to move the whole parent/subtask family. Managers create sprints with dates and an optional goal, start a nonempty planned sprint, and complete the active sprint. One sprint can be active per board. The Board view defaults to that sprint; the sprint filter can show future work, backlog or a particular completed sprint.

Managers can use **Edit sprint** in Backlog to change its name, start date, inclusive end date and goal, including after starting it. These edits preserve lifecycle state and ticket assignments. When Calendar and Kanban are available, visible board sprints appear as virtual all-day date ranges alongside ticket deadlines. Calendar entries link back to the sprint; edits update the same virtual entry on the next load. The calendar uses the same project/team visibility predicate as the board.

Completing a sprint retains finished tickets in that sprint and atomically moves unfinished tickets, including all their subtasks, to the backlog or a selected planned sprint. Complete unfinished subtasks of Done parents first. Completed sprints cannot restart, receive new tickets, or be deleted. Reopening completed-sprint work returns the parent and its subtasks to the backlog. Legacy sprint JSON defaults to Planned without changing dates or ticket assignments.

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
| `POST /api/plugins/kanban/boards/{id}/sprints/{sprintId}/start` | `{revision}`; manager-only start with revision/row locking |
| `POST /api/plugins/kanban/boards/{id}/sprints/{sprintId}/complete` | `{revision, destinationSprintId?}`; atomic completion/carry-over, null destination means backlog |
| `GET /api/projects/{id}/work-summary` | Authorized stage/assignee/epic/tag aggregates |
| `GET/POST /api/plugins/kanban/boards/{id}/cards/{cardId}/comments` | Comments/replies; POST accepts `text`, optional legacy `mentionIds` and `parentId` |
| `GET/PUT /api/plugins/kanban/projects/{projectId}/estimation` | Admin settings only: effective preferences and revision-checked project override (`inherit`, `storyPoints`, `tshirtSizes`, `revision`); requires `admin.plugins`, project access and table permissions |

Columns add `enter`/`leave` objects (`mode`, `userIds`); content adds `epics` and `workflow` (`enabled`, `transitions`, `positions`). Cards add `tags`, `epicId`, `type`, `priority`, `parentId`, `tshirtSize` and `links` (`targetId`, `type`). Sprints add `status` (`PLANNED`, `ACTIVE`, `COMPLETED`) and `goal`. Comment mention tokens use `@[Display name](user:UUID)`; the UI presents readable names. Comments add nullable `parentId`; project estimate overrides use `plugin_kanban_estimation`. Defaults preserve existing JSON. No business data is seeded. Search indexing includes tags and epic text when enabled; existing boards adopt that text on their next save.

## Bulk plugin activation

In **Administration → Plugins**, tick **Enabled** on cards, then **Save activation changes**. Select/deselect all stages choices without sending them. Saving patches changed flags in a single transaction and retains role, table, search, credential and business-data settings. Restore removed plugins first. Dependencies, conflicts and global extension security still control effective availability.

`PUT /api/admin/plugins/activation` accepts `{"changes":[{"id":"kanban","enabled":true,"expectedRevision":0}]}`. Use the admin catalogue's `settingsRevision` (`-1` before a settings row exists). Up to 100 unique changes are accepted. Stale revisions return `409`; an invalid/removed choice rejects the entire batch. `plugin_settings.revision` supplies optimistic locking; existing rows start at zero during schema update. Admin authorization and delegated `admin.plugins` access apply.

Tests cover direct moves/full-board writes, saved-ticket authority, manager overrides, stale writes, sprint lifecycle/carry-over/reopening, tag/epic/subtask validation, summary aggregation and atomic activation. Frontend planning/filter/ranking tests run with `node --test tests/kanban-board.test.mjs` from `frontend/`. Browser checks use isolated sample data rather than changing the running workspace.

## Board creation authority

New private boards require an administrator, Business Manager, or current project manager/team lead. Linked boards require leadership in that specific project. Business Manager follows `USER` plugin/table policies and does not gain unrelated project access. Existing board ownership and contribution rules remain, including the ability to edit previously created boards. The host exposes `/api/plugins/kanban/permissions` for creation controls; server POST checks remain authoritative.

## Ticket permissions and sprint lifecycle

Under column settings, **Column purpose → Completed tickets (Done)** explicitly marks the completed destination. Moving a ticket there automatically sets `DONE` and fills a missing completion date; moving it out clears the completion date. Dedicated moves and full-board saves share this rule, including tickets in renamed completed columns. Legacy columns named Done, Complete or Completed receive that purpose only when no explicit status was stored. Existing already-completed tickets without dates retain their missing dates rather than acquiring fabricated history.

Choose **Column purpose → Work started (set start date)** for the first working column. Its header shows a Work started badge. Entering it, including creating a ticket there, fills a missing start date with that day's UTC date; a saved date is preserved through later moves and re-entry. Ordinary saves in that column do not backfill historical dates. Review/Testing may remain ordinary In progress columns. New boards tag their In progress column by default. Older In progress/Work started columns inherit the tag only when `startsWork` was never explicitly configured; choosing ordinary In progress saves `startsWork: false`. This flag is part of manager-only column configuration and requires `IN_PROGRESS` status. Lead time in new reports uses start date through completion, with incomplete date coverage shown. Creation timestamps remain immutable provenance.

**Configure board > Ticket access** is available to administrators. Creation can allow any member with board write access, selected board members, or administrators. Deletion always requires an administrator with board write access and can be narrowed to selected administrators. Project and team restrictions continue to apply. Saved permissions govern full-board API updates as well as dedicated actions; including a new permission in the same request cannot authorize that request.

Ticket deletion asks for confirmation and removes its subtasks, comments and repository links. The API requires the saved ticket reference, current board revision and permission. Bulk board writes must explicitly list every removed ticket ID in `confirmedTicketDeletions`.

Managers can edit a planned sprint and choose **Delete planned sprint**, then confirm. Its tickets return to the backlog. Active and completed sprints cannot be deleted.

On completion, a snapshot queues an automatic retrospective when a chat provider is enabled in **Administration > App Intelligence**. A background worker makes a single attempt; completion is independent of the provider. Backlog shows the retrospective or its status under the completed sprint. With no AI, the status is `NOT_CONFIGURED`; an unavailable provider yields `UNAVAILABLE`. The model receives ticket titles, states, estimates and blockers; suggestions are labelled as suggestions. Retrospectives live in the Kanban-owned `plugin_kanban_retrospectives` table, follow its RBAC and clear with Kanban data. Interrupted attempts may remain `GENERATING` and require operator review rather than automatically repeat an external request.

| Action | API |
| --- | --- |
| Delete ticket and its subtasks | `POST /api/plugins/kanban/boards/{id}/cards/{cardId}/delete` with `{revision, confirmation: "saved-ticket-reference"}` |
| Delete planned sprint | `POST /api/plugins/kanban/boards/{id}/sprints/{sprintId}/delete` with `{revision, confirmation: "DELETE"}` |
| Configure ticket permissions | `PUT /api/plugins/kanban/boards/{id}` with `content.ticketAccess` |

`ticketAccess` contains `createMode` (`ANY_MEMBER`, `SELECTED_MEMBERS`, `ADMINS`), `creatorIds`, `deleteMode` (`ALL_ADMINS`, `SELECTED_ADMINS`), and `deleterIds`. Board responses expose `canCreateTickets`, `canDeleteTickets`, `canManageTicketAccess`, and a sprint-keyed `retrospectives` map. Refresh the board to retrieve a finished background result.
