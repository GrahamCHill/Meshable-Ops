package com.starter.config

import com.starter.service.JwtService
import com.starter.service.UserDetailsServiceImpl
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import com.starter.security.SaltedPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.StaticHeadersWriter
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

private val log = LoggerFactory.getLogger(SecurityConfig::class.java)

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val userDetailsService: UserDetailsServiceImpl,
    private val externalSettings: com.starter.security.oidc.ExternalLoginSettings,
) {
    @Value("\${cors.allowed-origins}") private lateinit var allowedOrigins: String

    @Bean
    fun securityFilterChain(http: HttpSecurity, externalFlow: com.starter.security.oidc.ExternalLoginFlow): SecurityFilterChain {
        http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource()) }
        .headers { it.addHeaderWriter(StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow, noarchive, nosnippet, noimageindex")) }
        .headers { it.addHeaderWriter(StaticHeadersWriter("Referrer-Policy","no-referrer")) }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .securityContext { it.securityContextRepository(org.springframework.security.web.context.NullSecurityContextRepository()) }
        .requestCache { it.requestCache(org.springframework.security.web.savedrequest.NullRequestCache()) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers("/api/health", "/api/auth/**").permitAll()
            auth.requestMatchers(HttpMethod.POST, "/internal/plugins/host-call").permitAll() // Short-lived, hashed invocation credential is checked by the host broker.
            auth.requestMatchers(HttpMethod.GET, "/api/branding", "/api/branding/assets/*/*").permitAll()
            auth.requestMatchers(HttpMethod.GET, "/api/branding/login-images/*/*").permitAll()
            auth.requestMatchers(HttpMethod.POST, "/api/webhooks/incoming/*").permitAll()
            auth.requestMatchers("/api/shared/**").permitAll()
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            auth.anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        externalSettings.repository?.let { registrations ->
            http.exceptionHandling { it.authenticationEntryPoint(org.springframework.security.web.authentication.Http403ForbiddenEntryPoint()) }
            http.oauth2Login { oauth ->
                oauth.clientRegistrationRepository(registrations)
                    .authorizedClientRepository(DiscardExternalTokens())
                    .loginPage("/login")
                    .authorizationEndpoint { it.authorizationRequestResolver(externalFlow.resolver()) }
                    .redirectionEndpoint { it.baseUri("/api/auth/oidc/callback/*") }
                    .successHandler(externalFlow::success)
                    .failureHandler { request, response, _ -> externalFlow.failure(request, response) }
            }
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOrigins.split(",").map(String::trim)
        val config = CorsConfiguration().apply {
            setAllowedOrigins(origins)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().also { it.registerCorsConfiguration("/**", config) }
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun passwordEncoder() = SaltedPasswordEncoder()
}

/** Provider tokens are used only to authenticate this request, never stored as API credentials. */
private class DiscardExternalTokens : org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository {
    override fun <T : org.springframework.security.oauth2.client.OAuth2AuthorizedClient?> loadAuthorizedClient(
        clientRegistrationId: String, principal: org.springframework.security.core.Authentication?, request: HttpServletRequest,
    ): T? = null
    override fun saveAuthorizedClient(client: org.springframework.security.oauth2.client.OAuth2AuthorizedClient, principal: org.springframework.security.core.Authentication?, request: HttpServletRequest, response: HttpServletResponse) = Unit
    override fun removeAuthorizedClient(clientRegistrationId: String, principal: org.springframework.security.core.Authentication?, request: HttpServletRequest, response: HttpServletResponse) = Unit
}

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsServiceImpl,
    private val sessions: com.starter.security.BrowserSessions
) : OncePerRequestFilter() {
    @Value("\${cors.allowed-origins}") private var allowedOrigins: String = ""

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (req.requestURI.startsWith("/api/webhooks/incoming/")) { chain.doFilter(req, res); return }
        if(req.requestURI.startsWith("/api/") && req.method !in setOf("GET","HEAD","OPTIONS")) {
            val origin=req.getHeader("Origin")
            val own="${req.scheme}://${req.getHeader("Host") ?: req.serverName}"
            if(req.getHeader("Sec-Fetch-Site")=="cross-site" || origin!=null && origin!=own && origin !in allowedOrigins.split(',').map(String::trim)) {
                res.sendError(403,"Cross-site requests are not allowed");return
            }
        }
        val token = resolveToken(req)
        if (token == null) {
            chain.doFilter(req, res); return
        }
        try {
            val username = jwtService.extractUsername(token)
            if (username != null && SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(username)
                if (userDetails.isEnabled && jwtService.isTokenValid(token, userDetails) && sessions.valid(token,req)) {
                    res.setHeader("Cache-Control","no-store")
                    val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    auth.details = WebAuthenticationDetailsSource().buildDetails(req)
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        } catch (e: Exception) {
            log.debug("Browser session authentication rejected")
        }
        chain.doFilter(req, res)
    }

    private fun resolveToken(req: HttpServletRequest): String? {
        return com.starter.security.BrowserSessions.authToken(req)
    }
}
