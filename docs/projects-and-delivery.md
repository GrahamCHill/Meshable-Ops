# Clients, projects, and delivery

The portfolio displays projects as responsive cards, grouped into workspace-defined sections, retaining section, text, client and multi-tag filters. Cards show each project's icon, client, section, tags and update time. On a project detail page, managers/team leads/admins can choose a built-in icon or upload WebP/PNG/JPEG up to 1 MiB (128 × 128 px recommended). Image reads enforce project membership; icons have their own revision, so editing artwork does not overwrite project details. [Repository links](plugins/source-control.md#project-links) connect projects directly to enabled Git providers without requiring a board.

**Projects** is an internal-member workspace at `/projects`. The directory supports text search, configurable sections, and multiple tag filters (all selected tags must match). Workspace search also includes authorized project matches. Lists are bounded to 200 results; narrow filters for larger portfolios.

## Project sections

Cards are grouped beneath ordered section headings, with a visible count and an empty-state message for each section. The initial sections are **Proposals**, **Active**, and **Archived**. All sections are shown by default; use the Section filter to focus on one. Counts reflect the current filters and loaded results.

Open **Administration → People & security → Project settings → Sections** to create, rename, reorder, or remove sections and choose the default for new projects. Use up to 20 sections with unique names of at most 80 characters. For example, rename the defaults and add a fourth section to use **Prototypes**, **Active development**, **Deployed**, and **Sunsetted**. Settings apply to the workspace.

Section IDs are stable: renaming or reordering does not move projects. A section containing projects cannot be removed; authorized project leaders/managers/admins must move those projects first through **Edit project → Details → Section**. Ordinary project members cannot change sections, and section membership grants no additional access. Existing `PROPOSAL`, `ACTIVE`, and `ARCHIVED` assignments are preserved on upgrade; legacy settings automatically receive the original sections. New projects use the configured default, including API creates that omit `category`.

## Clients, contacts, and files

A client can have many projects. Set contact details on each client in **Projects → Clients**. In **Edit project → Contacts & links**, choose **Use client contacts** to follow the linked client’s current list, or **Override for this project** to replace it with a project-specific list. An override may be empty. **Copy client contacts to project** makes an independent editable copy; subsequent client changes do not affect it. Projects without a client use only their own contacts. There is no app-wide default contact list. The app name may still identify an unlinked project.

Existing nonempty project contact lists become project overrides; existing empty lists inherit the linked client. Previously saved app-wide contacts are ignored, without altering existing client/project data. Contact edits retain the existing client-owner/admin and project-leadership permissions. Members see the effective list with its source. Projects also retain members, web links, and S3/Blob file or directory references.

Client owners and administrators edit inherited details; authorized project members can read them. Storage references never grant file access: each viewer still needs connection, root, and path permission. Hidden references are omitted from views and preserved during edits by members who cannot see them. PDF links open the existing viewer, subject to policy. Other files open their containing folder.

## Roles and team boundaries

Global roles remain Super admin, Admin, Member, and External. Projects add scoped **Member**, **Team lead**, and **Manager** assignments. Project leads have Team lead authority. Only project managers, team leads, and authorized administrators edit projects, change sections, manage membership, configure project schedules, or send team reports/digests. External accounts cannot use projects.

Admins create described groups with Team or Department kind, a project role, members, and tags. Explicitly linking a **Team** to a project grants its members that project role. Department membership and tags classify work without granting access. A member can belong to one department and a separate project team without seeing other teams' work.

Boards can restrict visibility to selected teams linked to their project. Project leads, project managers, and administrators retain oversight. A team lead from another team does not bypass this restriction. The same rules apply to APIs, board search, calendar deadlines, reports, and digests. Feature and plugin/table permissions still apply. Creator IDs are provenance, not permanent project access.

## Boards and tickets

Enable **Kanban boards** under Plugins. Open boards from the card directory or a project's **Open project boards** link. Managers configure columns, permissions and project/team access separately from everyday tickets. Drag tickets between columns, or open a ticket and choose its column. Backlog plans future sprints; managers start and complete sprints with unfinished work carried forward. Changes save immediately; optimistic revisions reject stale saves. See [Kanban 1.3.0](plugins/kanban.md).

