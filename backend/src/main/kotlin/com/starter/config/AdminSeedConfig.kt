package com.starter.config

import com.starter.model.postgres.User
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
    fun adminSeedRunner(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        @Value("\${app.seed-admin.email:admin@app.local}") seedEmail: String,
        @Value("\${app.seed-admin.username:admin}") seedUsername: String,
        @Value("\${app.seed-admin.password:admin123}") seedPassword: String,
        @Value("\${app.seed-admin.role:ADMIN}") seedRole: String,
    ) = ApplicationRunner {
        val existingByEmail = userRepository.findByEmail(seedEmail).orElse(null)
        val existingByUsername = userRepository.findByUsername(seedUsername).orElse(null)
        val existing = existingByEmail ?: existingByUsername

        val encodedPassword = passwordEncoder.encode(seedPassword)

        if (existing == null) {
            userRepository.save(
                User(
                    email = seedEmail,
                    username = seedUsername,
                    password = encodedPassword,
                    role = seedRole,
                )
            )
            seedLog.info("Created seed admin user {}", seedEmail)
            return@ApplicationRunner
        }

        val needsUpdate =
            existing.email != seedEmail ||
            existing.username != seedUsername ||
            existing.role != seedRole ||
            !passwordEncoder.matches(seedPassword, existing.password)

        if (needsUpdate) {
            userRepository.save(
                existing.copy(
                    email = seedEmail,
                    username = seedUsername,
                    password = encodedPassword,
                    role = seedRole,
                )
            )
            seedLog.info("Updated seed admin user {}", seedEmail)
        }
    }
}
