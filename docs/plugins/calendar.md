# Calendar and connected calendars

Month cells and date buttons create an all-day event for that day. Day/week views use a scrollable hourly grid: click a time slot to create a timed event, or the all-day area for a date-only event. Overlapping appointments use separate lanes. Clicking any event opens an inspector; **Edit event** is available for local events when the caller has write permission. Provider overlays and Kanban deadlines are read only here and link to their source. Saved personal imports are local calendar records and retain their existing two-way sync behavior. This interaction does not add recurrence editing, drag-to-reschedule, invitations, or a Google OAuth wizard.

The Events submenu has a separate large expand/collapse button in the sidebar. Personal connection/sync controls are in the account dropdown’s Calendar connections page. Agenda remains a scrollable list on the calendar page. Days/weeks start in the morning, with full 24-hour slots available by scrolling. Displayed times still use the browser timezone, including all-day exclusive end dates.

Enable **Calendar** (`calendar`) in **Administration → Plugins** to add its workspace page and Events submenu. It works without an external account. All plugins start disabled, with no seeded events. Members need plugin and table access. Ordinary local events retain the existing owner/admin policy; imported personal events and disconnected personal copies remain private to their owner, including against other app administrators through these APIs. The Data tab clears calendar events, sync relationships, and personal connections/credentials; shared administrator provider settings and Kanban cards are retained. Provider copies are not deleted by clear-data.

Choose **Day**, **Week**, or **Month**, with previous/next navigation and Today. Weeks start on Monday. Day and week views show each day's events and times; all three views include a scrollable agenda. Add a title, description, start/end, or all-day dates. Timed input/display uses the browser's time zone and is stored as UTC instants. The browser sends its IANA `zone` for date boundaries, so all-day events and card deadlines stay on the intended date. All-day dates use exclusive end dates (a one-day event on October 24 ends October 25). Local events support optimistic revisions; stale edits/deletions return `409`. Event durations are limited to 366 days; queries to 93 days. The view returns at most 200 local events and 200 card deadlines per window, with an explicit truncation flag. Export refuses incomplete local windows; use Day/Week for a smaller range.

When Kanban is active for the same role and its board table is readable, saved card **Due date** values appear as virtual all-day events with links back to the board. Only the owner/admin's visible boards contribute. Editing a deadline happens on its card; it is not copied into the calendar table. Disabling Kanban hides these virtual events and leaves local events available.

**Export local events (.ics)** downloads local events and visible board deadlines for the displayed window. This is a manual authenticated export, not a public subscription URL. No credentials or private calendar feed URLs are returned.

## Microsoft Calendar (`calendar-microsoft`)

An administrator enables Calendar and this separate provider. The shared connection in admin uses a tenant ID, application/client ID, encrypted client secret, and selected mailbox address/user ID; optionally specify a calendar ID. This uses Microsoft Graph application/client-credential access to a work/school mailbox in Microsoft's global cloud. Personal member connections use a separate delegated flow described below. Group calendars are outside these adapters.

