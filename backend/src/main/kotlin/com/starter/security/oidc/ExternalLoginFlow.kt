package com.starter.security.oidc

import com.starter.dto.LoginResponse
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.security.BrowserSessions
import com.starter.security.RateLimits
import com.starter.security.SecureValues
import com.starter.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class LinkIntent(val provider: String, val userId: UUID, val version: Long, val sessionHash: String) : Serializable
data class LoginFlow(val provider: String, val link: LinkIntent? = null, val startedAt: Instant = Instant.now()) : Serializable
data class PendingLogin(val identity: ProviderIdentity, val flow: LoginFlow) : Serializable
data class ExternalLoginResult(val auth: LoginResponse? = null, val linked: Boolean = false)

@Service
class ExternalLoginFlow(
    private val settings: ExternalLoginSettings,
    private val identities: ExternalIdentities,
    private val auth: AuthService,
    private val rates: RateLimits,
) {
    fun resolver(): OAuth2AuthorizationRequestResolver {
        val delegate = DefaultOAuth2AuthorizationRequestResolver(settings.repository!!, ExternalLoginSettings.AUTHORIZE)
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
        return object : OAuth2AuthorizationRequestResolver {
            override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? = prepare(request, delegate.resolve(request))
            override fun resolve(request: HttpServletRequest, id: String): OAuth2AuthorizationRequest? = prepare(request, delegate.resolve(request, id))
        }
    }

    private fun prepare(request: HttpServletRequest, authorization: OAuth2AuthorizationRequest?): OAuth2AuthorizationRequest? {
        if (authorization == null) return null
        rates.check("external-login-ip", request.remoteAddr, 30, 300)
        val provider = authorization.getAttribute<String>("registration_id") ?: throw BadRequestException("Invalid provider")
        val session = request.getSession(true)
        synchronized(session) {
            val intent = session.getAttribute(INTENT) as? Pair<*, *>
            session.removeAttribute(INTENT)
            val link = if (request.getParameter("link") == "1") {
                val candidate = intent?.first as? LinkIntent
                val expires = intent?.second as? Instant
                if (candidate?.provider != provider || expires == null || expires <= Instant.now())
                    throw ForbiddenException("Start connecting your identity from Security & MFA")
                candidate
            } else null
            session.removeAttribute(PENDING)
            session.setAttribute(FLOW, LoginFlow(provider, link))
        }
        // Force an account choice so linking is deliberate even with an existing provider session.
        return OAuth2AuthorizationRequest.from(authorization).additionalParameters { it["prompt"] = "select_account" }.build()
    }

    fun beginLink(provider: String, email: String, request: HttpServletRequest): String {
        settings.registration(provider)
        val user = identities.account(email)
        val token = BrowserSessions.authToken(request) ?: throw ForbiddenException("Sign in again before connecting an identity")
        request.getSession(true).setAttribute(INTENT, LinkIntent(provider, user.id, user.sessionVersion, SecureValues.hash(token)) to Instant.now().plusSeconds(300))
        return "${ExternalLoginSettings.AUTHORIZE}/$provider?link=1"
    }

    fun success(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        try {
            val session = request.getSession(false) ?: throw BadRequestException("Expired login")
            val flow = session.getAttribute(FLOW) as? LoginFlow ?: throw BadRequestException("Expired login")
            val oauth = authentication as? OAuth2AuthenticationToken ?: throw BadRequestException("Invalid login")
            val user = oauth.principal as? OidcUser ?: throw BadRequestException("An OpenID Connect identity is required")
            val registration = settings.registration(oauth.authorizedClientRegistrationId)
            val issuer = user.idToken.issuer.toString()
            val subject = user.idToken.subject
            if (flow.provider != registration.registrationId || flow.startedAt.plusSeconds(600) <= Instant.now() ||
                issuer != registration.providerDetails.issuerUri || subject.isNullOrBlank() || subject.length > 255)
                throw BadRequestException("Invalid identity")
            request.changeSessionId() // Rotate the temporary handshake session after primary authentication.
            session.removeAttribute(FLOW)
            session.setAttribute(PENDING, PendingLogin(ProviderIdentity(flow.provider, issuer, subject), flow))
            response.sendRedirect("${settings.baseUrl}/login?external=complete")
        } catch (_: Exception) { failure(request, response) }
    }

    fun failure(request: HttpServletRequest, response: HttpServletResponse) {
        request.getSession(false)?.invalidate()
        response.sendRedirect("${settings.baseUrl}/login?external=failed")
    }

    fun complete(request: HttpServletRequest, response: HttpServletResponse, principal: UserDetails?): ExternalLoginResult {
        rates.check("external-complete-ip", request.remoteAddr, 30, 300)
        val session = request.getSession(false) ?: throw BadRequestException("External sign-in expired. Please try again.")
        val pending = synchronized(session) {
            val value = session.getAttribute(PENDING) as? PendingLogin
            session.invalidate() // Completion is single-use, including failed account resolution.
            value
        } ?: throw BadRequestException("External sign-in expired. Please try again.")
        if (pending.flow.startedAt.plusSeconds(600) <= Instant.now()) throw BadRequestException("External sign-in expired. Please try again.")
        settings.registration(pending.identity.provider)
        val link = pending.flow.link
        if (link != null) {
            val user = principal?.let { identities.account(it.username) }
            val token = BrowserSessions.authToken(request)
            if (user?.id != link.userId || token == null || !BrowserSessions.same(SecureValues.hash(token), link.sessionHash))
                throw ForbiddenException("Sign in again before connecting an identity")
            identities.connect(pending.identity, link.userId, link.version)
            return ExternalLoginResult(linked = true)
        }
        return ExternalLoginResult(auth = auth.loginExternal(identities.resolve(pending.identity), response))
    }

    companion object {
        const val INTENT = "external-link-intent"
        const val FLOW = "external-login-flow"
        const val PENDING = "external-login-pending"
    }
}
