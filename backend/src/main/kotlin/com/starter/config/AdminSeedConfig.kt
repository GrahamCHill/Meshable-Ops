package com.starter.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.model.postgres.User
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import com.starter.repository.postgres.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.crypto.password.PasswordEncoder

private val seedLog = LoggerFactory.getLogger("AdminSeedConfig")

data class BootstrapUserSeed(
    val email: String,
    val username: String,
    val name: String? = null,
    val contactNumber: String? = null,
    val password: String,
    val role: String = "USER",
)

@Configuration
@EnableConfigurationProperties(AdminSeedProperties::class)
class AdminSeedConfig {

    @Bean
    fun seedUserRunner(
        environment: Environment,
        objectMapper: ObjectMapper,
        userRepository: UserRepository,
        dashboardEmbedConfigRepository: DashboardEmbedConfigRepository,
        passwordEncoder: PasswordEncoder,
        settings: AdminSeedProperties,
    ) = ApplicationRunner {
        val isProd = environment.acceptsProfiles(Profiles.of("prod"))
        val bootstrapUsers = parseBootstrapUsers(objectMapper, settings.bootstrapUsersJson)

        when {
            bootstrapUsers.isNotEmpty() -> {
                bootstrapUsers.forEachIndexed { index, seed ->
                    seedUser(
                        userRepository = userRepository,
                        passwordEncoder = passwordEncoder,
                        seed = seed,
                        label = "bootstrap-${index + 1}",
                    )
                }
            }

            isProd -> {
                seedLog.warn("No app.bootstrap-users-json provided for prod profile; skipping user bootstrap")
            }

            else -> {
                seedUser(
                    userRepository = userRepository,
                    passwordEncoder = passwordEncoder,
                    seed = settings.seedAdmin.toSeed(),
                    label = "admin",
                )
                seedUser(
                    userRepository = userRepository,
                    passwordEncoder = passwordEncoder,
                    seed = settings.seedUser.toSeed(),
                    label = "standard",
                )
            }
        }

        seedDashboardEmbed(
            dashboardEmbedConfigRepository,
            settings.seedDashboard.label,
            settings.seedDashboard.description,
            settings.seedDashboard.id,
            settings.seedDashboard.guestTokenResourceId,
            settings.seedDashboard.allowedRoles,
        )
    }

    private fun parseBootstrapUsers(objectMapper: ObjectMapper, bootstrapUsersJson: String): List<BootstrapUserSeed> {
        if (bootstrapUsersJson.isBlank()) {
            return emptyList()
        }

        return objectMapper.readValue(
            bootstrapUsersJson,
            object : TypeReference<List<BootstrapUserSeed>>() {},
        )
    }

    private fun seedUser(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        seed: BootstrapUserSeed,
        label: String,
    ): User {
        val normalizedEmail = seed.email.trim().lowercase()
        val normalizedUsername = seed.username.trim()
        val normalizedName = seed.name?.trim()?.ifBlank { null }
        val normalizedContactNumber = seed.contactNumber?.trim()?.ifBlank { null }
        val normalizedRole = seed.role.trim().uppercase()
        val existingByEmail = userRepository.findByEmail(normalizedEmail).orElse(null)
        val existingByUsername = userRepository.findByUsername(normalizedUsername).orElse(null)
        val existing = existingByEmail ?: existingByUsername
        val encodedPassword = passwordEncoder.encode(seed.password)

        if (existing == null) {
            val created = userRepository.save(
                User(
                    email = normalizedEmail,
                    username = normalizedUsername,
                    name = normalizedName,
                    contactNumber = normalizedContactNumber,
                    password = encodedPassword,
                    role = normalizedRole,
                )
            )
            seedLog.info("Created seed {} user {}", label, normalizedEmail)
            return created
        }

        val needsUpdate =
            existing.email != normalizedEmail ||
            existing.username != normalizedUsername ||
            existing.name != normalizedName ||
            existing.contactNumber != normalizedContactNumber ||
            existing.role != normalizedRole ||
            !existing.active ||
            !passwordEncoder.matches(seed.password, existing.password)

        if (needsUpdate) {
            val updated = userRepository.save(
                existing.copy(
                    email = normalizedEmail,
                    username = normalizedUsername,
                    name = normalizedName,
                    contactNumber = normalizedContactNumber,
                    password = encodedPassword,
                    role = normalizedRole,
                    active = true,
                )
            )
            seedLog.info("Updated seed {} user {}", label, normalizedEmail)
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