Tickets support stable references, types/priorities, descriptions, assignees, reviewers, dates, sprints, points, blocked flags, tags, epics and subtasks. Columns supply reporting status and independent entry/exit permissions. Completing fills an omitted completion date; reopening clears it. Assignees/reviewers and selected transition members must have internal board access. Project Overview charts current tickets by actual column/stage, assignee, epic and tag across only visible boards.

Saved tickets support comments and explicit @mention selections. Recipients are checked against board access; after commit, notifications use each member's preferences. Private mentions never fall back to shared chat channels. Unavailable delivery does not remove the comment. Removing a ticket removes its comments and development links.

Enabled GitHub, GitLab, or Gitea plugins provide repository and PR/MR links for boards and tickets. Approved repositories, provider permissions, and board access are checked independently. See [source-control setup](plugins/source-control.md).

### Delivery indicators

**Delivery risk** is restricted to project managers, team leads, and administrators. Its transparent 0–100 indicator covers assignees on boards visible to the caller: 20 per overdue ticket, 15 per blocked ticket, 10 per unstarted ticket due in the next three days, and 5 per in-progress ticket above five. Completed tickets are excluded. Dates use UTC. This describes current delivery conditions, not employee performance. Scores are excluded from reports, digests, and AI inputs.

## Reports and PDF attachments

**Reports** generates sprint, monthly, or custom-period PDFs on demand (up to 366 days and 1,500 tickets). Choose a board to narrow scope. PDFs include a cover, repeating table headers, page numbers, and ticket details with descriptions excerpted to 500 characters. The private Python WeasyPrint service renders escaped structured data. Reports describe the current task snapshot matched by dates/open work; they are not a historical event ledger.

PDFs are saved in PostgreSQL with their source board IDs. Listing/downloading rechecks current project and board access, including team revocation. Admin PDF download policy applies: watermark mode personalizes each copy; disabled downloads also block attachments.

Authorized leaders can email the PDF **as an attachment**. The project lead is always included, plus selected active internal project members. All recipients must read every included board; choose a narrower board when team access differs. External report recipients are rejected. Email and the notification must be configured. Provider acceptance is not a delivery receipt. Reports exclude private calendars and risk scores.

## Work digests

**My work digest** previews/emails in-progress assignments, due/overdue work, and the recipient's own local/synced calendar commitments. Project leaders can send each selected internal member a personalized project digest. Leaders receive statuses, not other members' calendar contents. Missing calendar permission/plugin omits that section. Each recipient's board access is checked separately.

A configured LLM can summarize bounded facts. Summaries are labeled and ordinary work details remain. No AI, absent credentials, or provider failures leave the normal digest usable. Enabling AI sends those work/calendar facts to the selected provider. Risk indicators are never supplied.

## Manual until scheduled

Nothing is scheduled automatically. Personal schedules send the owner's digest; project schedules require leadership and internal recipients. Emailed reports always include the lead.

Schedules support daily/weekly/monthly cadence, local time, timezone, weekday, and month day 1–28. Profile, schedule, digest, and indexing forms share a grouped timezone dropdown with familiar names (such as South Africa Time and Eastern Time), city, and current UTC offset. **Find a timezone** filters by name, city, or identifier. Values remain IANA identifiers and schedules adjust for daylight saving. Defaults are **disabled**, monthly, 08:00 UTC. Monthly reports cover the previous calendar month; period reports cover seven previous days; sprint jobs select the latest newly completed sprint. Digests cover the scheduled date through seven days ahead.

The backend checks each minute, claims due rows in PostgreSQL, advances deadlines, and uses delivery deduplication keys. After downtime, work runs once rather than once per missed interval. The backend must be running. Unavailable providers are recorded for manual review, without indefinite automatic retries.

## APIs and extensions

See [API reference](api.md#projects-and-delivery) for clients, projects, members, work, sprints, indicators, reports, digests, and schedules. Existing project/group/schedule edits require revisions.

Plugins declare `contextTargets: ["PROJECT", "CLIENT"]` to appear in authorized contexts. Reviewed modules can add computed panels with `WorkspaceContextProvider`; ZIP pages receive context through the SDK. This never grants project or storage access. See [context extensions](plugins/project-contexts.md).
