package com.starter.plugins.workflows

import com.starter.exception.*
import com.starter.plugins.documents.DocumentAccess
import com.starter.plugins.BundledModuleAccess
import com.starter.service.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowBindingService(private val bindings: WorkflowActionBindingRepository, private val definitions: WorkflowRepository,
    private val access: DocumentAccess, private val modules: BundledModuleAccess, private val catalogue: WorkflowActionCatalogue,
    private val runtime: com.fasterxml.jackson.databind.ObjectMapper, private val audit: AuditLogService) {
    private fun admin(email: String) = access.admin(email).also { modules.check("workflows",it,"workflow_definitions","admin.workflows",write=true) }
    fun list(email: String): List<WorkflowBindingView> { admin(email); return bindings.findAll().map(::view) }
    fun actions(email: String): List<WorkflowActionDescriptor> { admin(email); return catalogue.all() }
    @Transactional
    fun save(email: String, id: UUID?, request: WorkflowBindingRequest): WorkflowBindingView {
        admin(email)
        if (request.enabled && catalogue.all().none { it.id == request.selector }) throw BadRequestException("Choose an installed server action")
        if (request.contextField.isNotEmpty() && (!request.contextField.startsWith("trigger.") || request.contextEquals.isBlank())) throw BadRequestException("Choose a trigger context field and matching value")
        if (request.enabled) {
            val source = definitions.findById(request.workflowId).orElse(null) ?: throw NotFoundException("Workflow unavailable")
            if (source.state != WorkflowState.READY || !source.published) throw BadRequestException("Bindings require a published ready workflow")
            val graph = runtime.readValue(source.graphJson,WorkflowGraph::class.java)
            if (graph.nodes.any { it.kind == BlockKind.EMAIL }) throw BadRequestException("A prerequisite workflow must finish before the pending external action; use a design without email steps")
        }
        val row = id?.let { bindings.findById(it).orElse(null) ?: throw NotFoundException("Binding unavailable") } ?: WorkflowActionBinding()
        if (row.diagramWorkflowId != null) throw BadRequestException("Edit this trigger in Diagram & steps, then publish the workflow")
        if (id != null && row.revision != request.revision) throw ConflictException("This binding changed. Reload before saving.")
        row.selector=request.selector; row.workflowId=request.workflowId; row.enabled=request.enabled
        row.contextField=request.contextField; row.contextEquals=request.contextEquals
        val saved=bindings.saveAndFlush(row)
        audit.record(email,"WORKFLOW_BINDING_SAVED","WORKFLOW_BINDING",saved.id.toString())
        return view(saved)
    }
    private fun view(row: WorkflowActionBinding) = WorkflowBindingView(row.id,row.selector,row.workflowId,row.enabled,row.contextField,row.contextEquals,row.revision,row.diagramWorkflowId!=null)
}
