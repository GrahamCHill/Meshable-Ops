# Sending and receiving webhooks

**Admin → Webhooks** manages named incoming or outgoing endpoints, enabled state, authentication, and recent deliveries. Only `ADMIN` can configure, send, or inspect payloads. Set `APP_SECRETS_ENCRYPTION_KEY` first: endpoint secrets and retained payloads are encrypted in PostgreSQL. New or replaced secrets are shown once. Blank secret inputs preserve an existing secret for the same destination; a changed outgoing URL generates a new secret unless supplied explicitly. Direction cannot change after creation.

Outgoing sends are manual from this page or the admin API. They are not automatically subscribed to app events. Incoming JSON is authenticated and retained for admin review; it does not execute code, change app data, trigger emails, or enter search/AI retrieval. MongoDB, audit logs, and event collections are unchanged.

## Signature protocol

This is the application's generic protocol; third-party vendor signature formats need an adapter.

HMAC endpoints use these headers:

| Header | Value |
| --- | --- |
| `X-Webhook-Id` | Unique delivery identifier, 1–128 ASCII letters/digits or `._:-` |
| `X-Webhook-Timestamp` | Unix timestamp in seconds, within five minutes of backend time |
| `X-Webhook-Signature` | `sha256=` followed by the lowercase hex HMAC-SHA256 digest |

The signed string is `timestamp + "." + deliveryId + "." + rawBody`. Sign the exact UTF-8 JSON bytes sent; parsing and reserializing JSON can change the signature. Verification uses constant-time comparison. Incoming requests are `POST /api/webhooks/incoming/{endpointId}` with `Content-Type: application/json`. They do not use an app JWT.

Bearer endpoints instead require `Authorization: Bearer <endpoint-secret>`. Include `X-Webhook-Id` for retry deduplication; otherwise a new ID is assigned to each request. Duplicate `(endpoint, delivery ID)` records are acknowledged without a second retained delivery, while that record is retained. HMAC timestamps still bound signed retries to five minutes.

Outgoing JSON has `{ id, event, createdAt, data }`. The same ID appears in its `X-Webhook-Id` header. The sender signs this exact serialized envelope for HMAC destinations or supplies the configured Bearer secret. A 2xx response records `ACCEPTED`; other status codes or transport failure record `FAILED`. Acceptance does not confirm downstream processing. There are no automatic retries or redirects; check the receiver before manually retrying after a timeout.

## Network and retention limits

- Maximum UTF-8 body: 64 KiB. Invalid JSON/encoding is rejected.
- Public HTTPS destinations are the default. Embedded URL credentials, query parameters, and fragments are rejected. Private, loopback, link-local, multicast, and selected reserved network addresses are blocked during actual DNS resolution, including re-resolution at connection time.
- `APP_WEBHOOK_ALLOW_PRIVATE_TARGETS=true` deliberately allows private network targets; HTTP is allowed only for addresses classified as private/reserved by the client. Restrict this setting to trusted networks. The default remains false.
- The sender uses bounded connection/socket/response waits, two concurrent workers per instance, no redirect following, and no transport retry. Response bodies are discarded.
- IP and endpoint rate limits apply: incoming 180/IP and 120/endpoint per five minutes; outgoing 30/endpoint per five minutes. Direct proxy IPs can share an allowance.
- At most 100 deliveries per endpoint are retained for up to seven days (hourly cleanup). The page loads the latest 50. Payloads are admin-only and encrypted; do not send unnecessary personal data or credentials in JSON. Deduplication ends when the corresponding record is removed.

Set `APP_PUBLIC_BASE_URL` to the reachable HTTPS app origin for complete incoming URLs; otherwise the admin page shows a relative path. Your reverse proxy must route `/api/webhooks/incoming/*` to the backend. Standard HTTPS certificate verification stays enabled. See [API contracts](api.md#webhooks).
