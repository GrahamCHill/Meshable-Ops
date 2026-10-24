package com.starter.plugins

import com.starter.plugins.calendar.*
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.model.postgres.User
import com.starter.projects.ProjectBoardAccess
import com.starter.projects.ProjectAccessSql
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId

class KanbanCalendarTest {
    @Test fun `sprints use inclusive date ranges and retain their calendar identity after an edit`() {
        val user=User(email="member@example.test");val actors=mock(PluginActor::class.java);val registry=mock(PluginRegistry::class.java)
        val boards=mock(ProjectBoardAccess::class.java);val sql=mutableListOf<String>();val args=mutableListOf<List<Any?>>()
        var name="Sprint 1";var end="2026-11-01"
        val rs=mock(ResultSet::class.java) { call -> if(call.method.name=="getString") when(call.arguments[0]) {
            "board_id"->"board-one";"name"->"Delivery";"sprint_id"->"sprint-one";"title"->name;"state"->"ACTIVE";"goal"->"Ship safely";"starts"->"2026-10-20";"ends"->end;else->null
        } else RETURNS_DEFAULTS.answer(call) }
        val jdbc=mock(JdbcTemplate::class.java) { call -> if(call.method.name=="query") {
            val query=call.arguments[0] as String;sql+=query
            val supplied=call.arguments.drop(2);args+=supplied
            if(query.contains("'sprints'")) {
                @Suppress("UNCHECKED_CAST") val mapper=call.arguments[1] as RowMapper<CalendarEventView>
                listOf(mapper.mapRow(rs,0))
            } else emptyList<CalendarEventView>()
        } else RETURNS_DEFAULTS.answer(call) }
        val events=mock(CalendarEventRepository::class.java) { call -> if(call.method.name=="window") emptyList<CalendarEvent>() else RETURNS_DEFAULTS.answer(call) }
        `when`(actors.current(user.email)).thenReturn(user);`when`(boards.sqlArgs(user)).thenReturn(arrayOf("project-scope"))
        val plugin=PluginView(KanbanPlugin().manifest,true,setOf("USER"),tableAccess=mapOf("plugin_kanban_boards" to PluginTableAccess(setOf("USER"),emptySet())))
        `when`(registry.available(user.role)).thenReturn(listOf(plugin))
        val service=CalendarService(events,actors,registry,jdbc,boards)
        val start=Instant.parse("2026-10-24T22:00:00Z");val until=Instant.parse("2026-10-26T22:00:00Z")
        val first=service.window(user.email,start,until,ZoneId.of("Africa/Johannesburg")).events.single()
        assertThat(first.start).isEqualTo("2026-10-20");assertThat(first.end).isEqualTo("2026-11-02");assertThat(first.allDay).isTrue()
        assertThat(first.href).contains("sprint=sprint-one")
        assertThat(sql).hasSize(2).allMatch { it.replace(Regex("\\s+"), " ").contains(ProjectAccessSql.boardPredicate().replace(Regex("\\s+"), " ")) }
        assertThat(args.last()).containsExactly("project-scope","2026-10-27","2026-10-25")
        name="Release sprint";end="2026-11-04"
        val edited=service.window(user.email,start,until).events.single()
        assertThat(edited.id).isEqualTo(first.id);assertThat(edited.title).isEqualTo("Release sprint");assertThat(edited.end).isEqualTo("2026-11-05")
    }
    @Test fun `calendar cannot expose sprint data without Kanban table access`() {
        val user=User(email="member@example.test");val actors=mock(PluginActor::class.java);val registry=mock(PluginRegistry::class.java);val jdbc=mock(JdbcTemplate::class.java)
        `when`(actors.current(user.email)).thenReturn(user);`when`(registry.available(user.role)).thenReturn(emptyList())
        val service=CalendarService(mock(CalendarEventRepository::class.java),actors,registry,jdbc)
        assertThat(service.window(user.email,Instant.parse("2026-10-24T00:00:00Z"),Instant.parse("2026-10-25T00:00:00Z")).events).isEmpty()
        verifyNoInteractions(jdbc)
    }
}
