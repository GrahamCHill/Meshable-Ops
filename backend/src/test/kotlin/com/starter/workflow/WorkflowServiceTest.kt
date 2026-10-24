package com.starter.workflow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.documents.*
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.security.RateLimits
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*

class WorkflowServiceTest {
    private val definitions=mock(WorkflowRepository::class.java)
    private val rows=mock(WorkflowRunRepository::class.java)
    private val access=mock(DocumentAccess::class.java)
    private val documents=mock(GeneratedDocumentRepository::class.java)
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private val guard=WorkflowRunAccess(rows,definitions,access,documents,runtime)
    private val runner=WorkflowRunService(definitions,rows,guard,access,documents,runtime,WorkflowValidation(),mapper,mock(RateLimits::class.java))
    private val author=WorkflowService(definitions,access,WorkflowValidation(),mapper)
    private val member=User(email="member@example.test");private val admin=User(email="admin@example.test",role="ADMIN")
    private val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start"),WorkflowBlock("end",BlockKind.END,"End")),listOf(WorkflowEdge("edge","start","end")))
    private val source=WorkflowDefinition(ownerId=admin.id,name="Quote approval",state=WorkflowState.READY,published=true,graphJson=mapper.writeValueAsString(graph),revision=4)
    init {
        `when`(access.actor(member.email)).thenReturn(member);`when`(access.actor(admin.email)).thenReturn(admin)
        `when`(access.admin(admin.email)).thenReturn(admin);`when`(access.admin(member.email)).thenThrow(ForbiddenException("Admin required"))
        `when`(definitions.findById(source.id)).thenReturn(Optional.of(source))
        `when`(definitions.saveAndFlush(any(WorkflowDefinition::class.java))).thenAnswer {it.getArgument(0)}
        `when`(rows.saveAndFlush(any(WorkflowRun::class.java))).thenAnswer {it.getArgument(0)}
    }
    @Test fun `members cannot author or list designs and ready is private until explicitly published`() {
        val request=WorkflowRequest("Private design",WorkflowState.READY,graph)
        assertThatThrownBy {author.save(member.email,null,request)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {author.list(member.email)}.isInstanceOf(ForbiddenException::class.java)
        assertThat(author.save(admin.email,null,request).published).isFalse()
        assertThat(author.save(admin.email,null,request.copy(published=true)).published).isTrue()
        assertThat(author.save(admin.email,source.id,request.copy(state=WorkflowState.DRAFT,revision=4,published=true)).published).isFalse()
    }
    @Test fun `members start published ready workflows only and admins can preview private ready designs`() {
        source.published=false
        assertThatThrownBy {runner.start(member.email,source.id)}.isInstanceOf(NotFoundException::class.java)
        assertThat(runner.start(admin.email,source.id).current?.kind).isEqualTo(BlockKind.START)
        source.state=WorkflowState.DRAFT
        assertThatThrownBy {runner.start(admin.email,source.id)}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `running steps use an immutable design revision after later edits`() {
        val view=runner.start(member.email,source.id)
        val captured=org.mockito.ArgumentCaptor.forClass(WorkflowRun::class.java)
        verify(rows).saveAndFlush(captured.capture());val row=captured.value
        `when`(rows.findById(view.id)).thenReturn(Optional.of(row));`when`(rows.locked(view.id)).thenReturn(Optional.of(row))
        source.graphJson=mapper.writeValueAsString(graph.copy(nodes=graph.nodes.map {it.copy(label="Changed later")}));source.revision=5
        val updated=runner.advance(member.email,view.id,WorkflowAdvanceRequest(view.revision,"start"))
        assertThat(updated.workflowRevision).isEqualTo(4);assertThat(updated.current?.label).isEqualTo("End")
        assertThat(updated.history.single().label).isEqualTo("Start")
    }
    @Test fun `document replacements use the ordinary library ownership gate and cannot change another run`() {
        val documentId=UUID.randomUUID();val ownId=UUID.randomUUID()
        val doc=WorkflowBlock("doc",BlockKind.DOCUMENT,"PDF",documentId=documentId)
        val row=WorkflowRun(ownerId=member.id,workflowId=source.id,graphJson=mapper.writeValueAsString(graph.copy(nodes=listOf(doc))),currentNodeId="doc")
        `when`(rows.locked(row.id)).thenReturn(Optional.of(row))
        `when`(access.document(member,ownId)).thenThrow(NotFoundException("Not yours"))
        assertThatThrownBy {runner.useDocument(member.email,row.id,WorkflowDocumentRequest(0,"doc",ownId))}.isInstanceOf(NotFoundException::class.java)
        verify(rows,never()).saveAndFlush(any(WorkflowRun::class.java))
        doReturn(GeneratedDocument(id=ownId,ownerId=member.id)).`when`(access).document(member,ownId)
        runner.useDocument(member.email,row.id,WorkflowDocumentRequest(0,"doc",ownId))
        assertThat(runtime.overrides(row)).containsEntry(documentId,ownId)
        assertThatThrownBy {runner.cancel(admin.email,row.id,0)}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `unpublished runs retain history and can be cancelled without granting documents or progression`() {
        val row=WorkflowRun(ownerId=member.id,workflowId=source.id,graphJson=source.graphJson,currentNodeId="start")
        `when`(rows.locked(row.id)).thenReturn(Optional.of(row));source.published=false
        assertThatThrownBy {runner.advance(member.email,row.id,WorkflowAdvanceRequest(0,"start"))}.isInstanceOf(ForbiddenException::class.java)
        assertThat(runner.cancel(member.email,row.id,0).status).isEqualTo(WorkflowRunStatus.CANCELLED)
    }
}
