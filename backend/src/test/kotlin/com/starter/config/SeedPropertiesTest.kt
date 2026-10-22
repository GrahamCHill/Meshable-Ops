package com.starter.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.SystemEnvironmentPropertySource

class SeedPropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PropertyConfiguration::class.java)

    @Test
    fun `existing seed defaults are preserved`() {
        contextRunner.run { context ->
            assertThat(context.startupFailure).isNull()
            val admin = context.getBean(AdminSeedProperties::class.java)
            val storage = context.getBean(StorageSeedProperties::class.java)

            assertThat(admin.seedAdmin.email).isEqualTo("admin@app.local")
            assertThat(admin.seedAdmin.role).isEqualTo("ADMIN")
            assertThat(admin.seedUser.email).isEqualTo("user@app.local")
            assertThat(admin.seedUser.role).isEqualTo("USER")
            assertThat(admin.seedDashboard.allowedRoles).isEqualTo("ADMIN,USER")
            assertThat(admin.bootstrapUsersJson).isEmpty()
            assertThat(storage.provider).isEqualTo("NONE")
            assertThat(storage.region).isEqualTo("us-east-1")
            assertThat(storage.rootPath).isEqualTo("folders")
            assertThat(storage.force).isFalse()
        }
    }

    @Test
    fun `deployment environment variable names still bind to grouped settings`() {
        contextRunner.withInitializer { context ->
            context.environment.propertySources.addFirst(SystemEnvironmentPropertySource("seed-test", mapOf(
                "APP_SEED_ADMIN_EMAIL" to "owner@example.com",
                "APP_SEED_USER_USERNAME" to "viewer",
                "APP_BOOTSTRAP_USERS_JSON" to "[]",
                "APP_SEED_DASHBOARD_GUEST_TOKEN_RESOURCE_ID" to "42",
                "APP_SEED_DASHBOARD_ALLOWED_ROLES" to "ADMIN",
                "APP_SEED_STORAGE_PROVIDER" to "AZURE_BLOB",
                "APP_SEED_STORAGE_BUCKET" to "documents",
                "APP_SEED_STORAGE_REGION" to "southafricanorth",
                "APP_SEED_STORAGE_ENDPOINT" to "https://example.blob.core.windows.net",
                "APP_SEED_STORAGE_ACCESS_KEY" to "example",
                "APP_SEED_STORAGE_SECRET_KEY" to "test-only",
                "APP_SEED_STORAGE_ROOT_PATH" to "contracts/documents",
                "APP_SEED_STORAGE_FORCE" to "true",
            )))
        }.run { context ->
            assertThat(context.startupFailure).isNull()
            val admin = context.getBean(AdminSeedProperties::class.java)
            val storage = context.getBean(StorageSeedProperties::class.java)

            assertThat(admin.seedAdmin.email).isEqualTo("owner@example.com")
            assertThat(admin.seedUser.username).isEqualTo("viewer")
            assertThat(admin.bootstrapUsersJson).isEqualTo("[]")
            assertThat(admin.seedDashboard.guestTokenResourceId).isEqualTo("42")
            assertThat(admin.seedDashboard.allowedRoles).isEqualTo("ADMIN")
            assertThat(storage.provider).isEqualTo("AZURE_BLOB")
            assertThat(storage.bucket).isEqualTo("documents")
            assertThat(storage.region).isEqualTo("southafricanorth")
            assertThat(storage.endpoint).isEqualTo("https://example.blob.core.windows.net")
            assertThat(storage.accessKey).isEqualTo("example")
            assertThat(storage.secretKey).isEqualTo("test-only")
            assertThat(storage.rootPath).isEqualTo("contracts/documents")
            assertThat(storage.force).isTrue()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminSeedProperties::class, StorageSeedProperties::class)
    class PropertyConfiguration
}
