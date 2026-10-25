# Email delivery

Open **Administration > Communications > Outgoing email (SMTP / API)**, or follow **Configure email delivery** from Notifications. Administrators with Communications permission can configure, test, and send email. Regular users cannot use these endpoints.

Email is disabled by default and does not need to be configured for the app to start. Choose **Server environment** or **Admin settings**. The two sources keep separate credentials; switching sources does not copy secrets between them. Environment values are read-only in the app.

## Outgoing SMTP versus incoming IMAPS

| Connection | Purpose | Typical port | Where to configure |
| --- | --- | --- | --- |
| SMTP or email API | Sends invoices, notifications, OTPs and attachments | SMTP `587` with STARTTLS or `465` with TLS; APIs use HTTPS | Communications > Outgoing email |
| IMAPS | Reads a mailbox for incoming replies; cannot send mail | `993`, TLS from connection start | Communications > Incoming email (IMAPS) |

Use your mail provider's separate incoming and outgoing server details. The hostname, username and password/app password may differ. Configuring SMTP does not configure IMAPS, and sending an invoice does not require incoming email.

Create a named incoming connection, enter the IMAPS hostname, port, mailbox login, password/app password and folder, then enable it when complete. Disabled incomplete drafts can be saved. Set the sender's Reply-to address to a mailbox that delivers to that inbox. The Billing plugin's **Invoice email replies** card is also under Communications: select the shared connection and enable **Process invoice replies** there. Incoming email profiles are host-managed and can be reused by other plugins; each consumer must declare the needed schema/fields and have an administrator explicitly select its profile. This is not a general mail client or automatic support for arbitrary reply handlers.

Credentials stay encrypted and write-only. Blank passwords preserve a stored value for the same server/account; changing the hostname, port or username requires re-entering the password. Disable a connection to pause all linked consumers while retaining their selections. Profile changes affect all consumers. SMTP remains the existing shared application sender, independent of incoming profiles. IMAPS currently accepts mailbox/app passwords; it does not acquire or refresh OAuth tokens.

On upgrade, legacy Billing IMAPS fields move once into **Imported invoice inbox**, and Billing retains its processing switch. An explicitly selected profile is preserved. Legacy credential fields are removed from Billing's encrypted configuration. Back up PostgreSQL and wrapping keys before upgrading; avoid mixed old/new backend versions during this migration.

## Configure a sender

1. Select the configuration source. For admin-managed settings, enable email delivery and enter the sender email, optional sender name, and optional reply-to address.
2. Choose **SMTP account or relay**, **Resend API**, **SendGrid API**, or **Mailgun API**.
3. Fill in the transport fields below, save, and use **Send test email** with a recipient you control. The test sends a fixed plain-text message without documents or credentials.
4. **Send email** sends one message through the General messages notification rule and its configured email template. The template may include sanitized HTML with a plain-text fallback. Unsaved changes disable sending. Saving with delivery disabled removes these sending controls; direct send/test requests return HTTP 403.

Notification method switches and per-notification templates are managed in [Notifications](notifications.md). Transport tests use the saved connection independently of those rules. Saving settings does not test the service or send a message. A successful send means the SMTP/API provider accepted the message; delivery to the recipient's inbox is not confirmed. Check the provider's delivery/bounce tools and the recipient mailbox.

## SMTP and app passwords

| Field | Meaning |
| --- | --- |
| SMTP host / port | Hostname without a protocol or embedded credentials; typically submission port `587` or implicit-TLS port `465` |
| STARTTLS | Requires the SMTP server to negotiate TLS before authentication; no fallback to plaintext authentication |
| Implicit TLS | Starts TLS immediately, normally on port `465` |
| No TLS | Allowed only with **No authentication**, for an explicitly configured relay/local mail sink |
| Password or app password | SMTP username plus a normal SMTP credential or provider-generated app password |
| OAuth2 access token | SMTP username plus an externally obtained access token; uses `XOAUTH2` only |
| No authentication | For a relay that authorizes the backend by other means, such as network policy |

Presets fill editable host/port/security/authentication values. They do not create credentials or configure the provider account:

