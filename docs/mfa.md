# Optional multi-factor authentication

Every account can configure **Security & MFA** from the workspace footer. Admins also have a card in the admin menu. MFA is optional. A successful first login by the primary administrator opens the setup prompt; **Skip setup for now** dismisses it without enrolling a factor. `APP_MFA_PRIMARY_ADMIN_EMAIL` selects that account; when empty, the oldest active `ADMIN` receives the prompt.

## Enrollment and recovery

1. Enter the current account password.
2. Choose **Set up authenticator** or **Set up email MFA**.
3. For an authenticator, scan the locally generated QR code or enter the setup key. The implementation uses standard [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238) SHA-1 TOTP: six digits and 30-second steps, with one adjacent step of clock tolerance. Keep server time synchronized.
4. For email, first configure [email delivery](email-delivery.md). A code is sent only to the account's saved email address. The factor remains off until the user submits the received code. Saving a sender configuration alone does not enable email MFA.
5. Save the ten recovery codes somewhere secure and separate from the account. They are displayed once; each can be used once instead of a factor. Generating new codes invalidates all old ones.

Adding a second factor, removing a factor, or regenerating recovery codes requires the password and an existing factor or recovery code. An email management code can prove the existing email factor. MFA changes revoke older sessions and pending challenges and return a fresh session to the browser making the change.

Email enrollment is unavailable when delivery is disabled or invalid, and it fails if sending is not accepted. Admin settings refuse to disable or invalidate delivery while any account has email as its sole enrolled factor. Add an authenticator or remove that email factor first. Environment changes and provider outages cannot be prevented by this check: use an authenticator or saved recovery code, or restore delivery. There is no automatic password-only fallback and no unauthenticated factor reset endpoint. Losing all factors and recovery codes requires a deliberate, trusted operational recovery procedure; do not delete MFA rows as a routine support shortcut.

## Login and stored data

Password verification for an enrolled account returns an opaque five-minute challenge, clears the old login cookie, and issues no usable session token. The user then supplies an authenticator code, requests an email code, or enters a recovery code. Challenges accept at most five failed code attempts and cannot be reused. Successful TOTP steps and recovery codes cannot be reused across challenges. Account and IP rate limits also apply to password checks, MFA attempts, and code delivery; email resends have a 60-second cooldown.

Authenticator and pending setup secrets use the persistent AES-GCM wrapping key (`APP_SECRETS_ENCRYPTION_KEY`). Email codes are stored as HMAC proofs bound to their challenge; rotating `JWT_SECRET` invalidates those proofs and all JWT sessions. Random recovery codes are stored as SHA-256 hashes scoped to the account; they survive JWT key rotation. Challenge tokens are stored as SHA-256 hashes. Login and share passwords use salted bcrypt. Setup secrets, verification codes, passwords, and recovery codes stay in component memory rather than local storage or query mutation caches. The account JWT retains the application's existing session storage behavior.

The JWT includes a security version and a completed-MFA claim; the backend checks both against the current account on each authenticated request. Merely possessing a password-only token from before enrollment does not bypass MFA. Expired challenges are periodically removed. Rate counters are shared through PostgreSQL and cleaned hourly. IP limits use the direct request address; a deployment behind a proxy may share an IP allowance unless its trusted forwarding configuration is deliberately configured.

Back up PostgreSQL and the wrapping key together. The application JSON export deliberately omits factors, challenges, recovery hashes, and rate counters. See [API contracts](api.md#authentication-and-mfa) and [OWASP MFA guidance](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).
