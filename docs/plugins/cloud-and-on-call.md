# Cloud costs and on-call response

## Cloud costs

Enable `cloud-costs`. Administrators add named provider connections to projects; project members can read those projects' imported costs. Multiple accounts/subscriptions can map to one project. Connection secrets are encrypted on the server, absent from API views, and retained across application upgrades. Changing provider/scope clears the old credentials so they cannot silently be forwarded to a different account. Normal private-project RBAC still applies.

| Provider | Read path | Required setup |
| --- | --- | --- |
| AWS | Signed Cost Explorer `GetCostAndUsage`, daily `UnblendedCost`, grouped by service and filtered by linked account | 12-digit AWS account ID; scoped key/session token or server AWS credential chain; `ce:GetCostAndUsage`; Cost Explorer enabled |
| Azure | Cost Management subscription query, daily actual/pre-tax cost grouped by service | Subscription UUID, tenant/application UUIDs and client secret; Cost Management Reader or equivalent |
| GCP | Parameterized BigQuery query over the standard billing export, filtered by `project.id`; cost includes credits | Project ID, approved `project.dataset.table`, BigQuery job project, offline OAuth client/secret/refresh token; billing export read + job creation permissions |

Select a period of up to 31 days and refresh. Provider requests are bounded and do not follow arbitrary provider-supplied URLs. AWS pagination is bounded; incomplete Azure/BigQuery results are rejected with no partial window saved. A successful refresh replaces that connection's entire requested window atomically, including adjustments/disappeared rows. Totals are shown separately per currency. Cost data can lag or remain estimated; provider billing remains authoritative.

Daily refresh is **off by default**. Configure its local time/timezone to refresh the previous seven complete days. Configure and test provider permissions before enabling this. AWS Cost Explorer API and BigQuery jobs may incur provider charges; GCP queries have a 100 MiB bytes-billed cap per query. No real cloud API call is made during app startup or when merely saving a connection.

Sources: [AWS cost API](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_GetCostAndUsage.html), [Azure Cost Management query](https://learn.microsoft.com/en-us/rest/api/cost-management/query/usage?view=rest-cost-management-2025-03-01), [GCP billing export schema](https://docs.cloud.google.com/billing/docs/how-to/export-data-bigquery-tables/standard-usage).

## On-call rotations and severity

Enable `on-call`. Project leaders set a Monday-starting, seven-day rotation with timezone, primary internal member and up to five ordered backups. Overlapping rotations are rejected. All assigned people must be active project members. If no current rotation exists, the project lead is the fallback.

| Severity | Repeat while open/unacknowledged | Escalation |
| --- | --- | --- |
| SEV1 | Every minute | Adds a backup each repeat, then the project lead |
| SEV2 | Every five minutes | Adds a backup every two repeats, then the project lead |
| SEV3 | Every thirty minutes | Primary member |
| SEV4 | One page | Primary member |

These are application attempts, not delivery guarantees. Methods must be enabled and configured in **Administration → Notifications**, with an available private route to the member. Normal notification defaults and member preferences apply; on-call does not override a member's opt-out. Disabled Discord/Teams/Telegram/SMS plugins cannot be used. Acknowledgment stops repeat paging; the acknowledging member or a project leader can resolve the incident. Removed/inactive members are rechecked before delivery.

Ordinary project members may report an issue, but a leader must confirm before paging. Enable **Use the configured AI for severity suggestions** to classify report text with the existing optional LLM. Invalid/offline/disabled AI preserves the submitted severity and never prevents reporting. AI suggestions are visible, and leaders can override them. A separate opt-in allows AI-classified SEV1/SEV2 reports to page immediately; leave it off when human confirmation is desired. Incident text sent to the configured AI provider is bounded and treated as untrusted input.

## Service monitors

Create a dedicated enabled **incoming webhook** under Administration → Webhooks, then attach it to one project in Alerts & on-call. Existing bearer/HMAC authentication, timestamp/replay controls and rate limits apply before plugin dispatch. Turning off the plugin/source/endpoint stops ingestion. A monitor credential grants incident creation only for its attached project; it is not a general user login.

Configure a provider action/bridge to send normalized JSON:

```json
{
  "eventId": "stable-incident-episode-id",
  "title": "Production API unavailable",
  "description": "Health check failed in the production region.",
  "severity": "SEV1"
}
```

Use a stable event ID for retries and a new ID for a new incident episode. Azure common alert rule/description/severity fields and AWS alarm names can also populate the report, but provider delivery/authentication must still be adapted to the configured incoming endpoint. Direct AWS SNS confirmation/signature validation and Google Pub/Sub OIDC subscriptions are not implemented; use an authenticated forwarding action/bridge. Monitor events are confirmed, while user reports follow human/AI confirmation policy. Incoming “resolved” payloads do not close an incident automatically; acknowledgment/resolution remain explicit human actions.

## Chat paging versus calls

The bundled Discord, Telegram and Teams integrations deliver private chat notifications. They **do not claim to ring a telephone or create a live call**. Telegram's Bot API has no phone-call operation; Discord voice connections differ from dialing a member. Microsoft Graph calling requires a registered calling bot, application permissions and a publicly reachable validated callback service, beyond the existing chat integration. See [Telegram Bot API](https://core.telegram.org/bots/api), [Discord voice connections](https://docs.discord.com/developers/topics/voice-connections), and [Microsoft Graph calls](https://learn.microsoft.com/en-us/graph/api/application-post-calls?view=graph-rest-1.0).

Reviewed service plugins can implement `OnCallCallingProvider` to add a real calling API. Its `pluginId` must be enabled; it is invoked only for SEV1/SEV2 after normal project/recipient checks. An uploaded backend plugin can also contribute a notification method through the existing messaging SDK, for example an SMS provider, without core hardcoding. The calling capability API/page lists registered providers and clearly reports when none is installed.
