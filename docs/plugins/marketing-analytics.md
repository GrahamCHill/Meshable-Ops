# Marketing and website analytics connectors

Five independently enabled bundled plugins ship at **1.0.0**:

There are no dependencies between these five connectors. Each has its own enable switch, credentials, resource assignments, permissions, table, query cache and refresh errors. Missing Google configuration never blocks Meta, missing Meta configuration never blocks Google, and Cloudflare works independently of both.

| Plugin | Resource and optional scope | Snapshot metrics |
| --- | --- | --- |
| `google-ads` | Google Ads customer; exact campaign ID | Impressions, clicks, spend, CTR, CPC, conversions and conversion value |
| `meta-ads` | Meta ad account; exact campaign ID | Impressions, clicks, link clicks, period reach, spend, CTR and CPC |
| `google-analytics` | GA4 property; exact session campaign name or page path | Active users, sessions, page/screen views, engaged sessions, key events, revenue and engagement rate |
| `meta-page-analytics` | Facebook Page | Returned daily media views and post engagements, with date coverage notes |
| `cloudflare-analytics` | Cloudflare zone; exact request path | End-user HTTP requests, visits and bytes transferred, with adaptive sampling notes |

Enable a connector under Plugins and complete its **Analytics** settings. Open its page to select a project or client, or use the extension on that project's/client's detail page. Choose an approved resource, optional campaign/page filter and a descriptive link name. Linking stores the selection; **Refresh metrics** loads the first snapshot. Unlinking removes the local link and snapshot without changing the provider resource.

## Credentials and resource assignments

Credentials use the existing encrypted, write-only plugin configuration. Configure the installation's encryption key before saving secrets. Google access tokens are refreshed server-side; resolved Meta Page tokens stay in memory for the current request. Tokens never appear in link responses, metric payloads, browser URLs or provider error messages.

List approved resource IDs separated by commas. Google Ads customer IDs and manager IDs use ten digits without hyphens; Meta IDs and GA4 property IDs use digits; Cloudflare zone IDs use 32 hexadecimal characters.

Assign each approved resource to its permitted local context in **Resource assignments**. Separate assignments with semicolons:

```text
1234567890=CLIENT:11111111-1111-4111-8111-111111111111;
9876543210=PROJECT:22222222-2222-4222-8222-222222222222
```

A client assignment also permits links on that client's projects. A project assignment permits only that project. Repeat an ID to assign multiple contexts. Use `RESOURCE_ID=GLOBAL` only when every authorized local client owner/project manager should be able to link that resource. Unassigned resources are unavailable. Removing approval or changing an assignment hides cached links outside the new scope as well as preventing refreshes.

Provider permissions should independently restrict the connection to the intended accounts, Pages, properties or zones. These connectors read analytics; they contain no ad publishing, budget editing, Page publishing or Cloudflare configuration endpoints.

| Provider | Setup |
| --- | --- |
| Google Ads | Google OAuth client ID, client secret, offline refresh token with `https://www.googleapis.com/auth/adwords`, Ads developer token, and optionally a manager `login-customer-id`. The authenticated account must have reporting access to approved customers. REST defaults to `v26`; administrators can pin another supported major version. |
| Google Analytics | Google OAuth client ID, client secret and offline refresh token with `https://www.googleapis.com/auth/analytics.readonly`. Enable the Analytics Data API and grant the authenticated user access to each approved GA4 property. |
| Meta Ads | A token with `ads_read` for the approved ad accounts. Required app review/access levels depend on the Meta app and accounts. Graph defaults to `v26.0`. |
| Meta Page Analytics | A user token with Page access, `pages_show_list`, `pages_read_engagement` and `read_insights`; leave **Resolve each Page token** enabled to support multiple approved Pages. Alternatively supply one Page token and disable resolution. The user needs the Page's analytics task; Meta may withhold insights for ineligible Pages or metrics. |
| Cloudflare | A scoped API token with **Analytics: Read** for approved zones. The connector uses GraphQL `httpRequestsAdaptiveGroups`. Query windows and retention depend on the zone's plan/dataset; shorten the selected date range if necessary. |

## Reporting behavior

Refreshes are manual, limited to 30 per user/connector/hour, and use up to 31 completed calendar days. Cloudflare additionally requires the start date within the last 30 days. Providers may impose tighter windows, quotas or reporting delays. A failed refresh leaves the last snapshot intact. The UI always labels the saved snapshot's dates independently of the dates chosen for the next refresh, and flags snapshots older than 24 hours.

