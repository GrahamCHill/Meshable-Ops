package com.starter.plugins.knowledge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class KnowledgeServiceTest {
    private val rows = mock(KnowledgePageRepository::class.java)
    private val access = mock(KnowledgeAccess::class.java)
    private val service = KnowledgeService(rows, access, KnowledgeMarkdown(), jacksonObjectMapper(), mock(JdbcTemplate::class.java))
    private val parent = KnowledgePage(title = "Parent", revision = 2)
    private val child = KnowledgePage(title = "Child", parentId = parent.id)
    init { `when`(rows.findById(parent.id)).thenReturn(Optional.of(parent)); `when`(rows.findById(child.id)).thenReturn(Optional.of(child)) }
    @Test fun `hierarchy changes reject self-parenting cycles and stale revisions`() {
        assertThatThrownBy { service.save("member", parent.id, KnowledgePageInput("Parent", "text", parentId = child.id, revision = 2)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.save("member", parent.id, KnowledgePageInput("Parent", "text", revision = 1)) }.isInstanceOf(ConflictException::class.java)
        verify(rows, never()).saveAndFlush(any(KnowledgePage::class.java))
    }
    @Test fun `deleting a parent with children fails without deleting its content`() {
        `when`(rows.existsByParentId(parent.id)).thenReturn(true)
        assertThatThrownBy { service.delete("member", parent.id, 2) }.isInstanceOf(ConflictException::class.java)
        verify(rows, never()).delete(any(KnowledgePage::class.java))
    }
    @Test fun `read-only tables block writes before documents are loaded`() {
        doThrow(ForbiddenException("Read only")).`when`(access).user("member", true)
        assertThatThrownBy { service.save("member", parent.id, KnowledgePageInput("Parent", "text", revision = 2)) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(rows)
    }
}
