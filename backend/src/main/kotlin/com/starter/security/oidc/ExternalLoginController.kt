package com.starter.security.oidc

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class ExternalLoginController(
    private val settings: ExternalLoginSettings,
    private val identities: ExternalIdentities,
    private val flow: ExternalLoginFlow,
) {
    @GetMapping("/api/auth/providers")
    fun providers(response: HttpServletResponse): List<ExternalProvider> {
        response.setHeader("Cache-Control", "no-store")
        return settings.providers()
    }

    @PostMapping("/api/auth/oidc/complete")
    fun complete(request: HttpServletRequest, response: HttpServletResponse, @AuthenticationPrincipal principal: UserDetails?): ExternalLoginResult {
        response.setHeader("Cache-Control", "no-store")
        return flow.complete(request, response, principal)
    }

    @GetMapping("/api/account/identities")
    fun identities(@AuthenticationPrincipal principal: UserDetails) = identities.linked(principal.username)

    @PostMapping("/api/account/identities/{provider}/connect")
    fun connect(@PathVariable provider: String, @AuthenticationPrincipal principal: UserDetails, request: HttpServletRequest) =
        mapOf("authorizationUrl" to flow.beginLink(provider, principal.username, request))

    @DeleteMapping("/api/account/identities/{provider}")
    fun disconnect(@PathVariable provider: String, @AuthenticationPrincipal principal: UserDetails) {
        identities.disconnect(principal.username, provider)
    }
}
