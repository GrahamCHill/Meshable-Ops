package com.starter.security.oidc

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

internal fun settings(
    base: String = "https://workspace.example.test", entra: Boolean = false, google: Boolean = true,
    tenant: String = "11111111-2222-3333-4444-555555555555", secret: String = "test-secret",
) = ExternalLoginSettings(base, entra, tenant, "entra-client", secret, google, "google-client", secret)

class ExternalLoginSettingsTest {
    @Test fun `providers are opt in and disabled configuration needs no origin or secrets`() {
        val disabled = ExternalLoginSettings("", false, "", "", "", false, "", "")
        assertThat(disabled.providers()).isEmpty()
        assertThat(disabled.repository).isNull()
        assertThatThrownBy { disabled.registration("google") }.hasMessageContaining("not enabled")
    }

    @Test fun `each enabled provider has a fixed origin callback and issuer`() {
        val config = settings(entra = true)
        assertThat(config.providers().map { it.id }).containsExactly("entra", "google")
        assertThat(config.providers().toString()).doesNotContain("test-secret", "entra-client", "google-client")
        for (registration in config.registrations) {
            assertThat(registration.redirectUri).isEqualTo("https://workspace.example.test/api/auth/oidc/callback/${registration.registrationId}")
            assertThat(registration.scopes).containsExactlyInAnyOrder("openid", "profile", "email")
            assertThat(registration.providerDetails.issuerUri).isNotBlank()
        }
        assertThat(config.registration("entra").providerDetails.issuerUri)
            .isEqualTo("https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555/v2.0")
    }

    @Test fun `misconfigured enabled providers fail with safe configuration guidance`() {
        for (base in listOf("", "http://public.example.test", "https://user:password@example.test", "https://example.test/path", "https://example.test?x=1", "https://example.test#x")) {
            assertThatThrownBy { settings(base = base) }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("APP_PUBLIC_BASE_URL")
        }
        for (tenant in listOf("common", "organizations", "consumers", "example.test", "../tenant")) {
            assertThatThrownBy { settings(entra = true, tenant = tenant) }.hasMessageContaining("tenant UUID")
        }
        assertThatThrownBy { settings(secret = "") }.hasMessageContaining("client ID and client secret")
        assertThat(settings(base = "http://localhost:5173").baseUrl).isEqualTo("http://localhost:5173")
    }
}
