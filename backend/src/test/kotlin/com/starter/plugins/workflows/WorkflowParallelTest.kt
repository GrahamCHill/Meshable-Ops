package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkflowParallelTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private fun edge(source: String,target: String,branch: Branch=Branch.NEXT)=WorkflowEdge("$source-$target-${branch.name}",source,target,branch)
    private fun run(graph: WorkflowGraph)=WorkflowRun(graphJson=mapper.writeValueAsString(graph)).also {runtime.initialize(it)}
    private fun next(run: WorkflowRun,id: String,note: String="")=runtime.advance(run,WorkflowAdvanceRequest(run.revision,id,note=note),run.ownerId)
    @Test fun `one output activates every target and the join survives reloading before the second branch completes`() {
        val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),WorkflowBlock("left",BlockKind.AUDIT,"Left"),
            WorkflowBlock("right",BlockKind.AUDIT,"Right"),WorkflowBlock("join",BlockKind.JOIN,"Join"),WorkflowBlock("end",BlockKind.END,"End")),
            listOf(edge("start","left"),edge("start","right"),edge("left","join"),edge("right","join"),edge("join","end")))
        WorkflowValidation().check(graph,emptyList(),true)
        val run=run(graph);next(run,"start")
        assertThat(runtime.execution(run).active).containsExactlyInAnyOrder("left","right")
        next(run,"left","Checked left")
        assertThat(runtime.waiting(run).single().received).isEqualTo(1)
        assertThatThrownBy {next(run,"left","Duplicate")}.isInstanceOf(ConflictException::class.java)
        val reloaded=WorkflowRun(ownerId=run.ownerId,graphJson=run.graphJson,currentNodeId=run.currentNodeId,executionJson=run.executionJson,historyJson=run.historyJson)
        next(reloaded,"right","Checked right")
        assertThat(runtime.execution(reloaded).active).containsExactly("end")
        assertThat(runtime.history(reloaded).count {it.nodeId=="join"}).isEqualTo(1)
        assertThat(reloaded.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
        next(reloaded,"end");assertThat(reloaded.status).isEqualTo(WorkflowRunStatus.COMPLETED)
    }
    private fun webhookGraph(rule: JoinRule=JoinRule.PASS,inputs: JoinInputs=JoinInputs.ALL,audit: Boolean=false): WorkflowGraph {
        val branches=if(rule==JoinRule.PASS)listOf(edge("join","end")) else listOf(edge("join","end",Branch.TRUE),edge("join","deny",Branch.FALSE))
        return WorkflowGraph(listOf(WorkflowBlock("manual",BlockKind.START,"Manual"),WorkflowBlock("hook",BlockKind.WEBHOOK,"Callback",webhookEndpointId=UUID.randomUUID()),
            WorkflowBlock("join",BlockKind.JOIN,"Both inputs",joinInputs=inputs,joinRule=rule,auditRequired=audit),
            WorkflowBlock("end",BlockKind.END,"Allow"),WorkflowBlock("deny",BlockKind.END,"Deny",endOutcome=WorkflowEndOutcome.DENY)),
            listOf(edge("manual","join"),edge("hook","join"))+branches)
    }
    @Test fun `manual and webhook triggers join in either arrival order and cannot be spoofed through advance`() {
        for(webhookFirst in listOf(true,false)) {
            val run=run(webhookGraph())
            assertThatThrownBy {next(run,"hook")}.isInstanceOf(ForbiddenException::class.java)
            if(webhookFirst)runtime.signalWebhook(run,"hook",true,"Signed callback") else next(run,"manual")
            assertThat(runtime.waiting(run).any {it.kind==BlockKind.JOIN && it.received==1}).isTrue()
            assertThat(runtime.execution(run).active).doesNotContain("end")
            if(webhookFirst)next(run,"manual") else runtime.signalWebhook(run,"hook",true,"Signed callback")
            assertThat(runtime.execution(run).active).containsExactly("end")
        }
    }
    @Test fun `join waits for every input before all-true or any-true selects its true false output`() {
        for(rule in listOf(JoinRule.ALL_TRUE,JoinRule.ANY_TRUE)) {
            val run=run(webhookGraph(rule))
            runtime.signalWebhook(run,"hook",false,"False callback")
            assertThat(runtime.execution(run).active).containsExactly("manual")
            next(run,"manual")
            assertThat(runtime.execution(run).active).containsExactly(if(rule==JoinRule.ALL_TRUE)"deny" else "end")
        }
    }
    @Test fun `an audit-required join holds after all arrivals until its note is recorded`() {
        val run=run(webhookGraph(audit=true));next(run,"manual");runtime.signalWebhook(run,"hook",true,"Signed callback")
        assertThat(runtime.execution(run).active).containsExactly("join")
        assertThatThrownBy {next(run,"join")}.isInstanceOf(BadRequestException::class.java)
        next(run,"join","Both prerequisites checked")
        assertThat(runtime.execution(run).active).containsExactly("end")
    }
    @Test fun `first-arrival joins fire only once while end nodes wait for remaining active branches`() {
        val run=run(webhookGraph(inputs=JoinInputs.ANY));next(run,"manual");next(run,"end")
        assertThat(run.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
        runtime.signalWebhook(run,"hook",true,"Later callback")
        assertThat(run.status).isEqualTo(WorkflowRunStatus.COMPLETED)
        assertThat(runtime.history(run).count {it.kind==BlockKind.JOIN}).isEqualTo(1)
    }
    @Test fun `only chosen conditional outputs fan out and skipped edges do not satisfy an all-input join`() {
        val condition=WorkflowBlock("condition",BlockKind.CONDITION,"Decision",field="approved",expected="yes")
        val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),condition,WorkflowBlock("a",BlockKind.END,"A"),WorkflowBlock("b",BlockKind.END,"B"),WorkflowBlock("c",BlockKind.END,"C")),
            listOf(edge("start","condition"),edge("condition","a",Branch.TRUE),edge("condition","b",Branch.TRUE),edge("condition","c",Branch.FALSE)))
        WorkflowValidation().check(graph,emptyList(),true)
        val run=run(graph);next(run,"start");runtime.advance(run,WorkflowAdvanceRequest(0,"condition",WorkflowAction.EVALUATE,"yes"))
        assertThat(runtime.execution(run).active).containsExactlyInAnyOrder("a","b")
        next(run,"a");assertThat(run.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
        next(run,"b");assertThat(run.status).isEqualTo(WorkflowRunStatus.COMPLETED)
    }
    @Test fun `legacy runs with no execution state resume their current step without restarting roots`() {
        val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),WorkflowBlock("audit",BlockKind.AUDIT,"Audit"),WorkflowBlock("end",BlockKind.END,"End")),listOf(edge("start","audit"),edge("audit","end")))
        val run=WorkflowRun(graphJson=mapper.writeValueAsString(graph),currentNodeId="audit")
        next(run,"audit","Resumed legacy step")
        assertThat(runtime.execution(run).active).containsExactly("end")
        assertThat(runtime.execution(run).active).doesNotContain("start")
    }
    @Test fun `a skipped conditional edge cannot silently satisfy an all-input barrier`() {
        val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),WorkflowBlock("condition",BlockKind.CONDITION,"Decision",field="value",expected="yes"),
            WorkflowBlock("join",BlockKind.JOIN,"Strict barrier"),WorkflowBlock("end",BlockKind.END,"End")),
            listOf(edge("start","condition"),edge("condition","join",Branch.TRUE),edge("condition","join",Branch.FALSE),edge("join","end")))
        val run=run(graph);next(run,"start");runtime.advance(run,WorkflowAdvanceRequest(0,"condition",WorkflowAction.EVALUATE,"yes"))
        assertThat(runtime.execution(run).active).isEmpty()
        assertThat(runtime.waiting(run).single().received).isEqualTo(1)
        assertThat(run.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
    }
    @Test fun `webhook data is trusted by conditions and code cannot forge its namespace`() {
        val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),WorkflowBlock("condition",BlockKind.CONDITION,"Callback ready?",field="webhook.hook.status",expected="ready"),
            WorkflowBlock("yes",BlockKind.END,"Yes"),WorkflowBlock("no",BlockKind.END,"No")),
            listOf(edge("start","condition"),edge("condition","yes",Branch.TRUE),edge("condition","no",Branch.FALSE)))
        val run=run(graph);run.valuesJson="""{"webhook.hook.status":"ready"}""";next(run,"start")
        runtime.advance(run,WorkflowAdvanceRequest(0,"condition",WorkflowAction.EVALUATE,"spoofed"))
        assertThat(runtime.execution(run).active).containsExactly("yes")
        assertThatThrownBy {WorkflowCode.execute("def run(context):\n    return {\"webhook.hook.status\": \"forged\"}",emptyMap())}.isInstanceOf(BadRequestException::class.java)
    }
}
