package com.starter.service

import com.starter.dto.ItemRequest
import com.starter.dto.ItemResponse
import com.starter.dto.PageResponse
import com.starter.dto.StorageContentsResponse
import com.starter.dto.StorageEntryResponse
import com.starter.exception.NotFoundException
import com.starter.model.mongo.Event
import com.starter.model.postgres.Item
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ItemService(
    private val itemRepository: ItemRepository,
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
        item.storagePrefix = FolderStorageService.prefixFor(item.id)
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
    fun listShared(page: Int, size: Int): PageResponse<ItemResponse> {
        val p = itemRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        p.content.forEach { item ->
            if (item.storagePrefix == null) item.storagePrefix = FolderStorageService.prefixFor(item.id)
        }
        itemRepository.flush()
        val snapshot = folderStorageService.inspectAndEnsureFolders(p.content.mapNotNull { it.storagePrefix })
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
        val prefix = saved.storagePrefix ?: FolderStorageService.prefixFor(saved.id).also { saved.storagePrefix = it }
        return saved.toResponse(folderStorageService.inspectAndEnsureFolders(listOf(prefix)))
    }

    @Transactional
    fun delete(email: String, itemId: UUID) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException("Item not found") }

        val prefix = item.storagePrefix ?: FolderStorageService.prefixFor(item.id)
        folderStorageService.deleteFolderIfEmpty(prefix)
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
    fun reindexShared(): Int {
        val items = itemRepository.findAllByOrderByCreatedAtDesc()
        items.forEach { item ->
            if (item.storagePrefix == null) item.storagePrefix = FolderStorageService.prefixFor(item.id)
        }
        itemRepository.flush()
        folderStorageService.inspectAndEnsureFolders(items.mapNotNull { it.storagePrefix })

        items.forEach { item ->
            vectorService.upsert(
                item.id,
                vectorService.embedText(buildVectorText(item.title, item.description, item.tags), vectorSize),
                vectorPayload(item)
            )
        }

        return items.size
    }

    @Transactional(readOnly = true)
    fun browse(itemId: UUID, path: String): StorageContentsResponse {
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException("Storage root not found") }
        val rootPrefix = item.storagePrefix ?: FolderStorageService.prefixFor(item.id)
        val result = folderStorageService.browse(rootPrefix, path)
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
                )
            },
        )
    }

    private fun vectorPayload(item: Item) = mapOf(
        "itemId" to item.id.toString(),
        "folderId" to item.id.toString(),
        "entityType" to "folder",
        "title" to item.title,
        "storagePrefix" to (item.storagePrefix ?: FolderStorageService.prefixFor(item.id)),
    )

    private fun Item.toResponse(snapshot: FolderStorageSnapshot) = ItemResponse(
        id = id.toString(), title = title, description = description, tags = tags.toList(),
        storagePrefix = storagePrefix ?: FolderStorageService.prefixFor(id),
        storageProvider = snapshot.provider,
        documentCount = snapshot.documentCounts[storagePrefix ?: FolderStorageService.prefixFor(id)] ?: 0,
        createdAt = createdAt?.toString(), updatedAt = updatedAt?.toString()
    )
}
