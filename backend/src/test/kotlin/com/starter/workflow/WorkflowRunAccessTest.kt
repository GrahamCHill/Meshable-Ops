package com.starter.workflow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.documents.*
import com.starter.exception.*
import com.starter.model.postgres.User
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.*

class WorkflowRunAccessTest {
    private val runs=mock(WorkflowRunRepository::class.java)
    private val definitions=mock(WorkflowRepository::class.java)
    private val access=mock(DocumentAccess::class.java)
    private val documents=mock(GeneratedDocumentRepository::class.java)
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private val guard=WorkflowRunAccess(runs,definitions,access,documents,runtime)
    private val member=User(email="member@example.test")
    private val first=GeneratedDocument();private val future=GeneratedDocument()
    private val graph=WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Start",documentId=future.id),WorkflowBlock("doc",BlockKind.DOCUMENT,"PDF",documentId=first.id),WorkflowBlock("future",BlockKind.DOCUMENT,"Other branch",documentId=future.id)),emptyList())
    private val definition=WorkflowDefinition(state=WorkflowState.READY,published=true)
    private val row=WorkflowRun(ownerId=member.id,workflowId=definition.id,graphJson=mapper.writeValueAsString(graph),currentNodeId="start")
    init {
        `when`(access.actor(member.email)).thenReturn(member)
        `when`(runs.findById(row.id)).thenReturn(Optional.of(row))
        `when`(definitions.findById(definition.id)).thenReturn(Optional.of(definition))
        `when`(documents.findById(first.id)).thenReturn(Optional.of(first))
    }
    @Test fun `runs are private to their owner including against other administrators`() {
        assertThatThrownBy {guard.owned(User(role="ADMIN"),row.id)}.isInstanceOf(NotFoundException::class.java)
        assertThat(guard.owned(member,row.id)).isSameAs(row)
    }
    @Test fun `only current and previously reached document steps grant scoped PDF access`() {
        assertThat(guard.allowedDocuments(row)).isEmpty()
        assertThatThrownBy {guard.document(member.email,row.id,first.id)}.isInstanceOf(NotFoundException::class.java)
        row.currentNodeId="doc"
        assertThat(guard.document(member.email,row.id,first.id)).isSameAs(first)
        assertThatThrownBy {guard.document(member.email,row.id,future.id)}.isInstanceOf(NotFoundException::class.java)
        row.historyJson=mapper.writeValueAsString(listOf(WorkflowVisit("doc","PDF",BlockKind.DOCUMENT,"COMPLETED","",Instant.now())))
        row.currentNodeId=null;row.status=WorkflowRunStatus.COMPLETED
        assertThat(guard.document(member.email,row.id,first.id)).isSameAs(first)
        verify(access,never()).document(member,first.id) // The ordinary library gate is unchanged.
    }
    @Test fun `unpublishing archiving and removing a definition revoke continuation and scoped PDF grants`() {
        row.currentNodeId="doc";definition.published=false
        assertThat(guard.blocked(member,row)).isNotNull()
        assertThatThrownBy {guard.document(member.email,row.id,first.id)}.isInstanceOf(ForbiddenException::class.java)
        assertThat(guard.blocked(member.copy(role="ADMIN"),row)).isNull()
        definition.state=WorkflowState.ARCHIVED
        assertThatThrownBy {guard.active(member.copy(role="ADMIN"),row)}.isInstanceOf(ForbiddenException::class.java)
        `when`(definitions.findById(definition.id)).thenReturn(Optional.empty())
        assertThat(guard.blocked(member,row)).isNotNull()
    }
}
