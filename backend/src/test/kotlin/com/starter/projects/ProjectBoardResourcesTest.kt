package com.starter.projects

import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import com.starter.plugins.sourcecontrol.SourceControlService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class ProjectBoardResourcesTest {
    @Test fun `board membership and ticket existence apply before the aggregate limit`() {
        val actor=User(email="member@example.test");val project=UUID.randomUUID()
        val access=mock(ProjectAccess::class.java);val boards=mock(ProjectBoardAccess::class.java);val settings=mock(ProjectDevelopmentSettingsService::class.java)
        val sql=mutableListOf<String>()
        val jdbc=mock(JdbcTemplate::class.java) {call->if(call.method.name=="query") {sql+=call.arguments[0] as String;emptyList<Any>()}else RETURNS_DEFAULTS.answer(call)}
        `when`(access.actor(actor.email)).thenReturn(actor);`when`(access.hasFeature(actor,"plugin.kanban")).thenReturn(true)
        `when`(settings.effective(project)).thenReturn(DevelopmentFeatures());`when`(boards.sqlArgs(actor)).thenReturn(ProjectAccessSql.boardArgs(actor,true))
        val service=ProjectBoardResources(jdbc,access,boards,mock(PluginRegistry::class.java),mock(SourceControlService::class.java),settings)
        assertThat(service.list(actor.email,project)).isEmpty()
        val query=sql.single().replace(Regex("\\s+")," ")
        assertThat(query).contains(ProjectAccessSql.boardPredicate().replace(Regex("\\s+")," "),"dl.card_id IS NULL OR EXISTS","b.project_id=?")
        assertThat(query.indexOf("b.project_id=?")).isLessThan(query.indexOf("LIMIT 200"))
        doThrow(NotFoundException("Unavailable")).`when`(access).require(project,actor,false)
        assertThatThrownBy {service.list(actor.email,project)}.isInstanceOf(NotFoundException::class.java)
        assertThat(sql).hasSize(1)
    }
}
