package com.starter.plugins.workflows

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.documents.DocumentAccess
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

@Service
class WorkflowService(private val repository: WorkflowRepository, private val access: DocumentAccess,
    private val validation: WorkflowValidation, private val mapper: ObjectMapper, private val modules: com.starter.plugins.BundledModuleAccess,
    private val users: com.starter.repository.postgres.UserRepository? = null, private val audit: com.starter.service.AuditLogService? = null,
    private val diagramBindings: WorkflowDiagramBindings? = null, private val webhookConfiguration: WorkflowWebhookConfiguration? = null) {
    fun webhookEndpoints(email: String): List<Map<String,String>> {
        val actor=access.admin(email);modules.check("workflows",actor,"workflow_definitions","admin.workflows")
        return webhookConfiguration?.options().orEmpty()
    }
    fun list(email: String): List<WorkflowView> { val actor = access.admin(email); modules.check("workflows", actor, "workflow_definitions", "admin.workflows"); return repository.visible(actor.id, true, PageRequest.of(0, 100)).map(::view) }
    fun reviewers(email: String): List<Map<String,String>> {
        val actor=access.admin(email);modules.check("workflows",actor,"workflow_definitions","admin.workflows")
        return users?.findAll()?.filter { it.active && it.role!="EXTERNAL" }?.map { mapOf("id" to it.id.toString(),"name" to (it.name ?: it.username),"email" to it.email) }.orEmpty()
    }
    @Transactional
    fun save(email: String, id: UUID?, request: WorkflowRequest): WorkflowView {
        val actor = access.admin(email)
        modules.check("workflows", actor, "workflow_definitions", "admin.workflows", write = true)
        val saved = id?.let { repository.findById(it).orElse(null)?.takeIf { row -> com.starter.access.Roles.admin(actor.role) || row.ownerId == actor.id }
            ?: throw NotFoundException("Workflow unavailable") } ?: WorkflowDefinition(ownerId = actor.id)
        if (id != null && saved.revision != request.revision) throw ConflictException("The workflow changed. Reload before saving.")
        validation.check(request.graph, request.documentIds, request.state == WorkflowState.READY)
        if(request.state==WorkflowState.READY)webhookConfiguration?.check(request.graph)
        val reviewers = request.graph.nodes.flatMap { it.reviewerIds }.toSet()
        if (users != null && reviewers.isNotEmpty() && users.findAllById(reviewers).count { it.active && it.role != "EXTERNAL" } != reviewers.size)
            throw com.starter.exception.BadRequestException("Reviewers must be active internal members")
        access.references(actor, request.documentIds)
        saved.name = request.name.trim(); saved.state = request.state; saved.graphJson = mapper.writeValueAsString(request.graph)
        saved.published = request.published && request.state == WorkflowState.READY
        saved.documentIdsJson = mapper.writeValueAsString(request.documentIds); saved.updatedAt = Instant.now()
        val row = repository.saveAndFlush(saved)
        diagramBindings?.sync(email,row,request.graph)
        audit?.record(email,"WORKFLOW_DESIGN_SAVED","WORKFLOW",row.id.toString())
        return view(row)
    }
    private fun view(saved: WorkflowDefinition) = WorkflowView(saved.id, saved.ownerId, saved.name, saved.state, saved.revision, saved.updatedAt,
        mapper.readValue(saved.graphJson, WorkflowGraph::class.java), mapper.readValue(saved.documentIdsJson, object : TypeReference<List<UUID>>() {}), saved.published)
}
