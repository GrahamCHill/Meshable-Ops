package com.starter.service

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
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.security.MessageDigest
import java.time.Instant

data class VectorReindexResult(val folders: Int, val documents: Int)

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
) {
    @Value("\${vector.dimensions}") private var vectorSize: Int = 384

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

        vectorService.upsert(
            item.id,
            vectorService.embedText(buildVectorText(req.title, req.description, req.tags), vectorSize),
            vectorPayload(item)
        )

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

        vectorService.upsert(
            saved.id,
            vectorService.embedText(buildVectorText(req.title, req.description, req.tags), vectorSize),
            vectorPayload(saved)
        )
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
        items.forEach { item ->
            vectorService.upsert(
                item.id,
                vectorService.embedText(buildVectorText(item.title, item.description, item.tags), vectorSize),
                vectorPayload(item)
            )
            documentCount += syncDocuments(
                item,
                folderStorageService.listDocuments(item),
                removeMissing = true,
                forceVectorize = true,
            ).size
        }

        return VectorReindexResult(items.size, documentCount)
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
                    vectorStatus = documents[it.path]?.let { document ->
                        if (document.vectorizedAt == null) "PENDING" else "VECTORIZED"
                    },
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

    @Transactional
    fun registerSharedFolder(email: String, connection: StorageConnection, prefix: String, nickname: String, accessible: Boolean): ItemResponse {
        val user = userRepository.findByEmail(email).orElseThrow { NotFoundException("User not found") }
        val folder = itemRepository.saveAndFlush(Item(
            user = user, title = nickname, storagePrefix = prefix,
            storageConnection = connection, accessible = accessible,
        ))
        vectorService.upsert(folder.id, vectorService.embedText(nickname, vectorSize), vectorPayload(folder))
        auditLogService.record(actingEmail = email, action = "STORAGE_FOLDER_SHARED", resourceType = "FOLDER", resourceId = folder.id.toString())
        return folder.toResponse(FolderStorageSnapshot(connection.provider, emptyMap()))
    }

    fun configureSharing(folder: Item, nickname: String, accessible: Boolean) {
        folder.title = nickname
        folder.accessible = accessible
        itemRepository.saveAndFlush(folder)
        vectorService.upsert(folder.id, vectorService.embedText(buildVectorText(folder.title, folder.description, folder.tags), vectorSize), vectorPayload(folder))
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
    ): Map<String, StorageDocument> {
        val existing = storageDocumentRepository.findAllByItemId(item.id).associateBy { it.storagePath }
        val seen = entries.map { it.path }.toSet()
        val synchronized = entries.associate { entry ->
            val checksum = documentMetadataChecksum(entry)
            val document = existing[entry.path] ?: StorageDocument(item = item, storagePath = entry.path)
            val needsVector = forceVectorize || document.sourceChecksum != checksum || document.vectorizedAt == null
            document.fileName = entry.name
            document.contentType = entry.contentType
            document.sizeBytes = entry.size
            document.storageLastModified = entry.lastModified
            document.processingStatus = "METADATA_ONLY"
            val saved = storageDocumentRepository.saveAndFlush(document)
            if (needsVector) {
                val model = "metadata-v1"
                vectorService.upsertDocument(
                    saved.id,
                    vectorService.embedText(documentVectorText(saved), vectorSize),
                    documentVectorPayload(saved),
                    checksum,
                    model,
                )
                saved.sourceChecksum = checksum
                saved.embeddingModel = model
                saved.vectorizedAt = Instant.now()
                storageDocumentRepository.save(saved)
            }
            entry.path to saved
        }
        if (removeMissing) {
            storageDocumentRepository.deleteAll(existing.values.filter { it.storagePath !in seen })
        }
        return synchronized
    }

    private fun documentVectorText(document: StorageDocument) = listOfNotNull(
        document.fileName,
        document.storagePath.replace('/', ' '),
        document.contentType,
    ).joinToString(" ")

    private fun documentVectorPayload(document: StorageDocument) = buildMap {
        put("documentId", document.id.toString())
        put("itemId", document.item.id.toString())
        put("folderId", document.item.id.toString())
        put("entityType", "document")
        put("title", document.fileName)
        put("storagePath", document.storagePath)
        document.contentType?.let { put("contentType", it) }
        put("processingStatus", document.processingStatus)
    }

    private fun documentMetadataChecksum(entry: StorageEntry): String {
        val source = listOf(entry.path, entry.name, entry.contentType.orEmpty(), entry.size, entry.lastModified).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

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
