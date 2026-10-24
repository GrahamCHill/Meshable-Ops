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
    private val userDetailsService: UserDetailsServiceImpl
) {
    @Value("\${cors.allowed-origins}") private lateinit var allowedOrigins: String

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource()) }
        .headers { it.addHeaderWriter(StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow, noarchive, nosnippet, noimageindex")) }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
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
        .build()

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

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsServiceImpl
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (req.requestURI.startsWith("/api/webhooks/incoming/")) { chain.doFilter(req, res); return }
        val token = resolveToken(req)
        if (token == null) {
            chain.doFilter(req, res); return
        }
        try {
            val username = jwtService.extractUsername(token)
            if (username != null && SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(username)
                if (userDetails.isEnabled && jwtService.isTokenValid(token, userDetails)) {
                    val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    auth.details = WebAuthenticationDetailsSource().buildDetails(req)
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        } catch (e: Exception) {
            log.debug("JWT auth failed: ${e.message}")
        }
        chain.doFilter(req, res)
    }

    private fun resolveToken(req: HttpServletRequest): String? {
        val header = req.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }

        return req.cookies
            ?.firstOrNull { it.name == "app_auth_token" && it.value.isNotBlank() }
            ?.value
    }
}
