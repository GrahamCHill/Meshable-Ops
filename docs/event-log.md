# Event log

Open **Administration → Operations → Event log** (`/admin/events`). The log belongs to the admin portal rather than the main workspace menu and includes back links to Operations and Administration. The old `/events` bookmark redirects authorized administrators to the new page.

The history panel scrolls independently and loads 50 older events at a time when its end approaches. **Load older events** provides the same action for keyboard use or browsers without automatic observation. Continue until the beginning-of-history message appears; the viewer has no overall 20/50-event display cap. Expand **Details** to inspect an event payload. If an older page fails, loaded entries remain available and a retry action appears.

**Refresh newest events** returns to the top and starts a fresh history view. Background arrivals do not reset the scroll position. Newest-first cursor pagination uses timestamp plus record ID, so events arriving during browsing do not shift older pages as offset pagination would. Exact timestamp ties and legacy string IDs retain deterministic ordering. The loaded count describes the entries currently fetched, not a database-wide total.

Only `SUPER_ADMIN` and `ADMIN` accounts with the **Event log** feature can read the log. Super admins can cap delegated administrators' access in Access controls. The existing `events` feature ID is retained for administrator policy compatibility, but is now marked admin-only; old ordinary-member grants do not expose the log. Access-policy editing removes obsolete role grants outside each role's allowed features. Direct API calls receive the same role and feature checks.

## API

`GET /api/admin/events?size=50&cursor=<nextCursor>` returns `{content,nextCursor}`. Omit `cursor` initially; use the returned cursor for the next older slice. A null cursor marks the end. Size is bounded to 1–100 and malformed cursors return `400`. Cursors carry position information, not authorization. All requests require current admin permissions.

The existing `GET /api/events?page=0&size=20` response remains available to authorized administrators for integrations, with a nonnegative page and size of 1–100. It is no longer a member-readable endpoint.

This change reads the existing event collection without changing stored events, collection schema or indexes. Events and audit records remain excluded from search and AI. The separate **Audit & backup** page remains available for administrative activity and exports.
