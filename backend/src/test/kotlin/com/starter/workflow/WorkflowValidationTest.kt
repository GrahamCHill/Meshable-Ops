package com.starter.workflow

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkflowValidationTest {
    private val validation = WorkflowValidation()
    private val start = WorkflowBlock("start", BlockKind.START, "Start")
    private val end = WorkflowBlock("end", BlockKind.END, "End")
    @Test fun `drafts can be incomplete and ready diagrams need connected acyclic complete paths`() {
        validation.check(WorkflowGraph(listOf(start,end), emptyList()), emptyList(), false)
        assertThatThrownBy { validation.check(WorkflowGraph(listOf(start,end), emptyList()), emptyList(), true) }.isInstanceOf(BadRequestException::class.java)
        validation.check(WorkflowGraph(listOf(start,end), listOf(WorkflowEdge("edge", "start", "end"))), emptyList(), true)
    }
    @Test fun `cycles duplicate outputs unknown edges and nonfinite coordinates are rejected`() {
        val a = WorkflowBlock("a", BlockKind.REVIEW, "Review"); val b = WorkflowBlock("b", BlockKind.REVIEW, "Review again")
        listOf(
            WorkflowGraph(listOf(a,b), listOf(WorkflowEdge("one", "a", "b"), WorkflowEdge("two", "b", "a"))),
            WorkflowGraph(listOf(start,end,a), listOf(WorkflowEdge("one", "start", "a"), WorkflowEdge("two", "start", "end"))),
            WorkflowGraph(listOf(start,end), listOf(WorkflowEdge("one", "missing", "end"))),
            WorkflowGraph(listOf(start.copy(x = Double.NaN)), emptyList()),
            WorkflowGraph(listOf(start,start), emptyList()),
        ).forEach { assertThatThrownBy { validation.check(it, emptyList(), false) }.isInstanceOf(BadRequestException::class.java) }
    }
    @Test fun `condition requires true false branches and every block must be reachable`() {
        val condition = WorkflowBlock("condition", BlockKind.CONDITION, "Large quote", field = "total", operator = ConditionOperator.GREATER_THAN, expected = "100")
        val nodes = listOf(start,condition,end)
        val paths = listOf(WorkflowEdge("a", "start", "condition"), WorkflowEdge("b", "condition", "end", Branch.TRUE), WorkflowEdge("c", "condition", "end", Branch.FALSE))
        validation.check(WorkflowGraph(nodes, paths), emptyList(), true)
        assertThatThrownBy { validation.check(WorkflowGraph(nodes, paths.dropLast(1)), emptyList(), true) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { validation.check(WorkflowGraph(nodes + end.copy(id = "stray"), paths), emptyList(), true) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("all blocks")
    }
    @Test fun `document email blocks need selected attachments and ready email details`() {
        val id = UUID.randomUUID(); val doc = WorkflowBlock("doc", BlockKind.DOCUMENT, "Quote", documentId = id)
        assertThatThrownBy { validation.check(WorkflowGraph(listOf(doc), emptyList()), emptyList(), false) }.isInstanceOf(BadRequestException::class.java)
        val email = WorkflowBlock("mail", BlockKind.EMAIL, "Send quote", documentId = id)
        val graph = WorkflowGraph(listOf(start,email,end), listOf(WorkflowEdge("a", "start", "mail"), WorkflowEdge("b", "mail", "end")))
        assertThatThrownBy { validation.check(graph, listOf(id), true) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("recipient")
        validation.check(graph.copy(nodes = listOf(start,email.copy(recipient = "reader@example.test", subject = "Your quote"),end)), listOf(id), true)
    }
}
