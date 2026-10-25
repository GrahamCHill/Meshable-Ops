package com.starter.security.oidc

import com.starter.config.JwtAuthFilter
import com.starter.config.SecurityConfig
import com.starter.security.BrowserSessions
import com.starter.security.RateLimits
import com.starter.service.AuthService
import com.starter.service.JwtService
import com.starter.service.UserDetailsServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.security.oauth2.jwt.JwtDecoderFactory
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

class ExternalLoginSecurityTest {
    class ProviderTransport : OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        private val keys = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey get() = keys.public as java.security.interfaces.RSAPublicKey
        var invalid: String? = null
        var requests = 0
        override fun getTokenResponse(request: OAuth2AuthorizationCodeGrantRequest): OAuth2AccessTokenResponse {
            requests++
            val registration = request.clientRegistration
            val now = java.time.Instant.now()
            val nonce = request.authorizationExchange.authorizationRequest.additionalParameters["nonce"] as String
            val key = if (invalid == "signature") java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private else keys.private
            val token = io.jsonwebtoken.Jwts.builder()
                .issuer(if (invalid == "issuer") "https://attacker.example.test" else registration.providerDetails.issuerUri)
                .subject("connected-subject")
                .audience().add(if (invalid == "audience") "another-client" else registration.clientId).and()
                .issuedAt(java.util.Date.from(now.minusSeconds(30)))
                .expiration(java.util.Date.from(if (invalid == "expiry") now.minusSeconds(300) else now.plusSeconds(300)))
                .claim("nonce", if (invalid == "nonce") "incorrect-nonce" else nonce)
                .signWith(key).compact()
            return OAuth2AccessTokenResponse.withToken("provider-access-token")
                .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(300)
                .scopes(setOf("openid", "profile", "email")).additionalParameters(mapOf("id_token" to token)).build()
        }
    }

    @Configuration @EnableWebMvc @Import(SecurityConfig::class, ExternalLoginController::class)
    open class Config {
        @Bean open fun jwt() = mock(JwtService::class.java)
        @Bean open fun users() = mock(UserDetailsServiceImpl::class.java)
        @Bean open fun sessions() = mock(BrowserSessions::class.java)
        @Bean open fun jwtFilter(jwt: JwtService, users: UserDetailsServiceImpl, sessions: BrowserSessions) = JwtAuthFilter(jwt, users, sessions)
        @Bean open fun externalSettings() = settings(entra = true)
        @Bean open fun identities() = mock(ExternalIdentities::class.java)
        @Bean open fun auth() = mock(AuthService::class.java)
        @Bean open fun rates() = mock(RateLimits::class.java)
        @Bean open fun providerTransport() = ProviderTransport()
        @Bean open fun decoderFactory(transport: ProviderTransport): JwtDecoderFactory<ClientRegistration> = JwtDecoderFactory { registration ->
            NimbusJwtDecoder.withPublicKey(transport.publicKey).build().also {
                it.setJwtValidator(DelegatingOAuth2TokenValidator(JwtTimestampValidator(), OidcIdTokenValidator(registration)))
            }
        }
        @Bean open fun flow(settings: ExternalLoginSettings, identities: ExternalIdentities, auth: AuthService, rates: RateLimits) = ExternalLoginFlow(settings, identities, auth, rates)
    }

    private fun context() = AnnotationConfigWebApplicationContext().apply {
        servletContext = MockServletContext()
        environment.propertySources.addFirst(MapPropertySource("test", mapOf(
            "cors.allowed-origins" to "https://workspace.example.test",
            "jwt.secret" to "external-login-test-signing-key-with-at-least-32-bytes",
            "jwt.expiry-ms" to "3600000",
        )))
        register(Config::class.java)
        refresh()
    }

    @Test fun `enabled providers redirect with state and PKCE and reject a callback with mismatched state`() {
        context().use { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val providers = mvc.perform(get("/api/auth/providers")).andReturn().response
            assertThat(providers.status).isEqualTo(200)
            assertThat(providers.contentAsString).contains("Microsoft Entra ID", "Google").doesNotContain("test-secret", "google-client")
            val start = mvc.perform(get("/api/auth/oidc/authorize/google")).andReturn()
            assertThat(start.response.status).isEqualTo(302)
            assertThat(start.response.redirectedUrl).startsWith("https://accounts.google.com/").contains("state=", "nonce=", "code_challenge=", "code_challenge_method=S256")
            val session = start.request.getSession(false) as MockHttpSession
            val callback = mvc.perform(get("/api/auth/oidc/callback/google").session(session).param("code", "untrusted-code").param("state", "wrong-state")).andReturn().response
            assertThat(callback.redirectedUrl).isEqualTo("https://workspace.example.test/login?external=failed")
            assertThat(session.isInvalid).isTrue()
            verifyNoInteractions(context.getBean(AuthService::class.java), context.getBean(ExternalIdentities::class.java))
        }
    }

    @Test fun `HTTP session security contexts never authenticate application API requests`() {
        context().use { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val session = MockHttpSession()
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextImpl(
                UsernamePasswordAuthenticationToken("provider-subject", null, emptyList())))
            assertThat(mvc.perform(get("/api/account/identities").session(session)).andReturn().response.status).isEqualTo(403)
            assertThat(mvc.perform(get("/api/account/identities")).andReturn().response.status).isEqualTo(403)
        }
    }

    @Test fun `valid signed provider callback creates only a pending handoff even behind an HTTP reverse proxy`() {
        context().use { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val start = mvc.perform(get("/api/auth/oidc/authorize/entra")).andReturn()
            val session = start.request.getSession(false) as MockHttpSession
            val originalSessionId = session.id
            val state = java.net.URLDecoder.decode(org.springframework.web.util.UriComponentsBuilder.fromUriString(start.response.redirectedUrl!!).build().queryParams.getFirst("state")!!, Charsets.UTF_8)
            val callback = mvc.perform(get("/api/auth/oidc/callback/entra").session(session).param("code", "provider-code").param("state", state)).andReturn().response
            assertThat(callback.redirectedUrl).isEqualTo("https://workspace.example.test/login?external=complete")
            assertThat(session.id).isNotEqualTo(originalSessionId)
            assertThat(context.getBean(ProviderTransport::class.java).requests).isEqualTo(1)
            assertThat(session.getAttribute(ExternalLoginFlow.PENDING)).isInstanceOf(PendingLogin::class.java)
            assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull()
            assertThat(callback.getHeaders("Set-Cookie")).isEmpty()
            assertThat(mvc.perform(get("/api/account/identities").session(session)).andReturn().response.status).isEqualTo(403)
            verifyNoInteractions(context.getBean(AuthService::class.java), context.getBean(ExternalIdentities::class.java))
        }
    }

    @Test fun `provider tokens with invalid signature issuer audience expiry or nonce cannot create a handoff`() {
        context().use { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val transport = context.getBean(ProviderTransport::class.java)
            for (invalid in listOf("signature", "issuer", "audience", "expiry", "nonce")) {
                transport.invalid = invalid
                val start = mvc.perform(get("/api/auth/oidc/authorize/entra")).andReturn()
                val session = start.request.getSession(false) as MockHttpSession
                val state = java.net.URLDecoder.decode(org.springframework.web.util.UriComponentsBuilder.fromUriString(start.response.redirectedUrl!!).build().queryParams.getFirst("state")!!, Charsets.UTF_8)
                val requestsBeforeCallback = transport.requests
                val callback = mvc.perform(get("/api/auth/oidc/callback/entra").session(session).param("code", "provider-code").param("state", state)).andReturn().response
                assertThat(callback.redirectedUrl).describedAs(invalid).isEqualTo("https://workspace.example.test/login?external=failed")
                assertThat(transport.requests).describedAs("$invalid reached the token endpoint").isEqualTo(requestsBeforeCallback + 1)
                assertThat(session.isInvalid).isTrue()
            }
            verifyNoInteractions(context.getBean(AuthService::class.java), context.getBean(ExternalIdentities::class.java))
        }
    }

    @Test fun `same origin completion uses the shared MFA gate while cross site POST is blocked`() {
        context().use { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val identity = ProviderIdentity("google", "https://accounts.google.com", "connected-subject")
            val user = com.starter.model.postgres.User(email="local@example.test", username="local", password="hash")
            val challenge = com.starter.mfa.MfaChallengeResponse(challengeToken="test-challenge", methods=listOf(com.starter.mfa.MfaMethod.TOTP), expiresAt=java.time.Instant.now().plusSeconds(300).toString())
            `when`(context.getBean(ExternalIdentities::class.java).resolve(identity)).thenReturn(user)
            `when`(context.getBean(AuthService::class.java).loginExternal(eq(user) ?: user,
                any(jakarta.servlet.http.HttpServletResponse::class.java) ?: org.springframework.mock.web.MockHttpServletResponse())).thenReturn(challenge)
            val session = MockHttpSession()
            session.setAttribute(ExternalLoginFlow.PENDING, PendingLogin(identity, LoginFlow("google")))
            val rejected = mvc.perform(post("/api/auth/oidc/complete").session(session).header("Origin", "https://attacker.example.test").header("Sec-Fetch-Site", "cross-site")).andReturn().response
            assertThat(rejected.status).isEqualTo(403)
            assertThat(session.isInvalid).isFalse()
            val accepted = mvc.perform(post("/api/auth/oidc/complete").accept(org.springframework.http.MediaType.APPLICATION_JSON).session(session).header("Origin", "https://workspace.example.test")).andReturn().response
            assertThat(accepted.status).isEqualTo(200)
            assertThat(accepted.contentAsString).contains("\"mfaRequired\":true", "test-challenge")
            assertThat(accepted.getHeaders("Set-Cookie")).isEmpty()
            assertThat(session.isInvalid).isTrue()
        }
    }
}
