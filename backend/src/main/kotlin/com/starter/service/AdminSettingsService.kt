package com.starter.service

import com.starter.dto.AdminSettingsRequest
import com.starter.dto.AdminSettingsResponse
import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminSettingsService(
    private val appSettingsRepository: AppSettingsRepository,
) {

    @Transactional
    fun getSettings(): AdminSettingsResponse = toResponse(loadOrCreate())

    @Transactional
    fun updateSettings(req: AdminSettingsRequest): AdminSettingsResponse {
        val settings = loadOrCreate()
        settings.storageProvider = req.storageProvider.trim().uppercase()
        settings.storageBucket = req.storageBucket?.trim()?.ifBlank { null }
        settings.storageRegion = req.storageRegion?.trim()?.ifBlank { null }
        settings.storageEndpoint = req.storageEndpoint?.trim()?.ifBlank { null }
        settings.storageAccessKey = req.storageAccessKey?.trim()?.ifBlank { null }
        settings.storageSecretKey = req.storageSecretKey?.trim()?.ifBlank { null }
        settings.storageRootPath = normalizeRootPath(req.storageRootPath)
        settings.vectorReindexEnabled = req.vectorReindexEnabled
        settings.vectorReindexIntervalMinutes = req.vectorReindexIntervalMinutes
        return toResponse(appSettingsRepository.save(settings))
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
