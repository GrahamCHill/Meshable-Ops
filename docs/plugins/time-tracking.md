# Time tracking, ActivityWatch and TimeCamp

Enable `timesheets` for the shared ledger. Administrators separately enable **Allow internal time tracking** before members can use its timer or manual form. `timecamp` and `activitywatch` require the ledger but do not require internal collection to be enabled.

## Ownership and project visibility

Unallocated ActivityWatch suggestions belong only to their importing member. Imports retain application names, timestamps, durations, bucket/event identifiers; **window titles, URLs, filenames and keystrokes are discarded**. Export a selected ActivityWatch bucket or complete JSON export and upload up to 2 MB / 2000 completed events. AFK buckets are excluded. No connection to the member's localhost ActivityWatch server is required.

Allocate a suggestion to a project and supply a work description. The resulting entry is a draft in the shared timesheet. Its hours, time interval and chosen description are visible to the project's managers/team leads, administrators, and the client's assigned Business Manager **who is also a project member**. Private unallocated suggestions do not become visible to those people. A department label or global Business Manager role alone does not grant access to unrelated projects.

Every member with project and Timesheets access can see aggregate project hours, including draft/submitted/approved work from **all three sources**. Approved hours are shown separately; rejected entries are excluded. The project overview includes a Project hours panel. Totals never reveal another member's individual activity or hourly billing rate to ordinary members.

## Entry lifecycle and reconciliation

1. Record completed manual work, stop a running timer, allocate an ActivityWatch event, or import TimeCamp data.
2. Review the draft and submit it.
3. A project leader/assigned Business Manager approves or rejects it. Members cannot approve their own work; administrators can.
4. Approval snapshots the enabled project member rate (or default billing role), hourly rate and currency. An enabled rate is required for billable approvals when Billing items & rates is enabled. Only approved, billable entries can become invoice lines.
5. Invoice generation reserves entries. Reserved work cannot be edited, rejected or removed behind an invoice.

At most one internal timer runs per member. Entries must be completed intervals of at most 24 hours. Stop timers before that limit; longer spans must be recorded as separate completed intervals. Submitted, approved, imported and invoiced entries cannot be silently overwritten. Reconciliation resets an unbilled imported entry to draft and clears approval/rate snapshots: administrators reconcile TimeCamp; members can reconcile their own allocated ActivityWatch entries. Internal draft edits remain owner-controlled.

The ledger rejects overlapping non-rejected intervals across sources for the same user, including repeat imports. Imported IDs are stable; replaying unchanged data creates no new time. Changed provider entries are preserved and reported for reconciliation instead of replacing approved or invoiced work. Conservative overlap detection uses the whole start/end interval, even if a provider's tracked duration is smaller.

Date-range queries and billing allocation use the entry's **UTC start date**. Time inputs display the member's local time. A cross-midnight entry belongs to its start date; split it when a different reporting allocation is needed. API ranges have inclusive end dates; timestamps require an explicit timezone.

## TimeCamp setup

Administrators store an encrypted API token, select the provider timezone, and explicitly map each **TimeCamp user ID + task ID** to one active internal member and project. Unknown mappings are skipped; imported users must retain project access. The integration reads the current v3 `POST /v3/time-entries/list` endpoint, with bounded daily requests and no provider writes.

Configure an optional daily time/timezone in plugin settings. It is **off by default**. Each daily run revisits the previous seven complete provider-local days to find newly recorded work. Use the manual date-range import for older corrections (up to 31 days per request). The provider token determines the remote records it can read; use a dedicated least-privilege account. [TimeCamp API](https://developer.timecamp.com/) · [ActivityWatch exports](https://docs.activitywatch.net/en/latest/api/rest.html#export-and-import).

Developer billing roles such as Senior developer / Junior developer are pricing assignments in `billing-items`; they do not grant application or project permissions. Configure the going hourly rate, enabled switch and desired ISO currency; project-specific member rates are assigned in the project Billing tab. Rates are not automatically converted. Existing approvals retain their original snapshot; reject/reapprove unbilled work when intentionally applying a corrected rate.
