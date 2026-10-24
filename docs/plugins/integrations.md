# Integration plugins

Telegram, Microsoft Teams, WhatsApp Business, SharePoint, and OneDrive are five separate compiled plugins with independent activation and encrypted configuration. They start disabled, are administrator-only, and register no workspace pages or search providers. Configure them in **Administration → Plugins → Settings → Configuration**, then enable under **General**.

Saving configuration performs no network call. No account messages, scheduled broadcasts, MFA-channel changes, or file synchronization are triggered automatically. The initial APIs are extension points for reviewed server-side code and future workflow blocks. The existing workflow editor continues to use its email block.

## Telegram

Create a bot with BotFather, give it access to the destination chat, then set **Bot token** and **Destination chat ID**. Sending always uses the saved chat, with plain text and no markup parser. The bot token is stored as a credential. See [Telegram Bot API](https://core.telegram.org/bots/api#sendmessage) and [bot setup](https://core.telegram.org/bots).

```http
POST /api/plugins/telegram/send
Content-Type: application/json

{"text":"A quote is ready for review."}
```

## Microsoft Teams

Create a Teams Workflows webhook that posts an Adaptive Card to your chosen channel/chat. Set its full URL as **Workflows webhook URL**. This adapter supports HTTPS Power Platform (`*.api.powerplatform.com`) and Azure Logic Apps (`*.logic.azure.com`) endpoints with URL-based webhook authorization. Tenant-authenticated triggers, Teams bots, Graph chat messaging, and retired connector URLs are outside this adapter. Choose a workflow authentication mode compatible with server-to-server webhook requests and maintain its owners in Teams. See [Microsoft's Workflows setup](https://learn.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/how-to/add-incoming-webhook).

```http
POST /api/plugins/teams/send
Content-Type: application/json

{"text":"A document needs approval."}
```

The entire URL, including its signature/query, is encrypted and redacted. A successful HTTP response means the workflow accepted the request; later workflow execution and delivery are not confirmed.

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

Plugins register `NotificationChannelAdapter` beans with unique channel strings. `NotificationService.available("telegram")` and `send("telegram", ChannelMessage(text = "…"))` select an enabled adapter. Teams uses `teams`; WhatsApp uses `whatsapp` with recipient/template parameters. Unknown or disabled channels return an unavailable/disabled result. Existing mail callers continue using `NotificationChannel.EMAIL`; SMS remains reserved. Adding another plugin channel no longer requires expanding a core enum.

HTTP send/file APIs require an active app administrator and an enabled plugin. Sending is limited to 10 calls per minute per admin/plugin; file listing to 20. Transport uses fixed provider hosts or validated Microsoft workflow hosts, HTTPS, public DNS targets, no redirects or automatic retries, bounded responses, and timeouts. Errors omit request URLs, tokens, provider bodies, and credentials. Automated tests mock providers; deployment checks save disposable local credentials only and do not send external messages.

Additional [source-control](source-control.md) and [calendar](calendar.md) plugins use the same encrypted configuration and bounded transport. Their administrator-configured HTTPS Git/CalDAV endpoints support self-hosted providers; private network destinations require explicit deployment opt-in. [Composition](composition.md) allows optional shared Microsoft credentials and other declared connections without enabling plugins automatically.
