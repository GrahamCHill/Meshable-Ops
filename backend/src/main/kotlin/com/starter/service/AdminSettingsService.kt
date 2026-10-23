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
import com.starter.security.SecretEncryption

@Service
class AdminSettingsService(
    private val appSettingsRepository: AppSettingsRepository,
    private val encryption: SecretEncryption,
) {

    @Transactional
    fun getSettings(): AdminSettingsResponse = toResponse(loadOrCreate())

    @Transactional
    fun updateSettings(req: AdminSettingsRequest): AdminSettingsResponse {
        val settings = loadOrCreate()
        val validated = validatedStorageSettings(req, settings)
        ReindexSchedule.validate(req.vectorReindexSchedule, req.vectorReindexTime, req.vectorReindexDayOfWeek, req.vectorReindexTimeZone)
        val scheduleChanged = settings.vectorReindexEnabled != req.vectorReindexEnabled || settings.vectorReindexIntervalMinutes != req.vectorReindexIntervalMinutes ||
            settings.vectorReindexSchedule != req.vectorReindexSchedule || settings.vectorReindexTime != req.vectorReindexTime ||
            settings.vectorReindexDayOfWeek != req.vectorReindexDayOfWeek || settings.vectorReindexTimeZone != req.vectorReindexTimeZone
        if (!validated.storageAccessKey.isNullOrBlank() || !validated.storageSecretKey.isNullOrBlank()) encryption.requireConfigured()
        settings.storageProvider = validated.storageProvider
        settings.storageBucket = validated.storageBucket
        settings.storageRegion = validated.storageRegion
        settings.storageEndpoint = validated.storageEndpoint
        settings.storageAccessKey = validated.storageAccessKey
        settings.storageSecretKey = validated.storageSecretKey
        settings.storageRootPath = validated.storageRootPath
        settings.vectorReindexEnabled = req.vectorReindexEnabled
        settings.vectorReindexIntervalMinutes = req.vectorReindexIntervalMinutes
        settings.vectorReindexSchedule = req.vectorReindexSchedule
        settings.vectorReindexTime = req.vectorReindexTime
        settings.vectorReindexDayOfWeek = req.vectorReindexDayOfWeek
        settings.vectorReindexTimeZone = req.vectorReindexTimeZone
        if (scheduleChanged) {
            settings.vectorReindexPendingChanges = false
            settings.vectorReindexLastCheckAt = null
            settings.vectorReindexNextRunAt = if (settings.vectorReindexSchedule == "ON_CHANGE") null else ReindexSchedule.next(settings, Instant.now())
        }
        return toResponse(appSettingsRepository.save(settings))
    }

    // Validate a detached configuration so checking a draft never changes saved settings.
    fun validatedStorageSettings(req: AdminSettingsRequest, saved: AppSettings? = null): AppSettings {
        val provider = req.storageProvider.trim().uppercase()
        if (provider !in setOf("NONE", "S3", "AZURE_BLOB")) throw BadRequestException("Choose a supported storage provider")
        val bucket = req.storageBucket?.trim()?.ifBlank { null }
        val endpoint = req.storageEndpoint?.trim()?.ifBlank { null }
        val sameLocation = saved != null && provider == saved.storageProvider && bucket == saved.storageBucket && endpoint == saved.storageEndpoint
        val keepSaved = req.useServerIdentity == false && sameLocation
        val accessKey = if (req.useServerIdentity == true) null else req.storageAccessKey?.trim()?.ifBlank { null } ?: saved?.storageAccessKey?.takeIf { keepSaved }
        val secretKey = if (req.useServerIdentity == true) null else req.storageSecretKey?.trim()?.ifBlank { null } ?: saved?.storageSecretKey?.takeIf { keepSaved }
        if (keepSaved && accessKey != saved?.storageAccessKey && req.storageSecretKey.isNullOrBlank()) {
            throw BadRequestException("Enter a new secret when changing the account or access key")
        }
        if (provider != "NONE") {
            if (bucket == null) throw BadRequestException(if (provider == "S3") "Enter an S3 bucket name" else "Enter an Azure container name")
            if ((accessKey == null) != (secretKey == null)) throw BadRequestException("Provide both credential fields, or leave both blank to use the server identity")
            if (req.useServerIdentity == false && accessKey == null) throw BadRequestException("Enter credentials for this location or select server identity")
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

    fun storageSettingsForTest(req: AdminSettingsRequest) = validatedStorageSettings(req,
        if (req.useServerIdentity == false && (req.storageAccessKey.isNullOrBlank() || req.storageSecretKey.isNullOrBlank())) currentEntity() else null)

    @Transactional
    fun currentEntity(): AppSettings = loadOrCreate()

    @Transactional
    fun recordReindexRun(folderCount: Int, documentCount: Int, trigger: String, failures: Int = 0) {
        val settings = loadOrCreate()
        settings.lastVectorReindexAt = Instant.now()
        settings.lastVectorReindexStatus = "${if (failures == 0) "OK" else "PARTIAL"}: Reindexed $folderCount folders and $documentCount documents via $trigger; $failures documents failed"
        settings.vectorReindexPendingChanges = failures > 0 && settings.vectorReindexSchedule == "ON_CHANGE"
        settings.vectorReindexNextRunAt = if (settings.vectorReindexSchedule == "ON_CHANGE" && failures == 0) null else ReindexSchedule.next(settings, settings.lastVectorReindexAt!!)
        appSettingsRepository.save(settings)
    }

    @Transactional
    fun recordReindexFailure(message: String, trigger: String) {
        val settings = loadOrCreate()
        settings.lastVectorReindexAt = Instant.now()
        settings.lastVectorReindexStatus = "ERROR via $trigger: ${message.take(400)}"
        settings.vectorReindexNextRunAt = ReindexSchedule.next(settings, settings.lastVectorReindexAt!!)
        appSettingsRepository.save(settings)
    }

    @Transactional
    fun queueFileChanges() {
        val settings = loadOrCreate()
        if (!settings.vectorReindexEnabled || settings.vectorReindexSchedule != "ON_CHANGE") return
        settings.vectorReindexPendingChanges = true
        if (settings.vectorReindexNextRunAt == null) settings.vectorReindexNextRunAt = ReindexSchedule.next(settings, Instant.now())
        appSettingsRepository.save(settings)
    }

    @Transactional
    fun recordChangeCheck(changed: Boolean, now: Instant): AppSettings {
        val settings = loadOrCreate()
        settings.vectorReindexLastCheckAt = now
        if (changed && settings.vectorReindexEnabled && settings.vectorReindexSchedule == "ON_CHANGE") {
            settings.vectorReindexPendingChanges = true
            if (settings.vectorReindexNextRunAt == null) settings.vectorReindexNextRunAt = ReindexSchedule.next(settings, now)
        }
        return appSettingsRepository.save(settings)
    }

    @Transactional
    fun initializeNextRun(now: Instant): AppSettings {
        val settings = loadOrCreate()
        if (settings.vectorReindexNextRunAt == null && settings.vectorReindexSchedule != "ON_CHANGE") {
            settings.vectorReindexNextRunAt = if (settings.vectorReindexSchedule == "INTERVAL")
                ReindexSchedule.next(settings, settings.lastVectorReindexAt ?: now) else ReindexSchedule.next(settings, now)
        }
        return appSettingsRepository.save(settings)
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
        storageAccessKeyConfigured = !settings.storageAccessKey.isNullOrBlank(),
        storageSecretKeyConfigured = !settings.storageSecretKey.isNullOrBlank(),
        storageRootPath = settings.storageRootPath,
        vectorReindexEnabled = settings.vectorReindexEnabled,
        vectorReindexIntervalMinutes = settings.vectorReindexIntervalMinutes,
        lastVectorReindexAt = settings.lastVectorReindexAt?.toString(),
        lastVectorReindexStatus = settings.lastVectorReindexStatus,
        createdAt = settings.createdAt.toString(),
        updatedAt = settings.updatedAt.toString(),
        encryptionReady = encryption.configured,
        vectorReindexSchedule = settings.vectorReindexSchedule,
        vectorReindexTime = settings.vectorReindexTime,
        vectorReindexDayOfWeek = settings.vectorReindexDayOfWeek,
        vectorReindexTimeZone = settings.vectorReindexTimeZone,
        vectorReindexNextRunAt = settings.vectorReindexNextRunAt?.toString(),
        vectorReindexPendingChanges = settings.vectorReindexPendingChanges,
        vectorReindexLastCheckAt = settings.vectorReindexLastCheckAt?.toString(),
    )

    private fun normalizeRootPath(path: String): String {
        val segments = path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
            throw BadRequestException("Storage view path must be a non-root path without '.' or '..' segments")
        }
        return segments.joinToString("/")
    }
}
