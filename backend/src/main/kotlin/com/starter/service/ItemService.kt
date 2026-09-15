package com.starter.service

import com.starter.dto.ItemRequest
import com.starter.dto.ItemResponse
import com.starter.dto.PageResponse
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

        vectorService.upsert(
            item.id,
            vectorService.embedText(buildVectorText(req.title, req.description, req.tags), vectorSize),
            mapOf("itemId" to item.id.toString(), "title" to item.title)
        )

        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "ITEM_CREATED",
                payload = mapOf("itemId" to item.id.toString(), "title" to item.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "ITEM_CREATED",
            resourceType = "ITEM",
            resourceId = item.id.toString(),
        )

        return item.toResponse()
    }

    @Transactional(readOnly = true)
    fun listShared(page: Int, size: Int): PageResponse<ItemResponse> {
        val p = itemRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return PageResponse(p.content.map { it.toResponse() }, p.number, p.size, p.totalElements, p.totalPages)
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
            mapOf("itemId" to saved.id.toString(), "title" to saved.title)
        )
        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "ITEM_UPDATED",
                payload = mapOf("itemId" to saved.id.toString(), "title" to saved.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "ITEM_UPDATED",
            resourceType = "ITEM",
            resourceId = saved.id.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun delete(email: String, itemId: UUID) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { org.springframework.security.core.userdetails.UsernameNotFoundException("User not found") }
        val item = itemRepository.findById(itemId)
            .orElseThrow { NotFoundException("Item not found") }

        itemRepository.delete(item)
        vectorService.delete(itemId)

        eventRepository.save(
            Event(
                userId = user.id.toString(),
                type = "ITEM_DELETED",
                payload = mapOf("itemId" to itemId.toString(), "title" to item.title, "actorEmail" to user.email)
            )
        )
        auditLogService.record(
            actingEmail = email,
            action = "ITEM_DELETED",
            resourceType = "ITEM",
            resourceId = itemId.toString(),
        )
    }

    @Transactional
    fun reindexShared(): Int {
        val items = itemRepository.findAllByOrderByCreatedAtDesc()

        items.forEach { item ->
            vectorService.upsert(
                item.id,
                vectorService.embedText(buildVectorText(item.title, item.description, item.tags), vectorSize),
                mapOf("itemId" to item.id.toString(), "title" to item.title)
            )
        }

        return items.size
    }

    private fun Item.toResponse() = ItemResponse(
        id = id.toString(), title = title, description = description, tags = tags.toList(),
        createdAt = createdAt?.toString(), updatedAt = updatedAt?.toString()
    )
}
