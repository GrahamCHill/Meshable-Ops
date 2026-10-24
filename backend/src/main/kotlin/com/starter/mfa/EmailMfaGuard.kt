package com.starter.mfa

import com.starter.email.EmailConfiguration
import com.starter.email.validateEmailConfiguration
import com.starter.exception.BadRequestException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class EmailMfaGuard(private val jdbc: JdbcTemplate, private val credentials: MfaCredentialRepository) {
    // Shared transaction lock serializes email-only enrollment against delivery configuration changes.
    fun lock() { jdbc.query("SELECT pg_advisory_xact_lock(1938674201)", { _, _ -> 0 }) }
    fun check(config: EmailConfiguration) {
        lock()
        if (credentials.emailOnlyCount() > 0 && (!config.enabled || runCatching { validateEmailConfiguration(config) }.isFailure))
            throw BadRequestException("Email is the only MFA method for an account. Add an authenticator or disable that email MFA before disabling delivery.")
    }
}