- **Gmail**: `smtp.gmail.com`, `587`, STARTTLS. Put an eligible account's app password in **SMTP password or app password**. Account/organization policy must permit app passwords. See [Google app passwords](https://support.google.com/mail/answer/185833) and [Gmail SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp).
- **Microsoft 365**: `smtp.office365.com`, `587`, STARTTLS, OAuth2. Supply a valid SMTP access token with the required tenant/mailbox permissions. The app does not perform OAuth sign-in, acquire tokens, or refresh them; renew the token externally and update the active configuration before expiry. See [Microsoft SMTP submission](https://learn.microsoft.com/en-us/Exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365).
- **Amazon SES**: the preset uses `email-smtp.us-east-1.amazonaws.com`, `587`, STARTTLS. Change the endpoint for your SES region. Use SES SMTP credentials and a verified sender. See [SES SMTP connections](https://docs.aws.amazon.com/ses/latest/dg/smtp-connect.html) and [SMTP credentials](https://docs.aws.amazon.com/ses/latest/dg/smtp-credentials.html).
- **SendGrid SMTP**: `smtp.sendgrid.net`, `587`, STARTTLS, username `apikey`, and your SendGrid API key as the SMTP password. See [SendGrid SMTP integration](https://www.twilio.com/docs/sendgrid/for-developers/sending-email/integrating-with-the-smtp-api).

Other providers work through their standard SMTP submission settings. Hostname/certificate validation stays enabled. For private certificate authorities, configure the backend JVM trust store rather than bypassing verification. Connection, read, and write timeouts bound SMTP operations.

## Transactional email APIs

| Provider | Default API base URL | Authentication / request |
| --- | --- | --- |
| Resend | `https://api.resend.com` | Bearer API key; `POST /emails` |
| SendGrid | `https://api.sendgrid.com` | Bearer API key; `POST /v3/mail/send` |
| Mailgun | `https://api.mailgun.net/v3` | HTTP Basic username `api` plus key; `POST /{domain}/messages`, multipart form |

Mailgun also requires its verified sending domain. Its EU base URL is `https://api.eu.mailgun.net/v3`; SendGrid regional accounts use their matching API base URL. Use the provider's verified sender/domain and appropriate sending permissions. The adapters send plain text and the configured reply-to. Reference: [Resend send API](https://resend.com/docs/api-reference/emails/send-email), [SendGrid Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send), [Mailgun messages](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages).

API URLs require HTTPS, with a loopback HTTP exception for automated tests. Embedded credentials, query parameters, and fragments are rejected. Redirects are rejected so they cannot forward API credentials. Responses and errors do not expose provider response bodies, keys, or message content.

## Environment configuration

These fields are documented in [`.env.example`](../.env.example) and forwarded to the backend by Docker Compose. They are active when the saved configuration source is `ENVIRONMENT`, which is the initial default.

```dotenv
APP_EMAIL_ENABLED=true
APP_EMAIL_PROVIDER=SMTP
APP_EMAIL_FROM_ADDRESS=notifications@example.com
APP_EMAIL_FROM_NAME=Meshable Ops
APP_EMAIL_REPLY_TO=support@example.com
APP_EMAIL_SMTP_HOST=smtp.example.com
APP_EMAIL_SMTP_PORT=587
APP_EMAIL_SMTP_SECURITY=STARTTLS
APP_EMAIL_SMTP_AUTH=PASSWORD
APP_EMAIL_SMTP_USERNAME=notifications@example.com
APP_EMAIL_SMTP_PASSWORD=<provide-through-your-secret-store>
```

For an API provider, set `APP_EMAIL_PROVIDER=RESEND`, `SENDGRID`, or `MAILGUN`, plus `APP_EMAIL_API_KEY`. `APP_EMAIL_API_BASE_URL` is optional and defaults by provider. Mailgun requires `APP_EMAIL_MAILGUN_DOMAIN`. SMTP fields are irrelevant for API delivery. `APP_EMAIL_SMTP_PASSWORD` also holds an OAuth2 token when `APP_EMAIL_SMTP_AUTH=OAUTH2`. Security modes are `STARTTLS`, `TLS`, and `NONE`; authentication modes are `PASSWORD`, `OAUTH2`, and `NONE`.

Changing environment values requires recreating/restarting the backend with the updated environment. In Docker, `localhost` refers to the backend container; use a relay's Docker service hostname when it runs in the same network. Allow outbound connectivity to your configured SMTP port or HTTPS service.

## Credential storage and rotation

Admin-saved SMTP usernames, SMTP passwords/app passwords/tokens, and API keys use the existing AES-GCM encrypted entity converter. Set `APP_SECRETS_ENCRYPTION_KEY` before saving them. API responses expose only configured flags; credential inputs are write-only, are cleared from the admin form after saving, and are not saved in frontend local storage or the query mutation cache. SMTP credentials must be recoverable for outbound authentication; application login passwords continue to use salted bcrypt hashes.

Blank SMTP inputs preserve credentials only for the same provider, host, port, security, authentication method, and account. When changing accounts, enter the username and credential together. Blank API keys preserve one only for the same provider, API URL, and Mailgun domain. Provider/endpoint changes never borrow a credential from another destination. Explicit remove controls clear credentials; disable delivery first if the resulting configuration would otherwise lack required credentials.

The `email_settings` PostgreSQL row (singleton ID `1`) stores the source, transport fields, sender, and encrypted credentials. Startup secret migration includes this table for wrapping-key rotation. Follow the [existing rotation process](ai-search-and-secrets.md#stored-credentials-and-passwords); retain the current/previous wrapping keys as required. The existing JSON export does not include email settings or credentials: back up PostgreSQL and its wrapping keys separately.

## Using email in application code

Application workflows use `NotificationService` as the delivery boundary. `EMAIL` delegates to `EmailDeliveryService`; SMS methods are supplied by provider plugins. WhatsApp is now an optional plugin channel alongside Telegram and Teams, each with its own settings. See [Notifications](notifications.md) and [integration plugins](plugins/integrations.md). Existing email/MFA/document flows continue to use email and do not switch channels automatically. Direct email callers can still use `send(SendEmailRequest(recipient, subject, text))`; it returns `ACCEPTED`, `DISABLED`, or `UNAVAILABLE`. Provider/configuration failures become an optional result for callers to handle. Invalid message input is rejected. MFA and explicit PDF sharing use this sender; account creation and indexing do not send automatically.

Delivery is synchronous, with at most two concurrent sends per backend instance. Messages are limited to one recipient, a 200-character single-line subject, and 20,000 characters of plain text. Internal callers can include one PDF attachment up to 10 MiB via `EmailAttachment`; public manual email requests cannot supply attachments. [Protected sharing](file-sharing.md) generates an encrypted, watermarked copy before attaching it; passwords are kept out of the email. SMTP sends multipart MIME, Resend/SendGrid send base64 attachment fields, and Mailgun sends a binary multipart attachment. Bulk campaigns, HTML templates, a persistent queue, bounce processing, and automatic retries remain outside this sender. After a timeout, verify provider status before manually retrying because the provider may already have accepted the message.

Email MFA enrollment requires a successfully received code. The admin settings service prevents disabling/invalidating delivery while any account relies on email as its sole enrolled factor; first add an authenticator or remove that email factor. External configuration changes and provider outages still require recovery codes, another factor, or restoring delivery. See [MFA setup and recovery](mfa.md). Generic [webhook management](webhooks.md) receives authenticated JSON but does not implement provider-specific bounce/signature processing.

The admin APIs are described in [API reference](api.md#email-delivery). Local SMTP and HTTP fixtures verify transports, TLS refusal, redaction, credential binding, and authorization. Real provider credentials, DNS authentication, network access, and inbox delivery need verification in the target deployment.

[Generated documents](documents-and-workflows.md) use the same internal PDF attachment support, with a plain or password-protected choice per send. Existing watermark/download policy still applies; passwords are never saved in workflow blocks. This is distinct from external storage-file sharing, which always sends a watermarked protected copy. Workflow email blocks prepare an explicit send form and do not send automatically.
