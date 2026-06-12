package com.starter.config

import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.model.postgres.User
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import com.starter.repository.postgres.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

private val seedLog = LoggerFactory.getLogger("AdminSeedConfig")

@Configuration
class AdminSeedConfig {

    @Bean
    fun seedUserRunner(
        userRepository: UserRepository,
        dashboardEmbedConfigRepository: DashboardEmbedConfigRepository,
        passwordEncoder: PasswordEncoder,
        @Value("\${app.seed-admin.email:admin@app.local}") seedEmail: String,
        @Value("\${app.seed-admin.username:admin}") seedUsername: String,
        @Value("\${app.seed-admin.password:admin123}") seedPassword: String,
        @Value("\${app.seed-admin.role:ADMIN}") seedRole: String,
        @Value("\${app.seed-user.email:user@app.local}") standardEmail: String,
        @Value("\${app.seed-user.username:user}") standardUsername: String,
        @Value("\${app.seed-user.password:user123}") standardPassword: String,
        @Value("\${app.seed-user.role:USER}") standardRole: String,
        @Value("\${app.seed-dashboard.label:Metrics Overview}") dashboardLabel: String,
        @Value("\${app.seed-dashboard.description:Items created over time}") dashboardDescription: String,
        @Value("\${app.seed-dashboard.id:fdda8aae-c6fb-48fc-9322-5f797764b9dd}") dashboardId: String,
        @Value("\${app.seed-dashboard.guest-token-resource-id:}") guestTokenResourceId: String,
        @Value("\${app.seed-dashboard.allowed-roles:ADMIN,USER}") dashboardAllowedRoles: String,
    ) = ApplicationRunner {
        seedUser(userRepository, passwordEncoder, seedEmail, seedUsername, seedPassword, seedRole, "admin")
        seedUser(userRepository, passwordEncoder, standardEmail, standardUsername, standardPassword, standardRole, "standard")
        seedDashboardEmbed(dashboardEmbedConfigRepository, dashboardLabel, dashboardDescription, dashboardId, guestTokenResourceId, dashboardAllowedRoles)
    }

    private fun seedUser(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        email: String,
        username: String,
        password: String,
        role: String,
        label: String,
    ): User {
        val existingByEmail = userRepository.findByEmail(email).orElse(null)
        val existingByUsername = userRepository.findByUsername(username).orElse(null)
        val existing = existingByEmail ?: existingByUsername
        val encodedPassword = passwordEncoder.encode(password)

        if (existing == null) {
            val created = userRepository.save(
                User(
                    email = email,
                    username = username,
                    password = encodedPassword,
                    role = role,
                )
            )
            seedLog.info("Created seed {} user {}", label, email)
            return created
        }

        val needsUpdate =
            existing.email != email ||
            existing.username != username ||
            existing.role != role ||
            !passwordEncoder.matches(password, existing.password)

        if (needsUpdate) {
            val updated = userRepository.save(
                existing.copy(
                    email = email,
                    username = username,
                    password = encodedPassword,
                    role = role,
                )
            )
            seedLog.info("Updated seed {} user {}", label, email)
            return updated
        }

        return existing
    }

    private fun seedDashboardEmbed(
        dashboardEmbedConfigRepository: DashboardEmbedConfigRepository,
        label: String,
        description: String,
        dashboardId: String,
        guestTokenResourceId: String,
        allowedRoles: String,
    ) {
        val normalizedRoles = allowedRoles.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
        val existing = dashboardEmbedConfigRepository.findAll()

        if (existing.isNotEmpty()) {
            val missingRoles = existing.filter { it.allowedRoles.isEmpty() }
            if (missingRoles.isNotEmpty()) {
                missingRoles.forEach { it.allowedRoles = normalizedRoles.toMutableList() }
                dashboardEmbedConfigRepository.saveAll(missingRoles)
                seedLog.info("Backfilled allowed roles for {} dashboard embed configs", missingRoles.size)
            }
            val staleSeedResourceIds = existing.filter {
                it.dashboardId == dashboardId && it.guestTokenResourceId == "2"
            }
            if (staleSeedResourceIds.isNotEmpty()) {
                staleSeedResourceIds.forEach { it.guestTokenResourceId = null }
                dashboardEmbedConfigRepository.saveAll(staleSeedResourceIds)
                seedLog.info("Cleared stale guest token resource ids for {} dashboard embeds", staleSeedResourceIds.size)
            }
            return
        }

        dashboardEmbedConfigRepository.save(
            DashboardEmbedConfig(
                label = label,
                description = description,
                dashboardId = dashboardId,
                guestTokenResourceId = guestTokenResourceId.ifBlank { null },
                sortOrder = 0,
                enabled = true,
                allowedRoles = normalizedRoles.toMutableList(),
            )
        )
        seedLog.info("Seeded default dashboard embed {}", dashboardId)
    }
}
