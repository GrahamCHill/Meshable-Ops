package com.starter.service.pdfstorage

import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import com.starter.repository.postgres.StorageConnectionRepository
import com.starter.service.AdminSettingsService
import com.starter.service.GeneratedPdfCleanupStore
import com.starter.service.storage.GeneratedPdfNamespace
import com.starter.service.storage.normalizeStorageFolder
import com.starter.service.storage.storageViewPrefix
import org.springframework.stereotype.Service
import java.util.UUID

data class PdfStorageChoice(val value: String, val label: String)
data class PdfStorageStatus(val destination: String, val folder: String?, val choices: List<PdfStorageChoice>, val pendingDeletes: Long = 0)

@Service
class GeneratedPdfDestinations(private val settings: AdminSettingsService, private val connections: StorageConnectionRepository,
    private val cleanup: GeneratedPdfCleanupStore, private val plugins: PluginRegistry, private val configuration: PluginConfigurationService,
    private val files: GeneratedPdfFiles? = null) {
    fun resolve(): PdfStorageLocation? = cleanup.settings().let { resolve(it.destination, it.connectionId) }
    fun resolve(destination: PdfStorageDestination, connectionId: UUID?): PdfStorageLocation? {
        return when (destination) {
            PdfStorageDestination.DATABASE -> null
            PdfStorageDestination.AUTO -> objectLocation(settings.currentEntity(), "DEFAULT")
                ?: connections.findAllByEnabledTrueOrderByNicknameAsc().firstOrNull()?.let { objectLocation(it.settings(), "CONNECTION:${it.id}", it.basePath) }
            PdfStorageDestination.DEFAULT_STORAGE -> objectLocation(settings.currentEntity(), "DEFAULT")
                ?: throw BadRequestException("Configure the default storage container first")
            PdfStorageDestination.CONNECTION -> connections.findById(connectionId ?: throw BadRequestException("Choose a storage connection")).orElse(null)
                ?.takeIf { it.enabled }?.let { objectLocation(it.settings(), "CONNECTION:${it.id}", it.basePath) }
                ?: throw BadRequestException("Choose an enabled storage connection")
            else -> drive(destination)
        }
    }

    private fun objectLocation(saved: AppSettings, source: String, base: String = saved.storageViewPrefix()): PdfStorageLocation? {
        if (saved.storageProvider == "NONE" || saved.storageBucket.isNullOrBlank()) return null
        val values = mapOf("bucket" to saved.storageBucket!!, "region" to saved.storageRegion.orEmpty(), "endpoint" to saved.storageEndpoint.orEmpty(),
            "prefix" to normalizeStorageFolder(base) + GeneratedPdfNamespace.FOLDER + "/")
        return PdfStorageLocation(saved.storageProvider, source, values,
            mapOf("accessKey" to saved.storageAccessKey.orEmpty(), "secretKey" to saved.storageSecretKey.orEmpty()).filterValues { it.isNotEmpty() })
    }

    private fun drive(destination: PdfStorageDestination): PdfStorageLocation {
        val id = when (destination) { PdfStorageDestination.GOOGLE_DRIVE -> "google-drive"; PdfStorageDestination.ONEDRIVE -> "onedrive"; else -> "sharepoint" }
        // Storage is an administrator-selected supporting capability. Document owners do not need Drive browsing access.
        plugins.requireEnabled(id, "ADMIN")
        val values = configuration.current(id)
        if (plugins.missingConfiguration(id, values).isNotEmpty()) throw BadRequestException("Complete the $id connection settings first")
        plugins.validateConfiguration(id, values)
        val secrets = setOf("clientSecret", "refreshToken")
        return PdfStorageLocation(destination.name, id, values.filterKeys { it in setOf("tenantId", "clientId", "driveId", "rootItemId", "rootFolderId") }, values.filterKeys { it in secrets })
    }

    /** Rotate credentials at the same service without retargeting an existing object's immutable locator. */
    fun credentials(file: GeneratedPdfFile): GeneratedPdfFile {
        val live = runCatching {
            when {
                file.location.source == "DEFAULT" -> objectLocation(settings.currentEntity(), "DEFAULT")
                file.location.source.startsWith("CONNECTION:") -> connections.findById(UUID.fromString(file.location.source.substringAfter(':'))).orElse(null)?.let {
                    objectLocation(it.settings(), file.location.source, it.basePath)
                }
                else -> drive(PdfStorageDestination.valueOf(file.location.provider))
            }
        }.getOrNull()
        val identity = listOf("bucket", "endpoint", "tenantId", "clientId", "driveId")
        return if (live != null && live.provider == file.location.provider && identity.all { live.values[it] == file.location.values[it] })
            file.copy(location = file.location.copy(credentials = live.credentials)) else file
    }

    fun status(): PdfStorageStatus {
        val selected = runCatching { resolve() }.getOrNull()
        val choices = listOf(PdfStorageChoice("AUTO", "Automatic — prefer configured object storage"), PdfStorageChoice("DATABASE", "Database"),
            PdfStorageChoice("DEFAULT_STORAGE", "Default storage container")) + connections.findAllByEnabledTrueOrderByNicknameAsc().map {
            PdfStorageChoice("CONNECTION:${it.id}", it.nickname)
        } + listOf(PdfStorageChoice("GOOGLE_DRIVE", "Google Drive"), PdfStorageChoice("ONEDRIVE", "OneDrive"), PdfStorageChoice("SHAREPOINT", "SharePoint"))
        return PdfStorageStatus(selected?.provider ?: "DATABASE", selected?.values?.get("prefix") ?: selected?.let { GeneratedPdfNamespace.FOLDER }, choices, files?.pendingCount() ?: 0)
    }
}
