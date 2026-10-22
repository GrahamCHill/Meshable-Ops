package com.starter.config

import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val storageSeedLog = LoggerFactory.getLogger(StorageSettingsSeedConfig::class.java)

@Configuration
@EnableConfigurationProperties(StorageSeedProperties::class)
class StorageSettingsSeedConfig {
    @Bean
    fun seedStorageSettingsRunner(
        repository: AppSettingsRepository,
        seed: StorageSeedProperties,
    ) = ApplicationRunner {
        val normalizedProvider = seed.provider.trim().uppercase()
        if (normalizedProvider == "NONE") return@ApplicationRunner

        val settings = repository.findTopByOrderByCreatedAtAsc() ?: AppSettings()
        if (!seed.force && settings.storageProvider != "NONE") {
            storageSeedLog.info("Keeping existing {} object-storage settings", settings.storageProvider)
            return@ApplicationRunner
        }

        settings.storageProvider = normalizedProvider
        settings.storageBucket = seed.bucket.trim().ifBlank { null }
        settings.storageRegion = seed.region.trim().ifBlank { null }
        settings.storageEndpoint = seed.endpoint.trim().ifBlank { null }
        settings.storageAccessKey = seed.accessKey.trim().ifBlank { null }
        settings.storageSecretKey = seed.secretKey.trim().ifBlank { null }
        settings.storageRootPath = seed.rootPath.replace('\\', '/').trim('/').ifBlank { "folders" }
        repository.save(settings)
        storageSeedLog.info("Seeded {} object-storage settings for bucket {}", normalizedProvider, seed.bucket)
    }
}
