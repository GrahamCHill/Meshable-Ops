# Notifications

Open **Administration > Notifications** (`/admin/notifications`). Its tabs are **Notifications**, **Delivery methods**, **Delivery history**, and **Send message**. The notification-type list and delivery history scroll independently. Search the list to find workflow, security, document, or plugin notifications.

## Delivery methods and messages

Enable or disable each delivery method, then configure its connection. Email links to the existing SMTP/Resend/SendGrid/Mailgun settings page. Plugin methods link to that plugin's settings menu, where credentials remain encrypted and write-only. Enabling a plugin alone does not enable notification delivery through it: switch on its method in Notifications too. New plugin methods default off. Email's notification switch defaults on for compatibility, but its transport is still disabled until configured.

For each notification, choose whether it is enabled, the methods it allows, the methods selected by default, and whether members may choose their own methods. An allowed method must also be switched on globally, configured, and supplied by an enabled plugin. Multiple default methods send separate messages. A shared destination such as Teams or Telegram posts to the destination configured in that plugin; it is not a private member inbox. Verification codes cannot use shared destinations.

Each allowed method has its own message. Email has a subject, plain text, and optional HTML. SMS providers can supply plain text with their own length limit. WhatsApp uses an approved provider template and ordered body parameters, with an optional per-notification approved template name; Meta still controls the approved wording. This adapter does not implement WhatsApp authentication/button templates.

Variables are literal `{{variableName}}` replacements from the selected notification's declared list. There is no expression evaluator, arbitrary field lookup, or remote URL fetch. Preview uses fixed sample values and sends nothing. OTP preview always uses the synthetic code `123456`. Rendered messages must fit their method's limits; content is not silently truncated.

Use **Load HTML email template** for a local `.html`/`.htm` file up to 100 KB, or paste HTML up to 100,000 characters. Safe tables, text, links, and selected inline formatting are retained. Scripts, event handlers, images, forms, embedded documents, external styles, and network CSS are removed by the server. Variables are allowed in text nodes, not attributes, and inserted values are HTML-escaped. Preview uses a sandboxed iframe with network access blocked. Both plain text and HTML are sent so clients can choose either representation. [jsoup sanitizer guidance](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer), [Resend](https://resend.com/docs/api-reference/emails/send-email), [SendGrid](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send), [Mailgun](https://documentation.mailgun.com/docs/mailgun/user-manual/sending-messages/send-http).

Save applies all current edits and uses an optimistic revision; stale writes return 409. Disabling retains templates and provider connection settings. Templates contain ordinary application content, not encrypted credentials: keep credentials in the provider/plugin configuration fields.

## Member choices

Every signed-in member has **Notification preferences** (`/account/notifications`) below Security & MFA. They can use administrator defaults, choose among allowed methods, or deselect all optional methods for a notification. Removing an administrator-allowed method takes effect even if a member previously selected it. When member choice is disabled, administrator defaults apply. Changes are private to that account and revision checked.

Phone-based delivery uses the account's international `contactNumber`; an administrator must provide it. Preferences do not create or verify an OTP contact. General and workflow notifications have preferences. Verification codes and explicit document/share/workflow PDF sends follow the action's own controls instead of an optional-alert opt-out.

## Workflow alerts and PDF sends

Optional alerts are available for workflow started, next step ready, completed, and cancelled. They start disabled. They notify only the run owner using that member's preferences. Dispatch occurs after a successful database commit, on a bounded background executor. A newer run revision supersedes a queued older alert. Unpublished/archived workflows, inactive recipients, and disabled methods block dispatch. Alert failures never undo workflow progress.

These alerts are best effort: shutdown or a full executor queue can drop an alert. There is no automatic retry or durable background job scheduler. Delivery claims prevent an already-attempted event/method/recipient key from sending twice within the journal retention window. Plugins should supply stable operation keys when publishing.

**Workflow email steps** are a separate notification setting. They remain explicit PDF attachment sends, with existing password, watermark, document policy, and durable send-claim checks. Switching this setting off hides the send control and blocks direct sends. Manual confirmation of delivery handled separately remains available. **Document emails** and **Protected file sharing** similarly control their existing explicit attachment sends. SMS does not replace a required PDF email attachment.

## Verification codes

The existing email MFA flow now uses the Verification codes message and Email method. It still requires configured email, verified enrollment, salted/hashed access passwords, and hashed one-use code challenges. The application currently offers authenticator and email MFA; registering an SMS provider does not silently add a new login factor or trust an unverified phone number.

The server plugin API supports issuing a code through any direct method declaring OTP support, including a future SMS provider. The calling authentication feature must first verify the target contact and must implement code generation, expiry, attempts, and verification. A browser cannot supply a verification flag or call the OTP delivery method directly. Codes and rendered message bodies are never saved in notification settings, preferences, or delivery history.

Disabling Email or its Verification codes route is rejected while an account has email as its only MFA method. Add an authenticator or disable that account's email MFA first. The same transaction interlock covers email transport changes and enrollment.

## Manual sends and history

**Send message** uses the saved General messages template and the selected method. It is admin-only and creates a real delivery attempt; saving settings or previewing does not send. Existing email transport tests send a fixed plain-text test directly through the saved transport so it can be diagnosed independently of notification rules. Ordinary email sends honor the notification switches and templates.

History lists the latest 200 attempts and retains metadata for 90 days: notification type, method, status, masked recipient hint, optional recipient account UUID, and timestamps. It omits message bodies, subjects, full addresses, codes, passwords, provider responses, and credentials. `ACCEPTED` means provider acceptance, not recipient delivery. A stuck `SENDING` or failed result requires checking the provider before a deliberate new send. History, templates, and preferences are excluded from document search and AI indexing. Back up PostgreSQL; metadata JSON exports omit these tables.

See [notification API](api.md#notifications) and the [plugin delivery guide](plugins/notifications.md).