Grant the appropriate calendar read permission (`Calendars.Read`) and, only if publishing is required, `Calendars.ReadWrite`, with tenant consent and mailbox restrictions configured in Microsoft. File permissions do not grant calendar access. See [calendarView](https://learn.microsoft.com/en-us/graph/api/calendar-list-calendarview?view=graph-rest-1.0), [create event](https://learn.microsoft.com/en-us/graph/api/calendar-post-events?view=graph-rest-1.0), and [application access control](https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac).

To reuse tenant/client credentials, set **Reuse connection from plugin** to `onedrive` or `sharepoint`. That exact plugin must remain enabled and its credentials complete. Blank uses this calendar plugin's own credentials. Reuse never enables a plugin or grants provider permissions. Provider settings remain administrator-only.

## Google Calendar (`calendar-google`)

Configure an OAuth client ID, encrypted client secret, encrypted offline refresh token, and selected calendar ID (`primary` or an accessible calendar ID). Obtain the offline consent/token using your own Google OAuth application. This version consumes an existing refresh grant; it does not implement a browser OAuth authorization wizard. Use calendar read scope for browsing and `calendar.events` (or a suitable narrower write scope) when publishing. See [OAuth server applications](https://developers.google.com/identity/protocols/oauth2/web-server), [event listing](https://developers.google.com/workspace/calendar/api/v3/reference/events/list), and [event insertion](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert).

## CalDAV (`calendar-caldav`)

Configure the complete selected HTTPS calendar collection URL, account username, and encrypted app password. This provides a standards-based option for services such as iCloud, Nextcloud, and Fastmail when their account/server supports CalDAV. Use the provider's resolved collection URL; account discovery and redirects are not followed. App-specific passwords may be required. Private self-hosted targets need the explicit deployment opt-in described in [source control](source-control.md).

The adapter sends a bounded CalDAV calendar-query REPORT requesting server-expanded recurrences. It supports date-only, UTC, and recognized IANA TZID event times. Unexpanded recurrence rules, floating times, unsupported/custom time zones, and malformed events are omitted with a notice. Secure XML parsing disables DTDs and external entities. There is no local recurrence engine or calendar discovery. See [RFC 4791](https://www.rfc-editor.org/info/rfc4791/) and [iCalendar RFC 5545](https://www.rfc-editor.org/info/rfc5545/).

## Connected use and publishing

An enabled provider additionally requires the active Calendar capability. Its settings menu's **Connections** tab explains missing support. Shared external connections remain administrator-only; they are read on demand by choosing **Show a shared external calendar** or Refresh. At most 100 expanded events are returned per provider/window; additional/unsupported events are flagged rather than following untrusted provider URLs. Provider failures do not prevent local events from loading. Administrators may separately enable member access to a provider for personal sync; this grants no access to its shared credentials/mailbox.

Open a saved local event and use **Publish saved event** to create a separate external copy. Publishing never happens on local save, does not add attendees, and does not synchronize later edits/deletions or publish virtual board deadlines. Google uses a stable event ID and CalDAV a stable resource/UID with conditional creation; existing copies are left unchanged. Microsoft uses Graph's `transactionId` duplicate-request protection; its guarantees follow the provider. This is manual publication, not full bidirectional sync. Repeated/long-delayed Microsoft publication should be checked in the provider.

Provider credentials use the existing encrypted plugin configuration and are never returned in API responses. Remote event content is read on demand and excluded from search; calendar events are also excluded. Clearing local events does not delete provider events. No external account is required for local calendar functionality.

## Personal two-way sync for internal members

An internal member is an active signed-in `ADMIN` or `USER` account. Anonymous visitors and recipients of external file-share links cannot use personal sync. Enable Calendar for members, grant read/write on its three tables, and enable the desired provider for members in its General settings. Providers default to administrators only. Each member then opens **Calendar → Your calendar sync** and configures **one personal connection**. Settings endpoints derive ownership from the current account; there is no user selector or administrator override to view another member's connection.

Personal credentials are independent of administrator shared connections. The entire credential JSON is AES-GCM encrypted in `plugin_calendar_connections`, with the same wrapping-key/rotation requirements as other stored secrets. Responses contain non-secret fields and configured-secret names only. Blank secret values retain existing values; explicit removal clears them. Configuration revisions reject stale edits while background sync/status changes do not invalidate the settings form.

| Provider | Personal connection credentials |
| --- | --- |
| Google | OAuth client ID/secret, the member's offline refresh token with calendar read/write scope, selected calendar ID (for example `primary`) |
| Microsoft | OAuth client UUID, optional confidential-client secret, the member's offline refresh token, tenant UUID or `common`/`consumers`/`organizations`, optional calendar ID. Uses delegated `/me` access with `Calendars.ReadWrite` and `offline_access`, supporting work/school or personal accounts permitted by the registered OAuth app |
| CalDAV | The member's selected HTTPS collection URL, encrypted username and app password with read/write access |

Obtain OAuth grants through your registered provider application. There is no built-in browser OAuth authorization wizard in this version. Saving a connection makes no provider call. Explicitly checking **Enable automatic two-way sync for my events** opts into reading and modifying that member's selected external calendar. Sync now is available manually (five requests/minute/account). A scheduler checks due connections every minute, normally syncing every five minutes; it rechecks account activation, current role, base/table access, and provider capability before outbound work. Paused connections make no network requests. Disabling a plugin, revoking member access, or deactivating the member blocks sync.

Each cycle pulls the last 30 and next 60 days and publishes included local events in that window, plus pending changes/deletions to known linked events. Imported provider events are private owned local records, not public calendar feeds or copies on another member's calendar. Board deadlines are virtual and never published automatically. Only events owned by the connected member are pushed, even when the member is an administrator. Use an event's **Include in my personal calendar sync** checkbox to pause synchronization for that event while retaining both copies.

Updates and deletions propagate both ways. Local deletions retain internal tombstones so they can reach the provider. Time-window omissions are confirmed by a direct GET before interpreting an event as deleted. Conditional provider revisions (`If-Match`/ETags) protect against concurrent provider writes; Google/CalDAV use stable creation identities, and Microsoft uses a transaction ID. CalDAV updates preserve existing UIDs, alarms, locations, time zones, and other unmanaged properties. If both sides changed, provider content wins the linked record and local content is preserved as a **conflict copy** excluded from further sync. Private imported records stay private after disconnecting.

This first sync adapter handles single personal appointments and all-day events. Recurring events, meeting invitations/attendees, custom unsupported time zones, and unsupported calendar object types remain provider-managed and are flagged/omitted. This avoids changing meeting invitations or replacing recurring resources with standalone events. Shared read-only calendar overlays can still display expanded recurring events. Sync is bounded to the provider's first 100 resources, 20 event HTTP calls per cycle (plus OAuth), 500 local candidates, and 1,000 relationships per connection. Additional work retries later; page omissions are flagged rather than silently treated as deletions. These limits are not a full historical-calendar migration service.

**Pause sync** retains credentials and records. **Disconnect calendar** removes that member's encrypted connection and relationships, retaining external events and detached private local-only imported copies. Reconnecting can import retained provider events again. Provider errors retain local data and a sanitized status, then retry in five minutes. Credentials, event content, and personal sync records are excluded from search and metadata exports; include them in PostgreSQL backups together with the wrapping key stored separately.

`plugin_calendar_sync_links` is the child table; `plugin_calendar_events` and `plugin_calendar_connections` are parents. All three are declared by Calendar and clear in that order. Clear-data can remove every member's connection, so pause sync and back up before using the administrator action. Shared admin configuration remains in the separate host-managed configuration table.

## API and extension points

| Route under `/api/plugins/calendar` | Behavior |
| --- | --- |
| `GET /events?from=<ISO instant>&until=<ISO instant>&zone=<IANA zone>` | Local events plus permitted Kanban deadlines; zone defaults to UTC |
| `POST /events` | Create `{title,description,start,end,allDay,revision}` using ISO instants |
| `PUT /events/{id}` | Update with current revision |
| `DELETE /events/{id}?revision=<n>` | Delete an owned local event |
| `GET /export?from=...&until=...` | Authenticated iCalendar attachment |
| `GET /providers/{providerId}/events?from=...&until=...` | Selected provider's events; 20 reads/minute/user/provider |
| `POST /events/{id}/publish/{providerId}` | Explicit local-event publication; 10/minute/user/provider |
| `GET /personal` | Current member's redacted connection, eligible provider schemas, and write access |
| `PUT /personal` | `{providerId,values,clearSecrets,enabled,revision}`; save own encrypted connection only |
| `DELETE /personal?revision=<n>` | Disconnect own connection; retain local/provider events |
| `POST /personal/sync` | Run own enabled two-way sync; no arbitrary owner or connection ID |

Implement `CalendarProvider` as a reviewed Spring bean to add a provider; declare `calendar.provider` and require `calendar.events` in its manifest. `CalendarConnections` checks host roles/dependencies and local table access. New adapters need credential validation, bounded outbound requests, sanitized errors, format/time-zone tests, and an explicit publication policy. See [Connecting plugins](composition.md).

Month view spans complete Monday–Sunday weeks, including clickable adjacent-month dates and their events. Previous/Next navigates by the selected view, Today returns to the current date, and Go to month jumps directly to a past or future month. The fetched event window includes these adjacent dates, including year boundaries.

## Agenda and personal connections

Open **your name → Calendar connections** (or **User settings → Calendar connections**) to configure personal sync. It appears only while Calendar is enabled and permitted; personal connection/table policies still apply. Shared provider credentials stay in admin settings.

Agenda is a keyboard-focusable, scrollable list for the visible window. Each event shows notes, linked documents, its origin calendar, and available participant names/emails/responses. Clicking the event opens its inspector. Local event editing supports up to 20 labelled document references and 100 participants. Choose an accessible generated document or paste an HTTPS document URL or a workspace Storage/Documents link. References retain their destination permissions and do not upload/copy documents or grant access. Generated-document links resolve within the accessible library.

Google and Microsoft shared overlays expose attendee metadata when supplied by the provider; Google attachment URLs and supported CalDAV URI attachments/attendees appear when available. Unsupported/binary attachments and unsafe URLs are omitted. A provider may omit fields, in which case the UI says no information is available. The origin identifies the provider (or board/local calendar); it is not a fetched remote calendar display name.

Local participant/document fields are app metadata, not invitation sending or a permissions system. They are not exported as ICS invitations or sent through provider updates. Personal sync continues to handle appointments and omit recurring/invited meetings; view those through the shared overlay or provider. Notes continue to use the existing description field and its sync behavior.

`CalendarEventRequest` additionally accepts `documents: [{label, url}]` and `participants: [{name, email, response}]`. Calendar views return these plus `calendarName`. Omitted arrays default empty on a replacement event save; clients should send the existing arrays when editing. Optimistic revisions and existing owner/admin/private-event checks apply to metadata too.

Calendar opens without an Events submenu. Use the main calendar and agenda to inspect events.