Google and Meta reporting uses the provider's account/property timezone and attribution. Cloudflare uses UTC. GA4 campaign filters match **session campaign names**, case sensitively; page filters match exact paths without query strings/fragments. Meta Page totals sum only returned daily counts, with coverage counts; missing metrics display **Unavailable** rather than zero. Meta reach and GA4 users are provider aggregates and are never summed across days or links. Money retains its provider currency; overlapping scopes, currencies and providers are not combined. Cloudflare visits represent entries and are not unique visitors.

Only numeric aggregate fields and explicit reporting notes are persisted. No visitor identifiers, audience records, raw provider responses or arbitrary remote URLs are imported. Requests use fixed HTTPS origins, encoded filters/GraphQL variables, bounded responses and the existing DNS/redirect protections. Unexpected pagination/aggregate shapes fail rather than saving incomplete totals.

## Exporting insights

**Export CSV** and **Export PDF** download all saved snapshots for the current connector and client/project. Each linked resource also has individual CSV/PDF export buttons. This supports sharing web traffic, campaign traffic, ad performance, Page engagement and Cloudflare traffic insights without refreshing a provider. Google and Meta exports remain independent.

Exports include the source, resource ID, exact campaign/page selector, snapshot dates, refresh time, metric values/units/currency and reporting limitations. Separate scopes are retained rather than summed together. CSV contains one row per metric, UTF-8 encoding, quoted fields, an explicit availability column and spreadsheet formula protection. PDF uses the internal WeasyPrint worker and supports up to 30 snapshots per download; use CSV or an individual link for larger sets. Missing snapshots must be refreshed before export.

Exports use the same connector/table and client/project read permissions as the screen. Access, resource assignments, snapshot revisions and document download policy are checked again before delivery. Disabled document downloads block export; PDF exports receive the configured watermark when required. Export endpoints have their own rate limit and return `no-store` attachments. A PDF renderer failure does not block CSV exports or other connectors.

Project members can read project links subject to connector/table access. Project managers, leads and administrators can link/refresh/unlink. Client links use client visibility; changes require the client owner or an administrator. Client links are shared with client readers and do not automatically aggregate private links from other projects. Every read/write rechecks context and plugin/table access. Optimistic revisions protect concurrent refreshes and unlink operations.

## API

The following scoped routes apply separately to each of the five plugin IDs:

```text
GET    /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links
GET    /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links/options
POST   /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links
POST   /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links/{id}/sync
DELETE /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links/{id}?revision=N
GET    /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links/export/csv?linkId=OPTIONAL_UUID
GET    /api/plugins/{plugin}/contexts/{PROJECT|CLIENT}/{contextId}/links/export/pdf?linkId=OPTIONAL_UUID
```

Link body: `{ "label": "Launch", "selection": { "resourceId": "1234567890", "filter": "CAMPAIGN", "selector": "456" } }`. Filters are `ALL`, `CAMPAIGN` or `PAGE` according to connector. Refresh body: `{ "revision": 0, "from": "2026-10-01", "to": "2026-10-24" }`. Options contain only context-approved IDs, supported filters and a readiness flag.

Each connector owns one indexed table, `plugin_{id_with_underscores}_links`, containing context kind/ID, label, selection/snapshot JSON, update time and revision. Hibernate creates the tables with the other bundled plugin entities. Disabling/removing a plugin uses the existing lifecycle and table-retention controls.

## Provider references

- [Google Ads reporting and setup](https://developers.google.com/google-ads/api/docs/get-started/make-first-call)
- [Google Ads versions and sunset dates](https://developers.google.com/google-ads/api/docs/sunset-dates)
- [Google Analytics core reporting](https://developers.google.com/analytics/devguides/reporting/data/v1/basics)
- [GA4 dimensions and metrics](https://developers.google.com/analytics/devguides/reporting/data/v1/api-schema)
- [Google offline token refresh](https://developers.google.com/identity/protocols/oauth2/web-server#offline)
- [Meta Ads insights](https://developers.facebook.com/docs/marketing-api/insights/)
- [Meta Page insights](https://developers.facebook.com/docs/graph-api/reference/page/insights/)
- [Cloudflare analytics authentication](https://developers.cloudflare.com/analytics/graphql-api/getting-started/authentication/)
- [Cloudflare adaptive HTTP analytics](https://developers.cloudflare.com/analytics/graphql-api/migration-guides/graphql-api-analytics/)
- [Cloudflare dataset limits](https://developers.cloudflare.com/analytics/graphql-api/limits/)

Validation uses mocked provider responses and an isolated sample-data UI; production credentials, app approval and live provider access are required before a real account can refresh successfully.
