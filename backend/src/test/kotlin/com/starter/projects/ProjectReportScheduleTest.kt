package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.EmailDeliveryResult
import com.starter.exception.BadRequestException
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.*
import java.util.Optional
import java.util.UUID

class ProjectReportScheduleTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val records=mock(WorkScheduleRepository::class.java)
    private val schedules=mock(WorkSchedules::class.java)
    private val access=mock(ProjectAccess::class.java)
    private val users=mock(UserRepository::class.java)
    private val owner=User(email="lead@example.test")
    private val project=ProjectRecord(leadId=owner.id)
    private val row=WorkScheduleRecord(ownerId=owner.id,projectId=project.id,enabled=true)
    private val generated=mutableListOf<Pair<ProjectReportInput,String>>()
    private val reports=mock(ProjectReportService::class.java) {call->
        if(call.method.name=="generate") {
            assertThat(call.getArgument<UUID>(1)).isEqualTo(project.id)
            val input=call.getArgument<ProjectReportInput>(2);generated+=input to call.getArgument<String>(3)
            ProjectReportView(UUID.randomUUID(),project.id,input.kind,input.from,input.to,Instant.now(),mapOf(owner.id to EmailDeliveryResult("ACCEPTED","Test sink")))
        } else Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val runner=WorkScheduleRunner(schedules,records,users,access,reports,mock(WorkDigestService::class.java),mapper)
    private val scheduled=Instant.parse("2026-11-01T08:00:00Z")
    init {
        `when`(schedules.claim(any(Instant::class.java) ?: Instant.EPOCH)).thenReturn(listOf(row.id to scheduled))
        `when`(records.findById(row.id)).thenReturn(Optional.of(row))
        `when`(users.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(access.require(project.id,owner,true)).thenReturn(project)
    }
    @Test fun `monthly delivery uses the entire previous calendar month within the project`() {
        `when`(schedules.configuration(row)).thenReturn(WorkScheduleInput(enabled=true))
        runner.tick()
        val input=generated.single().first
        assertThat(input.kind).isEqualTo(ProjectReportKind.MONTHLY)
        assertThat(input.from).isEqualTo(LocalDate.of(2026,10,1));assertThat(input.to).isEqualTo(LocalDate.of(2026,10,31))
        assertThat(input.sendEmail).isTrue()
    }
    @Test fun `every newly completed sprint is delivered once even with matching end dates`() {
        val input=WorkScheduleInput(enabled=true,cadence=WorkCadence.SPRINT_END,reportKind=ProjectReportKind.SPRINT)
        `when`(schedules.configuration(row)).thenReturn(input)
        val first=KanbanSprint(UUID.randomUUID(),"First sprint",LocalDate.of(2026,10,1),LocalDate.of(2026,10,14),SprintStatus.COMPLETED)
        val second=first.copy(id=UUID.randomUUID(),name="Second sprint")
        val boardA=UUID.randomUUID();val boardB=UUID.randomUUID()
        `when`(schedules.completedSprints(project.id,owner)).thenReturn(listOf(boardA to first,boardB to second))
        runner.tick();runner.tick()
        assertThat(generated).hasSize(2)
        assertThat(generated.map {it.first.boardId}).containsExactly(boardA,boardB)
        assertThat(generated.map {it.first.sprintId}).containsExactly(first.id,second.id)
        assertThat(generated.map {it.second}.distinct()).hasSize(2)
        assertThat(row.reportedSprintsJson).contains(first.id.toString(),second.id.toString())
    }
    @Test fun `enabling sprint delivery excludes existing completions and ignores overdue active sprints`() {
        val boards=mock(KanbanRepository::class.java);val boardAccess=mock(ProjectBoardAccess::class.java)
        val service=WorkSchedules(records,access,users,mapper,mock(JdbcTemplate::class.java),boards,boardAccess)
        `when`(access.actor(owner.email)).thenReturn(owner)
        val completed=KanbanSprint(UUID.randomUUID(),"Old completion",LocalDate.of(2026,9,1),LocalDate.of(2026,9,14),SprintStatus.COMPLETED)
        val active=completed.copy(id=UUID.randomUUID(),name="Still active",status=SprintStatus.ACTIVE)
        val board=KanbanBoard(projectId=project.id,contentJson=mapper.writeValueAsString(KanbanContent(sprints=listOf(completed,active))))
        `when`(boards.findByProjectId(project.id)).thenReturn(listOf(board));`when`(boardAccess.readable(owner,board)).thenReturn(true)
        `when`(records.saveAndFlush(any(WorkScheduleRecord::class.java))).thenAnswer {it.arguments[0]}
        val saved=service.save(owner.email,project.id,null,WorkScheduleInput(enabled=true,cadence=WorkCadence.SPRINT_END,reportKind=ProjectReportKind.SPRINT))
        assertThat(saved.configuration.cadence).isEqualTo(WorkCadence.SPRINT_END)
        val capture=org.mockito.ArgumentCaptor.forClass(WorkScheduleRecord::class.java)
        verify(records).saveAndFlush(capture.capture())
        assertThat(capture.value.reportedSprintsJson).contains(completed.id.toString()).doesNotContain(active.id.toString())
        assertThat(service.completedSprints(project.id,owner).map {it.second.id}).containsExactly(completed.id)
    }
    @Test fun `sprint-end delivery validates report kind and checks again in one minute`() {
        val input=WorkScheduleInput(cadence=WorkCadence.SPRINT_END,reportKind=ProjectReportKind.SPRINT)
        assertThat(WorkSchedules.next(input,scheduled)).isEqualTo(scheduled.plusSeconds(60))
        assertThatThrownBy {WorkSchedules.validate(input.copy(kind=WorkScheduleKind.DIGEST))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {WorkSchedules.validate(input.copy(reportKind=ProjectReportKind.MONTHLY))}.isInstanceOf(BadRequestException::class.java)
    }
}
