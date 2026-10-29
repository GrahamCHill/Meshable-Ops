package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkflowApprovalTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private val initiator=UUID.randomUUID()
    private val reviewer=UUID.randomUUID()
    private fun run(block: WorkflowBlock) = WorkflowRun(ownerId=initiator,currentNodeId=block.id,graphJson=mapper.writeValueAsString(
        WorkflowGraph(listOf(block,WorkflowBlock("end",BlockKind.END,"Allow")),listOf(WorkflowEdge("out",block.id,"end")))))
    @Test fun `different reviewer rule audit notes and actual actor are enforced server side`() {
        val row=run(WorkflowBlock("review",BlockKind.REVIEW,"Approval",allowInitiatorReview=false,reviewerIds=setOf(reviewer),auditRequired=true))
        val request=WorkflowAdvanceRequest(0,"review",WorkflowAction.REVIEWED,note="Verified")
        assertThatThrownBy {runtime.advance(row,request,initiator)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {runtime.advance(row,request,UUID.randomUUID())}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {runtime.advance(row,request.copy(note=""),reviewer)}.isInstanceOf(BadRequestException::class.java)
        runtime.advance(row,request,reviewer)
        assertThat(runtime.history(row).single().actorId).isEqualTo(reviewer)
        assertThat(runtime.history(row).single().audited).isTrue()
    }
    @Test fun `initiator can approve when enabled and a reviewer can reject without executing future steps`() {
        val own=run(WorkflowBlock("review",BlockKind.REVIEW,"Approval"))
        runtime.advance(own,WorkflowAdvanceRequest(0,"review",WorkflowAction.REVIEWED),initiator)
        assertThat(own.currentNodeId).isEqualTo("end")
        val denied=run(WorkflowBlock("review",BlockKind.REVIEW,"Approval",reviewerIds=setOf(reviewer),allowInitiatorReview=false))
        runtime.advance(denied,WorkflowAdvanceRequest(0,"review",WorkflowAction.REJECT,note="Wrong destination"),reviewer)
        assertThat(denied.status).isEqualTo(WorkflowRunStatus.REJECTED)
        assertThat(denied.currentNodeId).isNull()
        assertThat(runtime.history(denied).single().outcome).isEqualTo("REJECTED")
    }
    @Test fun `trusted condition data cannot be replaced by client input and deny ends never authorize actions`() {
        val condition=WorkflowBlock("check",BlockKind.CONDITION,"Check",field="trigger.input.amount",operator=ConditionOperator.GREATER_THAN,expected="100")
        val end=WorkflowBlock("deny",BlockKind.END,"Rejected",endOutcome=WorkflowEndOutcome.DENY)
        val graph=WorkflowGraph(listOf(condition,end,WorkflowBlock("allow",BlockKind.END,"Allow")),listOf(WorkflowEdge("no","check","deny",Branch.FALSE),WorkflowEdge("yes","check","allow",Branch.TRUE)))
        val row=WorkflowRun(ownerId=initiator,currentNodeId="check",graphJson=mapper.writeValueAsString(graph),valuesJson="""{"trigger.input.amount":"10"}""")
        runtime.advance(row,WorkflowAdvanceRequest(0,"check",WorkflowAction.EVALUATE,value="999"),initiator)
        assertThat(row.currentNodeId).isEqualTo("deny")
        runtime.advance(row,WorkflowAdvanceRequest(0,"deny"),initiator)
        assertThat(row.status).isEqualTo(WorkflowRunStatus.REJECTED)
    }
    @Test fun `code results feed server conditions and audit steps require notes`() {
        val code=WorkflowBlock("code",BlockKind.CODE,"Calculate",code="def run(context):\n    return {\"ready\": True}")
        val row=run(code)
        runtime.advance(row,WorkflowAdvanceRequest(0,"code"),initiator)
        assertThat(runtime.values(row)).containsEntry("ready","True")
        val audit=run(WorkflowBlock("audit",BlockKind.AUDIT,"Record decision"))
        assertThatThrownBy {runtime.advance(audit,WorkflowAdvanceRequest(0,"audit"),initiator)}.isInstanceOf(BadRequestException::class.java)
    }
}
