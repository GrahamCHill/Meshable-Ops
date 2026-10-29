package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WorkflowValidation {
    fun check(graph: WorkflowGraph, documents: List<UUID>, ready: Boolean) {
        fun bad(message: String): Nothing = throw BadRequestException(message)
        if (graph.nodes.size > 50 || graph.edges.size > 100 || documents.size > 50 || documents.distinct().size != documents.size) bad("Use at most 50 blocks, 100 connections, and 50 unique documents")
        val nodes = graph.nodes.associateBy { it.id }
        if (nodes.size != graph.nodes.size || graph.edges.map { it.id }.distinct().size != graph.edges.size) bad("Block and connection IDs must be unique")
        if (graph.nodes.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in -10000.0..10000.0 || it.y !in -10000.0..10000.0 }) bad("Block positions are outside the supported canvas")
        if (graph.nodes.any { it.documentId != null && it.documentId !in documents }) bad("Select block documents from the workflow attachments")
        if (graph.nodes.any { it.field.isNotBlank() && !Regex("[A-Za-z][A-Za-z0-9_.-]{0,119}").matches(it.field) }) bad("Use a named data field in conditions")
        if (graph.nodes.any { it.field.split('.').any { part -> part in setOf("__proto__","prototype","constructor") } }) bad("Use a business data-field name")
        graph.nodes.forEach { node ->
            if (node.actionSelector.isNotEmpty() && node.kind != BlockKind.START) bad("Plugin triggers belong on the start step")
            if (node.actionSelector.length > 300 || node.contextField.length > 120 || node.contextEquals.length > 500) bad("Trigger configuration is too long")
            if (node.contextField.isNotEmpty() && (!node.contextField.startsWith("trigger.") || node.contextEquals.isBlank())) bad("Choose a trigger field and matching value")
            if (node.actionSelector.isNotEmpty() && graph.nodes.any { it.kind == BlockKind.EMAIL }) bad("Action-triggered workflows must finish before delivery; remove email steps")
            if (node.reviewerIds.size > 20) bad("Choose at most 20 reviewers")
            if (node.code.length > 8192) bad("Code steps support up to 8192 characters")
            if (ready && node.kind == BlockKind.CODE && node.code.isNotBlank()) WorkflowCode.validate(node.code)
            if (ready && node.kind == BlockKind.CODE && node.code.isBlank()) bad("Code steps need a run(context) module")
            if (ready && node.kind == BlockKind.REVIEW && !node.allowInitiatorReview && node.reviewerIds.isEmpty()) bad("Choose reviewers when a different approver is required")
        }
        if (graph.edges.any { it.source !in nodes || it.target !in nodes || it.source == it.target }) bad("Connect two different existing blocks")
        if (graph.edges.map { Triple(it.source,it.target,it.branch) }.distinct().size != graph.edges.size) bad("Do not duplicate the same connection")
        val outgoing = graph.edges.groupBy { it.source }
        if (graph.edges.any { edge -> nodes.getValue(edge.source).let { it.kind == BlockKind.END || if (it.kind == BlockKind.CONDITION || it.kind==BlockKind.JOIN && it.joinRule!=JoinRule.PASS) edge.branch == Branch.NEXT else edge.branch != Branch.NEXT } }) bad("Use true/false outputs for conditions and next outputs for other blocks")
        if (graph.edges.any { nodes.getValue(it.target).kind in setOf(BlockKind.START,BlockKind.WEBHOOK) }) bad("Manual and webhook triggers cannot have incoming connections")
        val done = mutableSetOf<String>()
        fun visit(id: String, path: Set<String>) {
            if (id in path) bad("Loops are not supported in this first workflow version")
            if (id in done) return
            outgoing[id].orEmpty().forEach { visit(it.target, path + id) }; done += id
        }
        nodes.keys.forEach { visit(it, emptySet()) }
        if(graph.nodes.count {it.actionSelector.isNotEmpty()}>1)bad("Use at most one connected plugin action per workflow")
        graph.nodes.filter {it.kind==BlockKind.WEBHOOK}.forEach {node ->
            val fields=node.webhookFields+listOfNotNull(node.webhookBooleanField.takeIf {it.isNotBlank()})
            if(node.webhookFields.size>20 || node.webhookFields.distinct().size!=node.webhookFields.size || fields.any {
                !it.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,79}")) || "webhook.${node.id}.$it".length>120 ||
                it.lowercase() in setOf("__proto__","prototype","constructor","password","secret","token","authorization","access_token","accesstoken","apikey","api_key")
            })bad("Choose up to 20 unique named non-secret webhook data fields; each context key must fit 120 characters")
            if(ready && (node.webhookEndpointId==null || !node.webhookEvent.matches(Regex("[A-Za-z0-9._-]{1,80}")) || node.webhookStartsRun && node.webhookOwnerId==null))bad("Webhook triggers need an incoming endpoint, event, and a run owner when starting new runs")
        }
        if (!ready) return
        val start = graph.nodes.filter { it.kind in setOf(BlockKind.START,BlockKind.WEBHOOK) }
        if (start.isEmpty() || graph.nodes.none { it.kind == BlockKind.END }) bad("A ready workflow needs a manual or webhook trigger and at least one end")
        graph.nodes.forEach { node ->
            val paths = outgoing[node.id].orEmpty()
            when (node.kind) {
                BlockKind.END -> Unit
                BlockKind.CONDITION -> if (paths.map { it.branch }.toSet() != setOf(Branch.TRUE, Branch.FALSE) || node.field.isBlank()) bad("Conditions need a data field and both true and false paths")
                BlockKind.JOIN -> {
                    if(graph.edges.count {it.target==node.id}<2)bad("A join needs at least two connected inputs")
                    val expected=if(node.joinRule==JoinRule.PASS)setOf(Branch.NEXT) else setOf(Branch.TRUE,Branch.FALSE)
                    if(paths.map {it.branch}.toSet()!=expected)bad("Connect the join's continue output, or both true and false outputs")
                }
                else -> if (paths.isEmpty()) bad("Connect every non-end block to its next step")
            }
            if (node.kind == BlockKind.CONDITION && node.operator == ConditionOperator.GREATER_THAN && !node.expected.trim().matches(Regex("[+-]?[0-9]{1,30}(\\.[0-9]{1,12})?"))) bad("Numeric conditions need a decimal comparison value")
            if (node.kind in setOf(BlockKind.DOCUMENT, BlockKind.EMAIL) && node.documentId == null) bad("Document and email blocks need an attached document")
            if (node.kind == BlockKind.EMAIL && (node.recipient.isBlank() || node.subject.isBlank())) bad("Email blocks need a recipient and subject")
        }
        val reachable = mutableSetOf<String>()
        fun reach(id: String) { if (!reachable.add(id)) return; outgoing[id].orEmpty().forEach { reach(it.target) } }
        start.forEach {reach(it.id)}
        if (reachable.size != nodes.size) bad("Connect all blocks to a trigger")
    }
}
