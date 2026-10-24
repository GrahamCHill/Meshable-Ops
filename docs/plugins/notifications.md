# Plugin notification delivery

Messaging plugins register delivery methods through a reviewed Spring `NotificationChannelAdapter` bean. The host discovers it at startup and displays it under **Administration > Notifications > Delivery methods**. No core enum, controller, or frontend form is required for a new provider. Add `PluginExtension.NOTIFICATIONS` to the owning plugin manifest, plus `SETTINGS` for connection fields. Use the shared encrypted plugin configuration service for API keys.

```kotlin
@Component
class SmsPortalDelivery(
    private val configuration: PluginConfigurationService,
    private val registry: PluginRegistry,
    private val client: SmsPortalClient,
) : NotificationChannelAdapter {
    override val channel = "smsportal.sms"
    override val pluginId = "smsportal"
    override val label = "SMSportal"
    override val recipientKind = NotificationRecipientKind.PHONE
    override val supportsOtp = true // Only for a direct, verified recipient.
    override val maxTextLength = 1600
    override fun available() = runCatching {
        registry.requireAvailable(pluginId, "ADMIN")
        configuration.view(pluginId).missingRequired.isEmpty()
    }.getOrDefault(false)
    override fun send(message: ChannelMessage): EmailDeliveryResult {
        registry.requireAvailable(pluginId, "ADMIN")
        val credentials = configuration.current(pluginId) // Server only.
        // Implement this provider client and validate its fixed HTTPS endpoint,
        // contact format, encoding/segment costs, limits and acceptance response.
        return client.send(credentials, message.recipient, message.text)
    }
}
```

This is a provider integration example, not an installed SMSportal implementation. Implement the reviewed `SmsPortalClient` and its tests against the provider's current official API before shipping it. Do not put API keys in manifests, browser code, templates, logs, or result messages. The adapter should return a safe status, never raw provider bodies. The core also bounds messages and sanitizes returned failure summaries.

Method IDs must be unique lowercase names up to 80 characters; the plugin ID must refer to an installed module declaring `NOTIFICATIONS`. A plugin can register multiple methods. `recipientKind` is `EMAIL`, `PHONE`, or `FIXED`; `FIXED` means the plugin controls a shared destination and must never declare OTP support. `supportsHtml` and `supportsAttachments` default false. `parameterized` defaults false; an approved-template method receives ordered `templateParameters` and an optional `providerTemplate` override instead of free text. The current attachment type is one bounded PDF through the already-authorized document flow. Declaring a capability is a contract, not permission to bypass document access checks.

New methods start off. Administrators must enable the plugin, complete its connection, enable its notification method, and allow it for the relevant notification types. The host rechecks the plugin and notification settings immediately before transport dispatch. Background notification delivery is server-authorized and uses the plugin's admin connection; member preferences do not grant access to its admin APIs or credentials.

## Publishing optional notifications

After enforcing the plugin feature's business RBAC/ownership and selecting the intended internal recipient, call the server's shared publisher:

```kotlin
publisher.publish(
    event = "general.message",
    userId = intendedMemberId,
    values = mapOf("subject" to "Item updated", "message" to "Your item is ready."),
    key = "tasks:item:$itemId:revision:$revision",
)
```

`NotificationPublisher` applies admin defaults and member preferences and sends each selected method's own template. An optional `selectedMethods` set can narrow, but never expand, the recipient's permitted/preferred methods. Publish only after the business transaction commits; use an `AFTER_COMMIT` listener rather than making a provider request inside a write transaction. Optional workflow alerts demonstrate this pattern in `WorkflowNotificationHooks.kt`.

For extra entries in the administrator's scrollable notification list, implement `NotificationDefinitionProvider` with your plugin ID. Return `NotificationDefinition` values whose IDs begin with `<pluginId>.`, declaring label, description, category, allowed literal variables, and default subject/text. Include `subject`/`message` in the variables if those tokens appear in templates. Definitions are discovered at startup and remain inactive while their plugin is disabled. Do not use a core event ID or namespace. New optional notification definitions should use `enabledByDefault=false`.

## OTP and explicit delivery

Trusted authentication code can call `NotificationService.sendOtp(methodId, target, code, expiresMinutes)`. Construct `NotificationTarget` server-side with the intended address/phone and the exact method ID in `verifiedMethods` only after verifying that contact. This API delivers a code; it does not generate, store, verify, expire, or rate-limit an authentication challenge. Those responsibilities belong to the calling authentication feature. Never expose a controller accepting `verifiedMethods` from a browser. Current built-in MFA remains authenticator/email; SMS login enrollment is not implemented.

`NotificationService.deliver(eventId, methodId, target, values, key)` is the lower-level templated entry point for an already-authorized explicit send. `sendEmail` retains PDF attachments and their calling feature's route. Neither bypasses method/rule switches. The legacy `send(channel, ChannelMessage)` supports explicit admin-composed plugin messages and follows the General messages route and global switches; prefer the publisher for automatic notifications.

Every actual attempt claims a metadata-only delivery record before network I/O. A stable key is scoped to notification/method/recipient. Duplicate attempts are skipped; uncertain sends are not retried automatically. Retention is 90 days. Plugin calls must not add hidden retry loops. Core workflow alerts use a bounded best-effort executor, not a durable queue. Store your own reviewed business jobs if stronger scheduling/delivery guarantees are required.

Test disabled plugin/method/rule states, incomplete credentials, verified-contact enforcement, shared-destination OTP rejection, ownership before publishing, member opt-outs, message lengths, parameterized messages, duplicate keys, sanitized failures, and plaintext/HTML fallback. Use local fake providers; tests must not send real notifications. See [Notification settings](../notifications.md).
