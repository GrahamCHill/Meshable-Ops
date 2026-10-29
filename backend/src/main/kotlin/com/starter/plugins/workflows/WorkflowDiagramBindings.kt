package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import com.starter.service.AuditLogService
import org.springframework.stereotype.Service

/** Design and trigger policy are saved in the same authoring transaction. */
@Service
class WorkflowDiagramBindings(private val bindings: WorkflowActionBindingRepository, private val catalogue: WorkflowActionCatalogue,
    private val audit: AuditLogService) {
    fun sync(email: String, definition: WorkflowDefinition, graph: WorkflowGraph) {
        val start=graph.nodes.singleOrNull { it.kind==BlockKind.START }
        if (start?.actionSelector?.isNotEmpty()==true && catalogue.all().none { it.id==start.actionSelector })
            throw BadRequestException("Choose an installed plugin action for this trigger")
        // Draft/archive pauses the source. Keep its last policy enforced until an explicit published replacement.
        if (!definition.published) return
        val previous=bindings.findByDiagramWorkflowId(definition.id)
        if (start?.actionSelector.isNullOrEmpty()) {
            if (previous!=null) { previous.enabled=false;bindings.saveAndFlush(previous);audit.record(email,"WORKFLOW_TRIGGER_REMOVED","WORKFLOW",definition.id.toString()) }
            return
        }
        val trigger=start!!
        val row=previous ?: WorkflowActionBinding(workflowId=definition.id,diagramWorkflowId=definition.id)
        row.selector=trigger.actionSelector;row.contextField=trigger.contextField;row.contextEquals=trigger.contextEquals;row.enabled=true
        bindings.saveAndFlush(row)
        audit.record(email,"WORKFLOW_TRIGGER_SAVED","WORKFLOW",definition.id.toString())
    }
}
