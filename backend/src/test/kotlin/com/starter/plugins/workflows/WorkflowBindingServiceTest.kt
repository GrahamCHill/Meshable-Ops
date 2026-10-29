package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.BundledModuleAccess
import com.starter.plugins.documents.DocumentAccess
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class WorkflowBindingServiceTest {
    private val bindings=mock(WorkflowActionBindingRepository::class.java)
    private val definitions=mock(WorkflowRepository::class.java)
    private val access=mock(DocumentAccess::class.java)
    private val catalogue=mock(WorkflowActionCatalogue::class.java)
    private val mapper=jacksonObjectMapper()
    private val service=WorkflowBindingService(bindings,definitions,access,mock(BundledModuleAccess::class.java),catalogue,mapper,mock(AuditLogService::class.java))
    private val actor=User(email="admin@example.test",role="ADMIN")
    private val definition=WorkflowDefinition(state=WorkflowState.READY,published=true,graphJson=mapper.writeValueAsString(WorkflowGraph(emptyList(),emptyList())))
    init {
        `when`(access.admin(actor.email)).thenReturn(actor)
        `when`(catalogue.all()).thenReturn(listOf(WorkflowActionDescriptor("category:external","External","system",true)))
        `when`(definitions.findById(definition.id)).thenReturn(Optional.of(definition))
        `when`(bindings.saveAndFlush(any(WorkflowActionBinding::class.java))).thenAnswer {it.arguments[0]}
    }
    @Test fun `only ready published prerequisite designs can enforce external sends`() {
        val input=WorkflowBindingRequest("category:external",definition.id)
        assertThat(service.save(actor.email,null,input).enabled).isTrue()
        definition.published=false
        assertThatThrownBy {service.save(actor.email,null,input)}.isInstanceOf(BadRequestException::class.java)
        definition.published=true
        definition.graphJson=mapper.writeValueAsString(WorkflowGraph(listOf(WorkflowBlock("mail",BlockKind.EMAIL,"Send")),emptyList()))
        assertThatThrownBy {service.save(actor.email,null,input)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `an administrator can disable an orphaned policy without reopening its deleted workflow`() {
        val row=WorkflowActionBinding(selector="route:POST /removed",workflowId=definition.id,revision=2)
        `when`(bindings.findById(row.id)).thenReturn(Optional.of(row))
        `when`(definitions.findById(definition.id)).thenReturn(Optional.empty())
        assertThat(service.save(actor.email,row.id,WorkflowBindingRequest(row.selector,row.workflowId,enabled=false,revision=2)).enabled).isFalse()
        assertThatThrownBy {service.save(actor.email,row.id,WorkflowBindingRequest(row.selector,row.workflowId,enabled=false,revision=1))}.isInstanceOf(ConflictException::class.java)
    }
}
