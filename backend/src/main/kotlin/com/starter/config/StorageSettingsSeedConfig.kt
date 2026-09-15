package com.starter.config

import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val storageSeedLog = LoggerFactory.getLogger(StorageSettingsSeedConfig::class.java)

@Configuration
class StorageSettingsSeedConfig {
    @Bean
    fun seedStorageSettingsRunner(
        repository: AppSettingsRepository,
        @Value("\${app.seed-storage.provider:NONE}") provider: String,
        @Value("\${app.seed-storage.bucket:}") bucket: String,
        @Value("\${app.seed-storage.region:us-east-1}") region: String,
        @Value("\${app.seed-storage.endpoint:}") endpoint: String,
        @Value("\${app.seed-storage.access-key:}") accessKey: String,
        @Value("\${app.seed-storage.secret-key:}") secretKey: String,
        @Value("\${app.seed-storage.root-path:folders}") rootPath: String,
        @Value("\${app.seed-storage.force:false}") force: Boolean,
    ) = ApplicationRunner {
        val normalizedProvider = provider.trim().uppercase()
        if (normalizedProvider == "NONE") return@ApplicationRunner

        val settings = repository.findTopByOrderByCreatedAtAsc() ?: AppSettings()
        if (!force && settings.storageProvider != "NONE") {
            storageSeedLog.info("Keeping existing {} object-storage settings", settings.storageProvider)
            return@ApplicationRunner
        }

        settings.storageProvider = normalizedProvider
        settings.storageBucket = bucket.trim().ifBlank { null }
        settings.storageRegion = region.trim().ifBlank { null }
        settings.storageEndpoint = endpoint.trim().ifBlank { null }
        settings.storageAccessKey = accessKey.trim().ifBlank { null }
        settings.storageSecretKey = secretKey.trim().ifBlank { null }
        settings.storageRootPath = rootPath.replace('\\', '/').trim('/').ifBlank { "folders" }
        repository.save(settings)
        storageSeedLog.info("Seeded {} object-storage settings for bucket {}", normalizedProvider, bucket)
    }
}
