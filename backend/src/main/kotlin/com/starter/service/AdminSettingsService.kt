package com.starter.service

import com.starter.dto.AdminSettingsRequest
import com.starter.dto.AdminSettingsResponse
import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.net.URI

@Service
class AdminSettingsService(
    private val appSettingsRepository: AppSettingsRepository,
) {

    @Transactional
    fun getSettings(): AdminSettingsResponse = toResponse(loadOrCreate())

    @Transactional
    fun updateSettings(req: AdminSettingsRequest): AdminSettingsResponse {
        val validated = validatedStorageSettings(req)
        val settings = loadOrCreate()
        settings.storageProvider = validated.storageProvider
        settings.storageBucket = validated.storageBucket
        settings.storageRegion = validated.storageRegion
        settings.storageEndpoint = validated.storageEndpoint
        settings.storageAccessKey = validated.storageAccessKey
        settings.storageSecretKey = validated.storageSecretKey
        settings.storageRootPath = validated.storageRootPath
        settings.vectorReindexEnabled = req.vectorReindexEnabled
        settings.vectorReindexIntervalMinutes = req.vectorReindexIntervalMinutes
        return toResponse(appSettingsRepository.save(settings))
    }

    // Validate a detached configuration so checking a draft never changes saved settings.
    fun validatedStorageSettings(req: AdminSettingsRequest): AppSettings {
        val provider = req.storageProvider.trim().uppercase()
        if (provider !in setOf("NONE", "S3", "AZURE_BLOB")) throw BadRequestException("Choose a supported storage provider")
        val bucket = req.storageBucket?.trim()?.ifBlank { null }
        val endpoint = req.storageEndpoint?.trim()?.ifBlank { null }
        val accessKey = req.storageAccessKey?.trim()?.ifBlank { null }
        val secretKey = req.storageSecretKey?.trim()?.ifBlank { null }
        if (provider != "NONE") {
            if (bucket == null) throw BadRequestException(if (provider == "S3") "Enter an S3 bucket name" else "Enter an Azure container name")
            if ((accessKey == null) != (secretKey == null)) throw BadRequestException("Provide both credential fields, or leave both blank to use the server identity")
            if (provider == "AZURE_BLOB" && endpoint == null) throw BadRequestException("Enter the Azure Blob service endpoint")
            if (endpoint != null) {
                val uri = runCatching { URI(endpoint) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null) {
                    throw BadRequestException("Use an HTTP or HTTPS service endpoint without credentials, query parameters, or fragments")
                }
            }
        }
        if (req.vectorReindexIntervalMinutes !in 15..10080) throw BadRequestException("Choose a reindex interval between 15 and 10080 minutes")
        return AppSettings(
            storageProvider = provider, storageBucket = bucket,
            storageRegion = req.storageRegion?.trim()?.ifBlank { null }, storageEndpoint = endpoint,
            storageAccessKey = accessKey, storageSecretKey = secretKey,
            storageRootPath = normalizeRootPath(req.storageRootPath.trim()),
        )
    }

    @Transactional
    fun currentEntity(): AppSettings = loadOrCreate()

    @Transactional
    fun recordReindexRun(folderCount: Int, documentCount: Int, trigger: String) {
        val settings = loadOrCreate()
        settings.lastVectorReindexAt = Instant.now()
        settings.lastVectorReindexStatus = "OK: Reindexed $folderCount folders and $documentCount documents via $trigger"
        appSettingsRepository.save(settings)
    }

    @Transactional
    fun recordReindexFailure(message: String, trigger: String) {
        val settings = loadOrCreate()
        settings.lastVectorReindexAt = Instant.now()
        settings.lastVectorReindexStatus = "ERROR via $trigger: ${message.take(400)}"
        appSettingsRepository.save(settings)
    }

    private fun loadOrCreate(): AppSettings =
        appSettingsRepository.findTopByOrderByCreatedAtAsc()
            ?: appSettingsRepository.save(AppSettings())

    private fun toResponse(settings: AppSettings) = AdminSettingsResponse(
        id = settings.id.toString(),
        storageProvider = settings.storageProvider,
        storageBucket = settings.storageBucket,
        storageRegion = settings.storageRegion,
        storageEndpoint = settings.storageEndpoint,
        storageAccessKey = settings.storageAccessKey,
        storageSecretKey = settings.storageSecretKey,
        storageRootPath = settings.storageRootPath,
        vectorReindexEnabled = settings.vectorReindexEnabled,
        vectorReindexIntervalMinutes = settings.vectorReindexIntervalMinutes,
        lastVectorReindexAt = settings.lastVectorReindexAt?.toString(),
        lastVectorReindexStatus = settings.lastVectorReindexStatus,
        createdAt = settings.createdAt.toString(),
        updatedAt = settings.updatedAt.toString(),
    )

    private fun normalizeRootPath(path: String): String {
        val segments = path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
            throw BadRequestException("Storage view path must be a non-root path without '.' or '..' segments")
        }
        return segments.joinToString("/")
    }
}
