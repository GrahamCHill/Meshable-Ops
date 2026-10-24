package com.starter.workflow

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
        if (graph.edges.any { it.source !in nodes || it.target !in nodes || it.source == it.target }) bad("Connect two different existing blocks")
        if (graph.edges.map { it.source to it.branch }.distinct().size != graph.edges.size) bad("Each output can connect to one next block")
        val outgoing = graph.edges.groupBy { it.source }
        if (graph.edges.any { edge -> nodes.getValue(edge.source).let { it.kind == BlockKind.END || if (it.kind == BlockKind.CONDITION) edge.branch == Branch.NEXT else edge.branch != Branch.NEXT } }) bad("Use true/false outputs for conditions and next outputs for other blocks")
        if (graph.edges.any { nodes.getValue(it.target).kind == BlockKind.START }) bad("Start blocks cannot have incoming connections")
        val done = mutableSetOf<String>()
        fun visit(id: String, path: Set<String>) {
            if (id in path) bad("Loops are not supported in this first workflow version")
            if (id in done) return
            outgoing[id].orEmpty().forEach { visit(it.target, path + id) }; done += id
        }
        nodes.keys.forEach { visit(it, emptySet()) }
        if (!ready) return
        val start = graph.nodes.filter { it.kind == BlockKind.START }
        if (start.size != 1 || graph.nodes.none { it.kind == BlockKind.END }) bad("A ready workflow needs one start and at least one end")
        graph.nodes.forEach { node ->
            val paths = outgoing[node.id].orEmpty()
            when (node.kind) {
                BlockKind.END -> Unit
                BlockKind.CONDITION -> if (paths.map { it.branch }.toSet() != setOf(Branch.TRUE, Branch.FALSE) || node.field.isBlank()) bad("Conditions need a data field and both true and false paths")
                else -> if (paths.size != 1) bad("Connect every non-end block to its next step")
            }
            if (node.kind == BlockKind.CONDITION && node.operator == ConditionOperator.GREATER_THAN && !node.expected.trim().matches(Regex("[+-]?[0-9]{1,30}(\\.[0-9]{1,12})?"))) bad("Numeric conditions need a decimal comparison value")
            if (node.kind in setOf(BlockKind.DOCUMENT, BlockKind.EMAIL) && node.documentId == null) bad("Document and email blocks need an attached document")
            if (node.kind == BlockKind.EMAIL && (node.recipient.isBlank() || node.subject.isBlank())) bad("Email blocks need a recipient and subject")
        }
        val reachable = mutableSetOf<String>()
        fun reach(id: String) { if (!reachable.add(id)) return; outgoing[id].orEmpty().forEach { reach(it.target) } }
        reach(start.single().id)
        if (reachable.size != nodes.size) bad("Connect all blocks to the start")
    }
}
