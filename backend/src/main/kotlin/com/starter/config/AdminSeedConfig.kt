package com.starter.config

import com.starter.model.postgres.Item
import com.starter.model.postgres.User
import com.starter.repository.postgres.ItemRepository
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
        itemRepository: ItemRepository,
        passwordEncoder: PasswordEncoder,
        @Value("\${app.seed-admin.email:admin@app.local}") seedEmail: String,
        @Value("\${app.seed-admin.username:admin}") seedUsername: String,
        @Value("\${app.seed-admin.password:admin123}") seedPassword: String,
        @Value("\${app.seed-admin.role:ADMIN}") seedRole: String,
        @Value("\${app.seed-user.email:user@app.local}") standardEmail: String,
        @Value("\${app.seed-user.username:user}") standardUsername: String,
        @Value("\${app.seed-user.password:user123}") standardPassword: String,
        @Value("\${app.seed-user.role:USER}") standardRole: String,
    ) = ApplicationRunner {
        seedUser(userRepository, passwordEncoder, seedEmail, seedUsername, seedPassword, seedRole, "admin")
        val standardUser = seedUser(userRepository, passwordEncoder, standardEmail, standardUsername, standardPassword, standardRole, "standard")
        seedStarterItems(itemRepository, standardUser)
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

    private fun seedStarterItems(itemRepository: ItemRepository, user: User) {
        if (itemRepository.findAllByOrderByCreatedAtDesc().any { it.user.id == user.id }) return

        itemRepository.saveAll(
            listOf(
                Item(
                    user = user,
                    title = "Quarterly Revenue Snapshot",
                    description = "Starter item for the non-admin test account",
                    tags = mutableListOf("demo", "finance"),
                ),
                Item(
                    user = user,
                    title = "Customer Support Trends",
                    description = "Tracks common ticket themes for dashboard testing",
                    tags = mutableListOf("demo", "support"),
                ),
                Item(
                    user = user,
                    title = "Inventory Watchlist",
                    description = "Example row so standard users have visible data immediately",
                    tags = mutableListOf("demo", "ops"),
                ),
            )
        )
        seedLog.info("Seeded starter items for {}", user.email)
    }
}
