package com.starter.service

import com.starter.service.storage.StorageBrowseResult
import com.starter.service.storage.objectStorageAdapter
import com.starter.service.storage.normalizeStorageFolder
import com.starter.dto.*
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageConnection
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import com.starter.security.SecretEncryption

@Service
class StorageConnectionService(
    private val connections: StorageConnectionRepository,
    private val items: ItemRepository,
    private val settings: AdminSettingsService,
    private val itemService: ItemService,
    private val audit: AuditLogService,
    private val encryption: SecretEncryption,
    private val access: com.starter.access.AccessControlService? = null,
) {
    @Transactional(readOnly = true)
    fun listAdmin() = connections.findAllByOrderByNicknameAsc().map { response(it) }

    @Transactional(readOnly = true)
    fun listShared() = connections.findAllByEnabledTrueOrderByNicknameAsc().mapNotNull { connection ->
        val count = if (access == null) items.countByStorageConnectionIdAndAccessibleTrue(connection.id) else items.findAllByStorageConnectionIdOrderByTitleAsc(connection.id).count { it.accessible && access.storage().allows(it.id, "", true) }.toLong()
        if (count == 0L) null else SharedStorageConnectionResponse(connection.id.toString(), connection.nickname, connection.provider, count)
    }

    @Transactional
    fun save(id: UUID?, req: StorageConnectionRequest, email: String): StorageConnectionResponse {
        val existing = id?.let { connection(it) }
        val sameService = existing?.provider == req.provider && existing.endpoint == req.endpoint?.trim()?.ifBlank { null } &&
            (req.provider != "GCS" || existing.bucket == req.bucket.trim())
        val access = if (req.useServerIdentity) null else req.accessKey?.trim()?.ifBlank { null } ?: existing?.accessKey?.takeIf { sameService }
        val secret = if (req.useServerIdentity) null else req.secretKey?.trim()?.ifBlank { null } ?: existing?.secretKey?.takeIf { sameService }
        if (!req.useServerIdentity && (secret == null || (req.provider != "GCS" && access == null))) {
            throw BadRequestException(if (req.provider == "GCS") "Enter service account JSON or use server identity" else "Enter both credentials or use server identity")
        }
        if (!req.useServerIdentity && existing?.secretKey != null && access != existing.accessKey && req.secretKey.isNullOrBlank()) {
            throw BadRequestException("Enter a new secret when changing the account or access key")
        }
        val base = normalizeStorageFolder(req.basePath.trim())
        val validated = settings.validatedStorageSettings(AdminSettingsRequest(
            storageProvider = req.provider, storageBucket = req.bucket, storageRegion = req.region,
            storageEndpoint = req.endpoint, storageAccessKey = access, storageSecretKey = secret,
            storageRootPath = base.ifBlank { "folders" },
        ))
        if (validated.storageProvider == "NONE") throw BadRequestException("Choose S3, Azure Blob, or Google Cloud Storage")
        if (!validated.storageAccessKey.isNullOrBlank() || !validated.storageSecretKey.isNullOrBlank()) encryption.requireConfigured()
        if (existing != null && items.findAllByStorageConnectionIdOrderByTitleAsc(existing.id).isNotEmpty() &&
            (existing.provider != validated.storageProvider || existing.bucket != validated.storageBucket || existing.endpoint != validated.storageEndpoint || existing.basePath != base)) {
            throw ConflictException("Remove the selected folders before changing this connection's storage location")
        }
        val saved = connections.save((existing ?: StorageConnection()).apply {
            nickname = req.nickname.trim().ifEmpty { throw BadRequestException("Enter a connection nickname") }
            provider = validated.storageProvider; bucket = validated.storageBucket!!
            region = validated.storageRegion; endpoint = validated.storageEndpoint
            accessKey = validated.storageAccessKey; secretKey = validated.storageSecretKey
            basePath = base; enabled = req.enabled
        })
        audit.record(actingEmail = email, action = "STORAGE_CONNECTION_SAVED", resourceType = "STORAGE_CONNECTION", resourceId = saved.id.toString())
        return response(saved)
    }

    @Transactional(readOnly = true)
    fun test(id: UUID): MessageResponse {
        val saved = connection(id)
        storageCall { objectStorageAdapter(saved.settings(), saved.basePath).testConnection() }
        return MessageResponse("Connection verified. Only explicitly shared folders will appear in Storage.")
    }

    @Transactional(readOnly = true)
    fun browse(id: UUID, path: String): StorageBrowseResult {
        val saved = connection(id)
        return storageCall { objectStorageAdapter(saved.settings(), saved.basePath).browse(saved.basePath, normalizeStorageFolder(path)) }
            .let { it.copy(entries = it.entries.filter { entry -> entry.type == "FOLDER" }) }
    }

    @Transactional
    fun shareFolder(id: UUID, req: ShareFolderRequest, email: String): SharedFolderResponse {
        val saved = connection(id)
        val relative = normalizeStorageFolder(req.path)
        val prefix = saved.basePath + relative
        if (items.existsByStorageConnectionIdAndStoragePrefix(id, prefix)) throw ConflictException("This folder is already selected. Edit its nickname or visibility below.")
        if (items.findAllByStorageConnectionIdOrderByTitleAsc(id).any { folder ->
            val selected = normalizeStorageFolder(folder.storagePrefix.orEmpty())
            prefix.startsWith(selected) || selected.startsWith(prefix)
        }) throw ConflictException("This selection overlaps another selected folder. Remove the overlapping selection first so visibility remains unambiguous.")
        val nickname = req.nickname.trim().ifEmpty { throw BadRequestException("Enter a folder nickname") }
        val folder = itemService.registerSharedFolder(email, saved, prefix, nickname, req.accessible)
        return SharedFolderResponse(folder.id, folder.title, relative, folder.accessible)
    }

    @Transactional(readOnly = true)
    fun legacyFolders() = items.findAllByStorageConnectionIsNullOrderByTitleAsc().map { folderResponse(it) }

    @Transactional
    fun updateFolder(id: UUID, req: UpdateSharedFolderRequest, email: String): SharedFolderResponse {
        val folder = items.findById(id).orElseThrow { NotFoundException("Folder not found") }
        val nickname = req.nickname.trim().ifEmpty { throw BadRequestException("Enter a folder nickname") }
        itemService.configureSharing(folder, nickname, req.accessible)
        audit.record(actingEmail = email, action = "STORAGE_FOLDER_ACCESS_UPDATED", resourceType = "FOLDER", resourceId = id.toString())
        return folderResponse(folder)
    }

    @Transactional
    fun removeFolder(id: UUID, email: String) {
        val folder = items.findById(id).orElseThrow { NotFoundException("Folder not found") }
        if (folder.storageConnection == null) throw BadRequestException("Use visibility to hide an application-managed folder")
        // ItemService removes metadata and vectors; mapped folders never delete cloud objects.
        itemService.delete(email, id)
    }

    @Transactional
    fun remove(id: UUID, email: String) {
        val saved = connection(id)
        if (items.findAllByStorageConnectionIdOrderByTitleAsc(id).isNotEmpty()) throw ConflictException("Remove this connection's selected folders first. Cloud objects will remain in storage.")
        connections.delete(saved)
        audit.record(actingEmail = email, action = "STORAGE_CONNECTION_REMOVED", resourceType = "STORAGE_CONNECTION", resourceId = id.toString())
    }

    private fun connection(id: UUID) = connections.findById(id).orElseThrow { NotFoundException("Storage connection not found") }
    private fun folderResponse(item: Item) = SharedFolderResponse(item.id.toString(), item.title, item.storagePrefix.orEmpty().removePrefix(item.storageConnection?.basePath.orEmpty()), item.accessible)
    private fun response(connection: StorageConnection) = StorageConnectionResponse(
        connection.id.toString(), connection.nickname, connection.provider, connection.bucket, connection.region,
        connection.endpoint, !connection.accessKey.isNullOrBlank(), !connection.secretKey.isNullOrBlank(), connection.basePath,
        connection.enabled, items.findAllByStorageConnectionIdOrderByTitleAsc(connection.id).map { folderResponse(it) },
    )
    private fun <T> storageCall(action: () -> T): T = try { action() } catch (ex: Exception) {
        if (ex is BadRequestException) throw ex
        throw BadRequestException("Could not read storage. Check the connection details and permission to list this bucket/container and folder.")
    }
}
