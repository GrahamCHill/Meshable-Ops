package com.starter.workflow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WorkflowRuntimeTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private val graph=WorkflowGraph(listOf(
        WorkflowBlock("start",BlockKind.START,"Start"),
        WorkflowBlock("condition",BlockKind.CONDITION,"Check amount",field="quote.total",operator=ConditionOperator.GREATER_THAN,expected="100"),
        WorkflowBlock("review",BlockKind.REVIEW,"Review quote"),WorkflowBlock("end",BlockKind.END,"End")
    ),listOf(WorkflowEdge("a","start","condition"),WorkflowEdge("b","condition","review",Branch.TRUE),WorkflowEdge("c","condition","end",Branch.FALSE),WorkflowEdge("d","review","end")))
    private fun run() = WorkflowRun(graphJson=mapper.writeValueAsString(graph),currentNodeId="start")
    private fun advance(row: WorkflowRun,action: WorkflowAction=WorkflowAction.CONTINUE,value: String="",note: String="") = runtime.advance(row,WorkflowAdvanceRequest(row.revision,row.currentNodeId!!,action,value,note))
    @Test fun `guided progress evaluates a true branch records review and ends without executing future steps`() {
        val row=run();advance(row)
        assertThat(row.currentNodeId).isEqualTo("condition")
        advance(row,WorkflowAction.EVALUATE,"100.01")
        assertThat(row.currentNodeId).isEqualTo("review");assertThat(runtime.values(row)).containsEntry("quote.total","100.01")
        assertThatThrownBy {advance(row)}.isInstanceOf(BadRequestException::class.java)
        advance(row,WorkflowAction.REVIEWED,note="Checked against quote")
        assertThat(row.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
        advance(row)
        assertThat(row.status).isEqualTo(WorkflowRunStatus.COMPLETED);assertThat(row.currentNodeId).isNull()
        assertThat(runtime.history(row).map {it.outcome}).containsExactly("COMPLETED","TRUE","REVIEWED","COMPLETED")
        assertThat(runtime.history(row)[2].note).isEqualTo("Checked against quote")
        assertThatThrownBy {runtime.matches(row,row.revision)}.isInstanceOf(ConflictException::class.java)
    }
    @Test fun `false branches skip the review and numeric input cannot become code or huge decimals`() {
        val row=run();advance(row)
        listOf("NaN","1e999999","Infinity","total > 100","9".repeat(31)).forEach {value ->
            assertThatThrownBy {advance(row,WorkflowAction.EVALUATE,value)}.isInstanceOf(BadRequestException::class.java)
            assertThat(row.currentNodeId).isEqualTo("condition")
        }
        advance(row,WorkflowAction.EVALUATE,"100.00")
        assertThat(row.currentNodeId).isEqualTo("end");assertThat(runtime.history(row).last().outcome).isEqualTo("FALSE")
    }
    @Test fun `stale revisions node jumps and skipped condition acknowledgments are rejected`() {
        val row=run()
        assertThatThrownBy {runtime.advance(row,WorkflowAdvanceRequest(1,"start"))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {runtime.advance(row,WorkflowAdvanceRequest(0,"end"))}.isInstanceOf(ConflictException::class.java)
        advance(row)
        assertThatThrownBy {advance(row)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `email needs an explicit delivery confirmation and in-flight attempts cannot be skipped`() {
        val email=WorkflowBlock("email",BlockKind.EMAIL,"Send",documentId=UUID.randomUUID())
        val row=WorkflowRun(graphJson=mapper.writeValueAsString(WorkflowGraph(listOf(email,graph.nodes.last()),listOf(WorkflowEdge("out","email","end")))),currentNodeId="email",emailState=WorkflowEmailState.SENDING,emailAttemptAt=Instant.now())
        assertThatThrownBy {advance(row,WorkflowAction.CONFIRM_EMAIL,note="Checked")}.isInstanceOf(ConflictException::class.java)
        row.emailAttemptAt=Instant.now().minusSeconds(121)
        assertThatThrownBy {advance(row,WorkflowAction.CONFIRM_EMAIL)}.isInstanceOf(BadRequestException::class.java)
        advance(row,WorkflowAction.CONFIRM_EMAIL,note="Provider confirmed receipt, handled manually")
        assertThat(row.currentNodeId).isEqualTo("end");assertThat(row.emailState).isEqualTo(WorkflowEmailState.NONE)
        assertThat(runtime.history(row).single().outcome).isEqualTo("DELIVERY_CONFIRMED_MANUALLY")
    }
    @Test fun `comparisons are literal and document replacements do not change the design snapshot`() {
        val block=graph.nodes[1]
        assertThat(runtime.evaluate(block.copy(operator=ConditionOperator.EQUALS,expected=" Done "),"Done")).isTrue()
        assertThat(runtime.evaluate(block.copy(operator=ConditionOperator.NOT_EQUALS,expected="Done"),"done")).isTrue()
        assertThat(runtime.evaluate(block.copy(operator=ConditionOperator.CONTAINS,expected="OK"),"Checked OK")).isTrue()
        assertThat(runtime.evaluate(block.copy(operator=ConditionOperator.IS_SET),"  ")).isFalse()
        val original=UUID.randomUUID();val selected=UUID.randomUUID();val row=run()
        row.documentOverridesJson=mapper.writeValueAsString(mapOf(original to selected))
        val document=WorkflowBlock("doc",BlockKind.DOCUMENT,"PDF",documentId=original)
        assertThat(runtime.effective(row,document).documentId).isEqualTo(selected)
        assertThat(document.documentId).isEqualTo(original)
    }
}
