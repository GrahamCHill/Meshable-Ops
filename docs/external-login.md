# Microsoft Entra ID and Google sign-in

External sign-in is optional, configured on the backend through environment variables, and disabled by default. It is host authentication rather than a runtime plugin: disabling a business plugin must not affect access to the workspace. Both providers share Spring Security's OpenID Connect authorization code flow with PKCE, state, nonce, and ID-token validation.

Accounts remain invite-only. An administrator creates the workspace account as usual. The member signs in with their workspace password (and local MFA when enabled), opens **User settings → Security & MFA**, and chooses **Connect Microsoft Entra ID** or **Connect Google**. They choose their provider account and return to the workspace. On subsequent visits, **Continue with Microsoft Entra ID/Google** on the login page authenticates that connected account. Local MFA is still required when enabled; provider MFA does not bypass it.

Connections use the provider, issuer, and immutable `sub` claim. Email, `preferred_username`, names, provider roles, and provider groups never create, link, or promote a workspace user. This also permits connecting an identity whose email differs from the workspace email. An unknown identity cannot sign in until its owner connects it from an authenticated workspace session. There is no automatic registration, email-based account linking, or first-external-login administrator bootstrap.

## Shared configuration

Set `APP_PUBLIC_BASE_URL` to the exact browser-visible origin, for example `https://app.example.com`. Do not add a path, query, or fragment. Use the same origin for the frontend and its `/api` reverse proxy. HTTP is permitted only for localhost development. Native/Vite development typically uses `http://localhost:5173`; Compose development typically uses `http://localhost:3000`. Set `APP_AUTH_COOKIE_SECURE=false` only for local HTTP development.

The existing `/api` proxy carries all authorization and callback routes. Callback and final redirect URLs come from the configured origin, never forwarded request headers or a user-supplied return URL. No extra public port is needed. Enabling a provider with missing credentials, a missing/invalid public origin, or a non-UUID Entra tenant fails startup with configuration guidance. Disabled providers ignore unused credentials and make no provider requests during startup.

Production Compose sets `CORS_ALLOWED_ORIGINS` from `APP_DOMAIN`. For a custom HTTPS reverse-proxy deployment, set it to the same public origin so the existing origin checks accept completion and other application POSTs.

## Microsoft Entra ID

1. Create a **single-tenant** app registration in your Entra tenant. Choose the Web platform.
2. Register the exact redirect URI `https://app.example.com/api/auth/oidc/callback/entra` (substitute your configured origin).
3. Create a client secret and retain its **value**. Copy the Application (client) ID and Directory (tenant) ID.
4. Configure the backend:

```dotenv
APP_PUBLIC_BASE_URL=https://app.example.com
APP_AUTH_ENTRA_ENABLED=true
APP_AUTH_ENTRA_TENANT_ID=your-directory-tenant-uuid
APP_AUTH_ENTRA_CLIENT_ID=your-application-client-id
APP_AUTH_ENTRA_CLIENT_SECRET=your-client-secret-value
```

The integration uses the tenant-specific v2.0 Microsoft identity endpoints and `openid profile email` scopes. Shared `common`, `organizations`, and `consumers` authorities are deliberately unsupported. Tenant membership/guest access and enterprise-app assignment remain Entra administration concerns; workspace account access and roles are still managed locally. No Microsoft Graph application permissions or group-to-admin mapping are required.

## Google

1. Configure a Google Cloud OAuth consent screen and create an OAuth client of type **Web application**.
2. Register `https://app.example.com/api/auth/oidc/callback/google` as an authorized redirect URI (substitute your configured origin). For local HTTP, register the exact localhost origin/port callback separately.
3. If the consent screen is in testing, add the connecting members as test users. Choose an appropriate audience for your deployment.
4. Configure:

```dotenv
APP_AUTH_GOOGLE_ENABLED=true
APP_AUTH_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
APP_AUTH_GOOGLE_CLIENT_SECRET=your-client-secret
```

Google uses `openid profile email` only. This is separate from Google Drive/calendar plugins and grants no Drive or Calendar access.

## Deployment and account lifecycle

The variables are listed in both environment examples and forwarded by the base Compose file. Recreate/rebuild the backend and frontend with your usual Compose files after deploying the change; recreate the backend after changing environment settings. Keep client secrets in the deployment's private env file or secret store, outside source control. No credentials are exposed by `GET /api/auth/providers` or stored in browser local storage.

Hibernate creates `user_external_identities` during startup. Back it up with PostgreSQL; the administrative JSON export does not include identity connections. Preserve local passwords: they remain the fallback if an identity provider is disabled/unavailable and are required for the existing MFA management actions. Replacing a provider's OAuth client can change its subject identifiers and require members to reconnect with their workspace passwords.

Members can disconnect from Security & MFA, including connections to a disabled provider. One identity per provider can be connected to each workspace user, and one provider identity cannot be shared by two users. Disconnecting prevents future logins through that identity; existing browser sessions retain their normal lifetime. Disable an account or use the existing session-revocation mechanisms when existing access must be removed immediately. Disabled workspace users cannot sign in through a provider.

Only the handshake uses a ten-minute, HttpOnly, SameSite=Lax session cookie restricted to `/api/auth/oidc`. Completion consumes the pending result once in a same-origin POST and then removes the temporary server session. Application cookies retain SameSite=Strict, fingerprint binding, and existing CSRF checks. Provider tokens are discarded after authentication and are never used as application sessions or persisted as integration credentials. In-flight handshakes are lost on backend restart; multiple backend replicas would need sticky routing or shared temporary session storage.

## Verification and troubleshooting

Check that providers are absent when disabled, and that each enabled provider appears on login and Security & MFA. With a test member, connect each provider, log out, sign in through it, and complete local MFA if configured. Confirm that an unconnected identity is rejected, a disabled workspace account is rejected, and disconnecting prevents another external login. Verify both successful sign-in and provider cancellation on the actual deployed HTTPS origin.

A callback mismatch usually means the origin/port/path registered with the provider differs from `APP_PUBLIC_BASE_URL`. An expired sign-in can mean the ten-minute handshake elapsed, the backend restarted, a newer sign-in replaced the pending flow, cookies were blocked, or HTTP development used Secure cookies. Begin again from the login page. Connection completion also requires the original workspace session to remain valid; if you sign out/change accounts or rotate sessions while connecting, sign in and retry from Security & MFA.

Provider setup references: [Spring Security OAuth2 login configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [Microsoft ID-token claims](https://learn.microsoft.com/en-us/entra/identity-platform/id-token-claims-reference), and [Google OpenID Connect setup](https://developers.google.com/identity/openid-connect/openid-connect).
