package com.starter.service

import com.starter.service.storage.FolderStorageSnapshot
import com.starter.service.storage.StorageEntry
import com.starter.service.storage.StorageObjectUpload
import com.starter.service.storage.StorageObjectDownload
import com.starter.ai.DocumentIndexService
import com.starter.ai.AiUnavailableException
import com.starter.ai.DocumentFingerprints
import com.starter.exception.BadRequestException
import com.starter.dto.ItemRequest
import com.starter.dto.ItemResponse
import com.starter.dto.PageResponse
import com.starter.dto.StorageContentsResponse
import com.starter.dto.StorageEntryResponse
import com.starter.exception.NotFoundException
import com.starter.model.mongo.Event
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageDocument
import com.starter.model.postgres.StorageConnection
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.repository.postgres.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class VectorReindexResult(val folders: Int, val documents: Int, val contentDocuments: Int = 0, val failures: Int = 0)

private const val STORAGE_ROOT_NOT_FOUND = "Storage root not found"

@Service
class ItemService(
    private val itemRepository: ItemRepository,
    private val storageDocumentRepository: StorageDocumentRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val vectorService: VectorService,
    private val folderStorageService: FolderStorageService,
    private val auditLogService: AuditLogService,
    private val documentIndex: DocumentIndexService,
) {
    private fun buildVectorText(title: String, description: String?, tags: List<String>): String =
        listOf(title, description.orEmpty(), tags.joinToString(" ")).joinToString(" ").trim()

    @Transactional
    fun create(email: String, req: ItemRequest): ItemResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }

        val item = itemRepository.saveAndFlush(
            Item(
                user = user,
                title = req.title,
                description = req.description,
                tags = req.tags.toMutableList()
            )
        )
        item.storagePrefix = folderStorageService.prefixFor(item.id)
        itemRepository.saveAndFlush(item)
        folderStorageService.createFolder(item.storagePrefix!!)

        indexFolder(item)

        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "FOLDER_CREATED",
                payload = mapOf("folderId" to item.id.toString(), "title" to item.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "FOLDER_CREATED",
            resourceType = "FOLDER",
            resourceId = item.id.toString(),
        )

        return item.toResponse(folderStorageService.inspectAndEnsureFolders(listOf(item.storagePrefix!!)))
    }

    @Transactional
    fun listShared(page: Int, size: Int, connectionId: UUID? = null): PageResponse<ItemResponse> {
        val p = itemRepository.findVisibleByConnection(connectionId, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100)))
        p.content.forEach { it.ensureCurrentPrefix() }
        itemRepository.flush()
        val snapshot = legacySnapshot(p.content)
        return PageResponse(
            p.content.map { it.toResponse(snapshot) },
            p.number,
            p.size,
            p.totalElements,
            p.totalPages,
        )
    }

    @Transactional
    fun update(email: String, itemId: UUID, req: ItemRequest): ItemResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException("Item not found") }

        item.title = req.title
        item.description = req.description
        item.tags = req.tags.toMutableList()
        item.ensureCurrentPrefix()
        val saved = itemRepository.save(item)

        indexFolder(saved)
        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "FOLDER_UPDATED",
                payload = mapOf("folderId" to saved.id.toString(), "title" to saved.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "FOLDER_UPDATED",
            resourceType = "FOLDER",
            resourceId = saved.id.toString(),
        )
        return saved.toResponse(legacySnapshot(listOf(saved)))
    }

    @Transactional
    fun delete(email: String, itemId: UUID) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException("Item not found") }

        val prefix = item.ensureCurrentPrefix()
        if (item.storageConnection == null) folderStorageService.deleteFolderIfEmpty(prefix)
        storageDocumentRepository.deleteAll(storageDocumentRepository.findAllByItemId(itemId))
        storageDocumentRepository.flush()
        itemRepository.delete(item)
        vectorService.delete(itemId)

        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "FOLDER_DELETED",
                payload = mapOf("folderId" to itemId.toString(), "title" to item.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "FOLDER_DELETED",
            resourceType = "FOLDER",
            resourceId = itemId.toString(),
        )
    }

    @Transactional
    fun reindexShared(): VectorReindexResult {
        val items = itemRepository.findAllVisible()
        items.forEach { it.ensureCurrentPrefix() }
        itemRepository.flush()
        legacySnapshot(items)

        var documentCount = 0
        var contentCount = 0
        var failures = 0
        items.forEach { item ->
            try { vectorService.upsertText(
                item.id,
                buildVectorText(item.title, item.description, item.tags),
                vectorPayload(item)
            ) } catch (_: AiUnavailableException) { failures++ }
              catch (_: BadRequestException) { failures++ }
            val indexed = syncDocuments(
                item,
                folderStorageService.listDocuments(item),
                removeMissing = true,
                forceVectorize = true,
            )
            documentCount += indexed.size
            contentCount += indexed.values.count { it.processingStatus in setOf("CONTENT_INDEXED", "CONTENT_TRUNCATED") }
            failures += indexed.values.count { it.processingStatus in setOf("INDEX_ERROR", "CONTENT_UNAVAILABLE") }
        }

        return VectorReindexResult(items.size, documentCount, contentCount, failures)
    }

    @Transactional(readOnly = true)
    fun hasStorageChanges(): Boolean = itemRepository.findAllVisible().any { item ->
        val current = folderStorageService.listDocuments(item)
        val indexed = storageDocumentRepository.findAllByItemId(item.id).associateBy { it.storagePath }
        current.size != indexed.size || current.any { indexed[it.path]?.sourceChecksum != DocumentFingerprints.metadata(it) }
    }

    @Transactional
    fun browse(itemId: UUID, path: String): StorageContentsResponse {
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException(STORAGE_ROOT_NOT_FOUND) }
        val rootPrefix = folderStorageService.prefixFor(item)
        val result = folderStorageService.browse(item, path)
        val documents = syncDocuments(item, result.entries.filter { it.type == "OBJECT" })
        return StorageContentsResponse(
            folderId = item.id.toString(),
            rootPrefix = rootPrefix,
            path = result.path,
            storageProvider = result.provider,
            entries = result.entries.map {
                StorageEntryResponse(
                    name = it.name,
                    path = it.path,
                    type = it.type,
                    size = it.size,
                    contentType = it.contentType,
                    lastModified = it.lastModified,
                    documentId = documents[it.path]?.id?.toString(),
                    vectorStatus = documents[it.path]?.processingStatus,
                )
            },
        )
    }

    @Transactional
    fun upload(email: String, itemId: UUID, path: String, uploads: List<StorageObjectUpload>): StorageContentsResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }
        if (!user.canUploadDocuments) {
            throw com.starter.exception.ForbiddenException("Document upload permission is required")
        }
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException(STORAGE_ROOT_NOT_FOUND) }
        if (uploads.isEmpty()) throw com.starter.exception.BadRequestException("Select at least one file")

        folderStorageService.upload(item, path, uploads)
        syncDocuments(item, folderStorageService.listDocuments(item))
        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "DOCUMENTS_UPLOADED",
                payload = mapOf("folderId" to item.id.toString(), "count" to uploads.size, "path" to path, "actorEmail" to user.email),
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "DOCUMENTS_UPLOADED",
            resourceType = "FOLDER",
            resourceId = item.id.toString(),
        )
        return browse(itemId, path)
    }

    @Transactional(readOnly = true)
    fun download(itemId: UUID, path: String, archive: Boolean): StorageObjectDownload {
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException(STORAGE_ROOT_NOT_FOUND) }
        return folderStorageService.download(item, path, archive)
    }

    @Transactional(readOnly = true)
    fun getShared(itemId: UUID): ItemResponse {
        val item = itemRepository.findById(itemId).orElseThrow { NotFoundException(STORAGE_ROOT_NOT_FOUND) }
        if (!item.accessible || item.storageConnection?.enabled == false) throw NotFoundException(STORAGE_ROOT_NOT_FOUND)
        return item.toResponse(FolderStorageSnapshot(item.storageConnection?.provider ?: "NONE", emptyMap()))
    }

    @Transactional
    fun registerSharedFolder(email: String, connection: StorageConnection, prefix: String, nickname: String, accessible: Boolean): ItemResponse {
        val user = userRepository.findByEmail(email).orElseThrow { NotFoundException("User not found") }
        val folder = itemRepository.saveAndFlush(Item(
            user = user, title = nickname, storagePrefix = prefix,
            storageConnection = connection, accessible = accessible,
        ))
        indexFolder(folder)
        auditLogService.record(actingEmail = email, action = "STORAGE_FOLDER_SHARED", resourceType = "FOLDER", resourceId = folder.id.toString())
        return folder.toResponse(FolderStorageSnapshot(connection.provider, emptyMap()))
    }

    fun configureSharing(folder: Item, nickname: String, accessible: Boolean) {
        folder.title = nickname
        folder.accessible = accessible
        itemRepository.saveAndFlush(folder)
        indexFolder(folder)
    }

    private fun indexFolder(folder: Item) {
        if (!folder.accessible || folder.storageConnection?.enabled == false) {
            vectorService.delete(folder.id)
            return
        }
        try {
            vectorService.upsertText(folder.id, buildVectorText(folder.title, folder.description, folder.tags), vectorPayload(folder))
        } catch (_: AiUnavailableException) {
            // Sharing and storage changes must remain available when an optional AI service is down.
            vectorService.delete(folder.id)
        } catch (_: BadRequestException) {
            vectorService.delete(folder.id)
        }
    }

    private fun legacySnapshot(items: List<Item>): FolderStorageSnapshot {
        val prefixes = items.filter { it.storageConnection == null }.map { it.ensureCurrentPrefix() }
        return if (prefixes.isEmpty()) FolderStorageSnapshot("NONE", emptyMap()) else folderStorageService.inspectAndEnsureFolders(prefixes)
    }

    private fun vectorPayload(item: Item) = mapOf(
        "itemId" to item.id.toString(),
        "folderId" to item.id.toString(),
        "entityType" to "folder",
        "title" to item.title,
        "storagePrefix" to item.ensureCurrentPrefix(),
    )

    private fun syncDocuments(
        item: Item,
        entries: List<StorageEntry>,
        removeMissing: Boolean = false,
        forceVectorize: Boolean = false,
    ): Map<String, StorageDocument> = documentIndex.sync(item, entries, removeMissing, forceVectorize)

    private fun Item.toResponse(snapshot: FolderStorageSnapshot): ItemResponse {
        val prefix = ensureCurrentPrefix()
        return ItemResponse(
            id = id.toString(), title = title, description = description, tags = tags.toList(),
            storagePrefix = prefix,
            storageProvider = storageConnection?.provider ?: snapshot.provider,
            documentCount = snapshot.documentCounts[prefix] ?: 0,
            createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
            connectionId = storageConnection?.id?.toString(), connectionNickname = storageConnection?.nickname,
            accessible = accessible,
        )
    }

    private fun Item.ensureCurrentPrefix(): String {
        val expected = folderStorageService.prefixFor(this)
        if (storagePrefix != expected) storagePrefix = expected
        return expected
    }
}
