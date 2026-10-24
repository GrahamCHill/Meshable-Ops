package com.starter.config

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

data class BootstrapAccount(val email: String, val username: String, val password: String,
    val role: String = "USER", val name: String? = null, val contactNumber: String? = null) {
    override fun toString() = "BootstrapAccount(email=$email, password=[redacted])"
}

@Configuration
@EnableConfigurationProperties(BootstrapProperties::class)
class AccountBootstrapConfig {
    @Bean @org.springframework.core.annotation.Order(100)
    fun bootstrapAccounts(mapper: ObjectMapper, users: UserRepository, encoder: PasswordEncoder, settings: BootstrapProperties) = ApplicationRunner {
        val log = LoggerFactory.getLogger(AccountBootstrapConfig::class.java)
        if (settings.bootstrapUsersJson.isBlank()) {
            log.info("No initial accounts configured; application sample-data seeding is disabled")
            return@ApplicationRunner
        }
        val accounts: List<BootstrapAccount> = try {
            mapper.readValue(settings.bootstrapUsersJson, object : TypeReference<List<BootstrapAccount>>() {})
        } catch (_: JsonProcessingException) {
            // Jackson diagnostics may contain source JSON, including passwords.
            throw IllegalArgumentException("APP_BOOTSTRAP_USERS_JSON must be a valid account array")
        }
        val normalized = accounts.map { it.copy(email = it.email.trim().lowercase(), username = it.username.trim(),
            role = it.role.trim().uppercase(), name = it.name?.trim()?.ifBlank { null }, contactNumber = it.contactNumber?.trim()?.ifBlank { null }) }
        require(normalized.size <= 100 && normalized.map { it.email }.distinct().size == normalized.size && normalized.map { it.username }.distinct().size == normalized.size) {
            "Bootstrap accounts must have unique identities and at most 100 entries"
        }
        normalized.forEach {
            require(it.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) && it.email.length <= 255 && it.username.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "Invalid bootstrap account identity" }
            require(it.role in com.starter.access.Roles.all && it.password.length >= 14 && it.password.toByteArray(Charsets.UTF_8).size <= 72) { "Bootstrap accounts need a valid role and a password of 14 characters to 72 UTF-8 bytes" }
        }
        if (normalized.isNotEmpty() && users.count() == 0L) require(normalized.any { com.starter.access.Roles.admin(it.role) }) { "Configure an initial ADMIN account" }
        normalized.forEach { account ->
            val existing = users.findByEmail(account.email).orElse(null)
            if (existing != null) return@forEach // Preserve passwords, roles, MFA state, profiles, and deactivations.
            require(users.findByUsername(account.username).isEmpty) { "Bootstrap username conflicts with an existing account" }
            users.save(User(email = account.email, username = account.username, name = account.name, contactNumber = account.contactNumber,
                password = encoder.encode(account.password), role = account.role))
        }
    }
}
