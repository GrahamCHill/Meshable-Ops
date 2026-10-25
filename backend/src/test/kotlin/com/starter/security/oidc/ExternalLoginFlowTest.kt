package com.starter.security.oidc

import com.starter.dto.AuthResponse
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.security.RateLimits
import com.starter.security.SecureValues
import com.starter.security.SessionUserDetails
import com.starter.service.AuthService
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import java.time.Instant

class ExternalLoginFlowTest {
    private val config = settings()
    private val identities = mock(ExternalIdentities::class.java)
    private val auth = mock(AuthService::class.java)
    private val rates = mock(RateLimits::class.java)
    private val service = ExternalLoginFlow(config, identities, auth, rates)
    private val identity = ProviderIdentity("google", "https://accounts.google.com", "google-subject")
    private val user = User(email = "local@example.test", username = "local", password = "hash")

    private fun request(path: String = "/api/auth/oidc/authorize/google") = MockHttpServletRequest("GET", path).apply { servletPath = path }
    private fun authentication(issuer: String = identity.issuer): OAuth2AuthenticationToken {
        val token = OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(300), mapOf("iss" to issuer, "sub" to identity.subject, "email" to "unrelated@example.test", "roles" to listOf("ADMIN")))
        val principal = DefaultOidcUser(listOf(SimpleGrantedAuthority("OIDC_USER")), token)
        return OAuth2AuthenticationToken(principal, principal.authorities, "google")
    }
    private fun pending(flow: LoginFlow = LoginFlow("google")) = request("/api/auth/oidc/complete").apply {
        method = "POST"
        getSession(true)!!.setAttribute(ExternalLoginFlow.PENDING, PendingLogin(identity, flow))
    }

    @Test fun `authorization uses PKCE nonce state and the configured origin`() {
        val request = request().apply { serverName = "attacker.example.test"; addHeader("Host", "attacker.example.test") }
        val result = service.resolver().resolve(request)!!
        assertThat(result.state).isNotBlank()
        assertThat(result.additionalParameters["nonce"]).isNotNull()
        assertThat(result.additionalParameters["code_challenge_method"]).isEqualTo("S256")
        assertThat(result.additionalParameters["code_challenge"]).isNotNull()
        assertThat(result.redirectUri).isEqualTo("https://workspace.example.test/api/auth/oidc/callback/google")
        assertThat(result.additionalParameters["prompt"]).isEqualTo("select_account")
    }

    @Test fun `a link cannot be started by a cross-site GET without an authenticated connect intent`() {
        assertThatThrownBy { service.resolver().resolve(request().apply { addParameter("link", "1") }) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test fun `callback holds a pending identity without issuing a local session`() {
        val request = request().apply { getSession(true)!!.setAttribute(ExternalLoginFlow.FLOW, LoginFlow("google")) }
        val response = MockHttpServletResponse()
        service.success(request, response, authentication())
        assertThat(response.redirectedUrl).isEqualTo("https://workspace.example.test/login?external=complete")
        assertThat(response.getHeaders("Set-Cookie")).isEmpty()
        assertThat(request.session!!.getAttribute(ExternalLoginFlow.PENDING)).isInstanceOf(PendingLogin::class.java)
        verifyNoInteractions(auth, identities)
    }

    @Test fun `callback rejects a different issuer missing flow or expired flow`() {
        for (flow in listOf(null, LoginFlow("entra"), LoginFlow("google", null, Instant.now().minusSeconds(601)))) {
            val request = request()
            if (flow != null) request.getSession(true)!!.setAttribute(ExternalLoginFlow.FLOW, flow)
            val response = MockHttpServletResponse()
            service.success(request, response, authentication())
            assertThat(response.redirectedUrl).endsWith("?external=failed")
        }
        val request = request().apply { getSession(true)!!.setAttribute(ExternalLoginFlow.FLOW, LoginFlow("google")) }
        val response = MockHttpServletResponse()
        service.success(request, response, authentication("https://attacker.example.test"))
        assertThat(response.redirectedUrl).endsWith("?external=failed")
        verifyNoInteractions(auth, identities)
    }

    @Test fun `completion consumes the result once and uses the shared local authentication gate`() {
        val request = pending()
        val session = request.session as MockHttpSession
        val response = MockHttpServletResponse()
        val result = AuthResponse("", user.id.toString(), user.email, user.username, null, null, "USER", false)
        `when`(identities.resolve(identity)).thenReturn(user)
        `when`(auth.loginExternal(user, response)).thenReturn(result)
        assertThat(service.complete(request, response, null).auth).isSameAs(result)
        assertThat(session.isInvalid).isTrue()
        assertThatThrownBy { service.complete(request, response, null) }.isInstanceOf(BadRequestException::class.java)
        verify(auth, times(1)).loginExternal(user, response)
    }

    @Test fun `completion rejects expired handoffs before resolving accounts`() {
        assertThatThrownBy { service.complete(pending(LoginFlow("google", null, Instant.now().minusSeconds(601))), MockHttpServletResponse(), null) }
            .isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(auth, identities)
    }

    @Test fun `link completion requires the original authenticated browser session`() {
        val link = LinkIntent("google", user.id, user.sessionVersion, SecureValues.hash("original-session"))
        `when`(identities.account(user.email)).thenReturn(user)
        for (token in listOf(null, "changed-session")) {
            val request = pending(LoginFlow("google", link))
            if (token != null) request.setCookies(Cookie("app_auth_token", token))
            assertThatThrownBy { service.complete(request, MockHttpServletResponse(), SessionUserDetails(user, false)) }
                .isInstanceOf(ForbiddenException::class.java)
        }
        val request = pending(LoginFlow("google", link)).apply { setCookies(Cookie("app_auth_token", "original-session")) }
        assertThat(service.complete(request, MockHttpServletResponse(), SessionUserDetails(user, false)).linked).isTrue()
        verify(identities).connect(identity, user.id, user.sessionVersion)
        verifyNoInteractions(auth)
    }
}
