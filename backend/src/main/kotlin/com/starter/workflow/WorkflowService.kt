package com.starter.workflow

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.documents.DocumentAccess
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

@Service
class WorkflowService(private val repository: WorkflowRepository, private val access: DocumentAccess,
    private val validation: WorkflowValidation, private val mapper: ObjectMapper) {
    fun list(email: String): List<WorkflowView> { val actor = access.admin(email); return repository.visible(actor.id, true, PageRequest.of(0, 100)).map(::view) }
    @Transactional
    fun save(email: String, id: UUID?, request: WorkflowRequest): WorkflowView {
        val actor = access.admin(email)
        val saved = id?.let { repository.findById(it).orElse(null)?.takeIf { row -> com.starter.access.Roles.admin(actor.role) || row.ownerId == actor.id }
            ?: throw NotFoundException("Workflow unavailable") } ?: WorkflowDefinition(ownerId = actor.id)
        if (id != null && saved.revision != request.revision) throw ConflictException("The workflow changed. Reload before saving.")
        validation.check(request.graph, request.documentIds, request.state == WorkflowState.READY)
        access.references(actor, request.documentIds)
        saved.name = request.name.trim(); saved.state = request.state; saved.graphJson = mapper.writeValueAsString(request.graph)
        saved.published = request.published && request.state == WorkflowState.READY
        saved.documentIdsJson = mapper.writeValueAsString(request.documentIds); saved.updatedAt = Instant.now()
        return view(repository.saveAndFlush(saved))
    }
    private fun view(saved: WorkflowDefinition) = WorkflowView(saved.id, saved.ownerId, saved.name, saved.state, saved.revision, saved.updatedAt,
        mapper.readValue(saved.graphJson, WorkflowGraph::class.java), mapper.readValue(saved.documentIdsJson, object : TypeReference<List<UUID>>() {}), saved.published)
}
