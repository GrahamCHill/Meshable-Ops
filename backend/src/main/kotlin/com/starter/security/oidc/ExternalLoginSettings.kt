package com.starter.security.oidc

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.stereotype.Component
import java.net.URI

data class ExternalProvider(val id: String, val name: String, val authorizationUrl: String)

/** Login providers belong to the host's security boundary, independently of runtime plugins. */
@Component
class ExternalLoginSettings(
    @Value("\${app.public-base-url:}") publicBaseUrl: String,
    @Value("\${app.auth.external.entra.enabled:false}") entraEnabled: Boolean,
    @Value("\${app.auth.external.entra.tenant-id:}") tenantId: String,
    @Value("\${app.auth.external.entra.client-id:}") entraClientId: String,
    @Value("\${app.auth.external.entra.client-secret:}") entraClientSecret: String,
    @Value("\${app.auth.external.google.enabled:false}") googleEnabled: Boolean,
    @Value("\${app.auth.external.google.client-id:}") googleClientId: String,
    @Value("\${app.auth.external.google.client-secret:}") googleClientSecret: String,
) {
    val baseUrl = publicBaseUrl.trim().trimEnd('/')
    final val registrations: List<ClientRegistration>
    final val repository: InMemoryClientRegistrationRepository?

    init {
        if (entraEnabled || googleEnabled) {
            val uri = runCatching { URI(baseUrl) }.getOrNull()
            require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty() &&
                (uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "[::1]"))) {
                "External login requires APP_PUBLIC_BASE_URL to be an HTTPS origin (HTTP is allowed on localhost)"
            }
        }
        registrations = buildList {
            if (entraEnabled) {
                require(tenantId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                    "APP_AUTH_ENTRA_TENANT_ID must be a tenant UUID; shared/multi-tenant authorities are not supported"
                }
                require(entraClientId.isNotBlank() && entraClientSecret.isNotBlank()) { "Entra login requires a client ID and client secret" }
                val authority = "https://login.microsoftonline.com/${tenantId.lowercase()}"
                add(ClientRegistration.withRegistrationId("entra")
                    .clientName("Microsoft Entra ID").clientId(entraClientId).clientSecret(entraClientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("$baseUrl/api/auth/oidc/callback/entra")
                    .scope("openid", "profile", "email")
                    .authorizationUri("$authority/oauth2/v2.0/authorize")
                    .tokenUri("$authority/oauth2/v2.0/token")
                    .jwkSetUri("$authority/discovery/v2.0/keys")
                    .issuerUri("$authority/v2.0").userNameAttributeName("sub").build())
            }
            if (googleEnabled) {
                require(googleClientId.isNotBlank() && googleClientSecret.isNotBlank()) { "Google login requires a client ID and client secret" }
                add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientName("Google").clientId(googleClientId).clientSecret(googleClientSecret)
                    .redirectUri("$baseUrl/api/auth/oidc/callback/google")
                    .scope("openid", "profile", "email").build())
            }
        }
        // Explicit endpoints avoid provider discovery/network requests during application startup.
        repository = registrations.takeIf { it.isNotEmpty() }?.let { InMemoryClientRegistrationRepository(it) }
    }

    fun providers() = registrations.map { ExternalProvider(it.registrationId, it.clientName, "$AUTHORIZE/${it.registrationId}") }
    fun registration(id: String) = registrations.firstOrNull { it.registrationId == id }
        ?: throw com.starter.exception.BadRequestException("This sign-in provider is not enabled")

    companion object { const val AUTHORIZE = "/api/auth/oidc/authorize" }
}
