# Integration plugins

Discord, Telegram, Microsoft Teams, WhatsApp Business, SharePoint, and OneDrive are separate compiled plugins with independent activation and encrypted configuration. They start disabled, are administrator-only, and register no workspace pages or search providers. Configure them in **Administration → Plugins → Settings → Configuration**, then enable under **General**. Discord/Telegram/Teams also have **People & chats** settings for internal identity links and explicit approved-destination posts.

Saving configuration performs no network call. Notifications require both an enabled method and an allowed rule. Linking an identity does not send a message or enroll MFA. Shared posts are explicit admin actions; private notifications use mapped member destinations without shared fallback. Workflow email blocks remain email delivery.

## Discord

Set **Bot token** and approved channel IDs. In **People & chats**, link each member's numeric Discord user ID and optional display name. The bot resolves a direct conversation for private delivery; Discord privacy settings can reject it. Shared posts use only approved channels. Plain-text messages are bounded to 2,000 characters with remote mention parsing disabled. This is a bot/API integration, not Discord OAuth login. See [Discord's message API](https://github.com/discord/discord-api-docs/blob/main/developers/resources/message.mdx).

## Telegram

Create a bot with BotFather and set **Bot token**. Shared posting supports an optional default chat and approved chat IDs. For private notifications the member must first start the bot; link their positive numeric user/chat ID. Negative group IDs are rejected for private mappings. Messages use plain text without markup parsing. See [Telegram Bot API](https://core.telegram.org/bots/api#sendmessage) and [bot setup](https://core.telegram.org/bots).

```http
POST /api/plugins/telegram/send
Content-Type: application/json

{"text":"A quote is ready for review."}
```

## Microsoft Teams

Choose **WEBHOOK** for explicit shared posts via Teams Workflows or **GRAPH** for approved chats/private member delivery. Webhook mode supports signed HTTPS Power Platform (`*.api.powerplatform.com`) and Azure Logic Apps (`*.logic.azure.com`) URLs. Maintain workflow owners/authentication in Teams. See [Workflows setup](https://learn.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/how-to/add-incoming-webhook).

Graph mode uses an externally acquired delegated access token saved as a secret, plus approved existing chat IDs. Link members by Entra UUID and existing one-to-one chat ID. Before private delivery the app verifies chat type and mapped membership; group chats are refused. Grant appropriate read/member/message-send permissions and refresh the token when it expires. Automated OAuth consent/refresh and chat creation are not implemented. See [Graph chat messages](https://learn.microsoft.com/en-us/graph/api/chat-post-messages?view=graph-rest-1.0).

```http
POST /api/plugins/teams/send
Content-Type: application/json

{"text":"A document needs approval."}
```

The signed URL is encrypted/redacted. Provider acceptance does not confirm workflow completion or recipient delivery. Webhook mode is unavailable for private member notifications.

## Member links and shared posts

For `discord`, `telegram`, or `teams`:

```text
GET    /api/admin/plugins/{id}/members
GET    /api/admin/plugins/{id}/member-options
PUT    /api/admin/plugins/{id}/members/{userId}
DELETE /api/admin/plugins/{id}/members/{userId}?revision=N
GET    /api/plugins/{id}/destinations
POST   /api/plugins/{id}/post
```

Mappings contain `remoteUserId`, optional `remoteName`, `destinationId` (Teams; Telegram defaults to its user ID), and `revision` (`-1` for new). Stale edits and duplicate remote identities are rejected. Only active internal accounts are eligible. Shared posts use `{"destination":"approved-id","text":"Message"}`; other destinations are refused. Legacy Telegram/Teams `/send` remains an explicit shared-send compatibility API.

## WhatsApp Business

WhatsApp now lives in its own plugin instead of a reserved core notification enum. Configure an access token, sender phone-number ID, supported Meta Graph version, approved template name, and matching language code. Use your Meta app's supported API version; no version is silently selected. The first adapter supports text parameters in the template body, in order. It does not implement free-text sessions, media/header/button parameters, inbound callbacks, or delivery receipts. See [Meta's template request reference](https://www.postman.com/meta/whatsapp-business-platform/request/lwtlz1k/send-message-template-interactive) and [official sample payloads](https://github.com/fbsamples/whatsapp-api-examples/blob/main/send-messages-flight-app-python/message_helper.py).

```http
POST /api/plugins/whatsapp/send
Content-Type: application/json

{"recipient":"27821234567","templateParameters":["Quote 42"]}
```

The recipient is an international number without `+`. The number/order of parameters must match the approved template. Set `text` empty for this adapter. Provider acceptance does not confirm delivery.

## SharePoint and OneDrive

These are separate plugins using the shared Microsoft Graph client. Each has its own tenant ID, client ID, encrypted client secret, drive ID, and optional root folder item ID. The OneDrive adapter targets **OneDrive for Business**; personal Microsoft account delegated sign-in is not implemented. This version targets Microsoft's global cloud.

Register a Microsoft Entra application and grant the required application permissions/resource access separately. Prefer an appropriate Selected permission and explicit read grant to the intended resource; the app does not grant permissions or broaden consent itself. See [client credentials](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow), [Selected permissions](https://learn.microsoft.com/en-us/graph/permissions-selected-overview), and [list folder contents](https://learn.microsoft.com/en-us/graph/api/driveitem-list-children?view=graph-rest-1.0).

```http
GET /api/plugins/sharepoint/files
GET /api/plugins/onedrive/files
```

These calls list up to 100 immediate children of the configured root (or drive root if blank). Results contain ID, name, size, folder flag, and `hasMore`. They do not return access tokens, preauthenticated download URLs, or provider pagination URLs. Browsing arbitrary folders, pagination beyond that first page, uploads/downloads, sync, storage-browser mounting, and vector indexing are future extensions. Credentials and remote metadata are excluded from search.

## Extending notifications

Plugins declare `NOTIFICATIONS` and register `NotificationChannelAdapter` beans. Enable the plugin/method in **Administration > Notifications**, then allow its notification rules. Discord/Telegram/Teams use `MEMBER` recipients: an internal UUID resolves to a private mapping. Missing links/providers return unavailable without shared fallback. `NotificationPublisher` selects member preferences; shared posts use the separate API. See [notification contracts](notifications.md). WhatsApp uses phone/template delivery; SMS can be added by a provider plugin. Existing core email callers stay compatible.

HTTP send/file APIs require an active app administrator and an enabled plugin. Sending is limited to 10 calls per minute per admin/plugin; file listing to 20. Transport uses fixed provider hosts or validated Microsoft workflow hosts, HTTPS, public DNS targets, no redirects or automatic retries, bounded responses, and timeouts. Errors omit request URLs, tokens, provider bodies, and credentials. Automated tests mock providers; deployment checks save disposable local credentials only and do not send external messages.

Additional [source-control](source-control.md) and [calendar](calendar.md) plugins use the same encrypted configuration and bounded transport. Their administrator-configured HTTPS Git/CalDAV endpoints support self-hosted providers; private network destinations require explicit deployment opt-in. [Composition](composition.md) allows optional shared Microsoft credentials and other declared connections without enabling plugins automatically.
