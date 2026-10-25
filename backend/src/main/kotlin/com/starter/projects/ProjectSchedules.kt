package com.starter.projects

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.kanban.KanbanContent
import com.starter.plugins.kanban.KanbanRepository
import com.starter.plugins.kanban.KanbanSprint
import com.starter.plugins.kanban.SprintStatus
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class WorkScheduleKind { REPORT, DIGEST }
enum class WorkCadence { DAILY, WEEKLY, MONTHLY, SPRINT_END }
data class WorkScheduleInput(val enabled: Boolean = false, val kind: WorkScheduleKind = WorkScheduleKind.REPORT,
    val cadence: WorkCadence = WorkCadence.MONTHLY, val time: String = "08:00", val timeZone: String = "UTC",
    val dayOfWeek: Int = 1, val dayOfMonth: Int = 1, val reportKind: ProjectReportKind = ProjectReportKind.MONTHLY,
    val recipientIds: Set<UUID> = emptySet(), val includeAi: Boolean = true, val revision: Long = -1)
@Entity @Table(name = "workspace_work_schedules", indexes = [Index(columnList = "enabled,next_run_at")])
class WorkScheduleRecord(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), val projectId: UUID? = null,
    var enabled: Boolean = false, @get:JsonIgnore @Column(columnDefinition = "TEXT") var configurationJson: String = "{}",
    var nextRunAt: Instant? = null, var lastRunAt: Instant? = null, var lastStatus: String? = null, var lastSprintId: UUID? = null, var lastSprintEnd: LocalDate? = null,
    @Version var revision: Long = 0,
    @Column(columnDefinition="TEXT",nullable=false) @org.hibernate.annotations.ColumnDefault("'[]'") var reportedSprintsJson: String = "[]")
interface WorkScheduleRepository : JpaRepository<WorkScheduleRecord, UUID> { fun findByProjectId(projectId: UUID?): List<WorkScheduleRecord>; fun findByOwnerIdAndProjectIdIsNull(ownerId: UUID): List<WorkScheduleRecord> }
data class WorkScheduleView(val id: UUID, val projectId: UUID?, val configuration: WorkScheduleInput, val nextRunAt: Instant?, val lastRunAt: Instant?, val lastStatus: String?)

@Service
class WorkSchedules(private val records: WorkScheduleRepository, private val access: ProjectAccess, private val users: UserRepository,
    private val mapper: ObjectMapper, private val jdbc: JdbcTemplate,private val boards: KanbanRepository,private val boardAccess: ProjectBoardAccess) {
    fun list(email: String, projectId: UUID?): List<WorkScheduleView> {
        val actor = access.actor(email)
        return (if (projectId == null) records.findByOwnerIdAndProjectIdIsNull(actor.id) else { access.require(projectId, actor, true); records.findByProjectId(projectId) }).map(::view)
    }
    @Transactional
    fun save(email: String, projectId: UUID?, id: UUID?, input: WorkScheduleInput): WorkScheduleView {
        val actor = access.actor(email); val project = projectId?.let { access.require(it, actor, true) }
        validate(input)
        if (projectId == null && input.kind != WorkScheduleKind.DIGEST) throw BadRequestException("Personal schedules are work digests")
        if (project != null && input.recipientIds.any { target -> users.findById(target).orElse(null)?.let { it.active && it.role != "EXTERNAL" && target in access.members(project) && access.canRead(it, project) } != true }) throw BadRequestException("Choose active internal project members")
        val row = id?.let { records.findById(it).orElseThrow { NotFoundException("Schedule unavailable") } } ?: WorkScheduleRecord(ownerId = actor.id, projectId = projectId)
        if (row.projectId != projectId || projectId == null && row.ownerId != actor.id) throw NotFoundException("Schedule unavailable")
        if (id != null && row.revision != input.revision) throw ConflictException("This schedule changed. Reload before saving.")
        val previous = configuration(row)
        if (input.cadence == WorkCadence.SPRINT_END && (!row.enabled || previous.cadence != WorkCadence.SPRINT_END)) {
            row.reportedSprintsJson = mapper.writeValueAsString(completedSprints(projectId!!,actor).map { "${it.first}:${it.second.id}" })
        }
        row.enabled = input.enabled; row.configurationJson = mapper.writeValueAsString(input.copy(revision = -1)); row.nextRunAt = if (input.enabled) next(input, Instant.now()) else null
        return view(records.saveAndFlush(row))
    }
    @Transactional
    fun claim(now: Instant): List<Pair<UUID, Instant>> {
        val ids = jdbc.query("SELECT id FROM workspace_work_schedules WHERE enabled AND next_run_at <= ? ORDER BY next_run_at LIMIT 5 FOR UPDATE SKIP LOCKED", { rs, _ -> rs.getObject(1, UUID::class.java) }, java.sql.Timestamp.from(now))
        return ids.map { id ->
            val row = records.findById(id).orElseThrow(); val scheduled = row.nextRunAt ?: now
            row.nextRunAt = next(configuration(row), now); row.lastRunAt = now; row.lastStatus = "RUNNING"; records.saveAndFlush(row)
            id to scheduled
        }
    }
    fun configuration(row: WorkScheduleRecord) = mapper.readValue(row.configurationJson, WorkScheduleInput::class.java).copy(enabled = row.enabled, revision = row.revision)
    fun completedSprints(projectId: UUID,actor: User): List<Pair<UUID,KanbanSprint>> {
        access.require(projectId,actor,true)
        return boards.findByProjectId(projectId).filter {boardAccess.readable(actor,it)}.flatMap { board ->
            mapper.readValue(board.contentJson,KanbanContent::class.java).sprints.filter {it.status==SprintStatus.COMPLETED}.map {board.id to it}
        }.sortedBy {it.second.endDate}
    }
    private fun view(row: WorkScheduleRecord) = WorkScheduleView(row.id, row.projectId, configuration(row), row.nextRunAt, row.lastRunAt, row.lastStatus)
    companion object {
        fun validate(input: WorkScheduleInput) {
            if (input.cadence == WorkCadence.SPRINT_END && (input.kind != WorkScheduleKind.REPORT || input.reportKind != ProjectReportKind.SPRINT)) throw BadRequestException("Sprint-end delivery requires a sprint report")
            if (runCatching { LocalTime.parse(input.time) }.isFailure || runCatching { ZoneId.of(input.timeZone) }.isFailure || input.dayOfWeek !in 1..7 || input.dayOfMonth !in 1..28 || input.recipientIds.size > 20) throw BadRequestException("Choose a valid time, timezone, weekday, month day (1–28), and up to 20 internal recipients")
        }
        fun next(input: WorkScheduleInput, after: Instant): Instant {
            validate(input)
            if(input.cadence == WorkCadence.SPRINT_END)return after.plusSeconds(60)
            val zone = ZoneId.of(input.timeZone); val local = after.atZone(zone); val time = LocalTime.parse(input.time)
            var date = when (input.cadence) {
                WorkCadence.DAILY -> local.toLocalDate()
                WorkCadence.WEEKLY -> local.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(input.dayOfWeek)))
                WorkCadence.MONTHLY -> local.toLocalDate().withDayOfMonth(input.dayOfMonth)
                WorkCadence.SPRINT_END -> error("Sprint-end delivery is checked each minute")
            }
            if (!date.atTime(time).atZone(zone).toInstant().isAfter(after)) date = when (input.cadence) { WorkCadence.DAILY -> date.plusDays(1); WorkCadence.WEEKLY -> date.plusWeeks(1); WorkCadence.MONTHLY -> date.plusMonths(1);WorkCadence.SPRINT_END -> error("Sprint-end delivery is checked each minute") }
            return date.atTime(time).atZone(zone).toInstant()
        }
    }
}

@Service
class WorkScheduleRunner(private val schedules: WorkSchedules, private val records: WorkScheduleRepository, private val users: UserRepository,
    private val access: ProjectAccess, private val reports: ProjectReportService, private val digests: WorkDigestService,
    private val mapper: ObjectMapper) {
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    fun tick() {
        schedules.claim(Instant.now()).forEach schedule@{ (id, scheduled) ->
            val row = records.findById(id).orElse(null) ?: return@schedule
            if (!row.enabled) return@schedule
            try {
                val input = schedules.configuration(row)
                val owner = users.findById(row.ownerId).orElseThrow { NotFoundException("Schedule owner unavailable") }; access.requireFeature(owner)
                if (input.cadence == WorkCadence.SPRINT_END) {
                    sendCompletedSprints(row,input,owner)
                    return@schedule
                }
                val date = scheduled.atZone(ZoneId.of(input.timeZone)).toLocalDate()
                val result = if (input.kind == WorkScheduleKind.DIGEST) {
                    val payload = WorkDigestInput(date, date.plusDays(7), input.timeZone, input.includeAi, input.recipientIds)
                    if (row.projectId == null) listOf(digests.sendMine(owner.email, payload, "digest:$id:$date")) else digests.sendProject(owner.email, row.projectId, payload, "digest:$id:$date").values.toList()
                } else {
                    val projectId = row.projectId ?: throw BadRequestException("Report schedules need a project"); access.require(projectId, owner, true)
                    val sprintEntry = if (input.reportKind == ProjectReportKind.SPRINT) schedules.completedSprints(projectId,owner).filter { it.second.endDate < date && (row.lastSprintEnd == null || it.second.endDate > row.lastSprintEnd) }.maxByOrNull { it.second.endDate } else null
                    val sprint = sprintEntry?.second
                    if (input.reportKind == ProjectReportKind.SPRINT && sprint == null) { row.lastStatus = "NO_COMPLETED_SPRINT"; records.saveAndFlush(row); return@schedule }
                    val month = date.minusMonths(1).withDayOfMonth(1)
                    val from = sprint?.startDate ?: if (input.reportKind == ProjectReportKind.MONTHLY) month else date.minusDays(7)
                    val to = sprint?.endDate ?: if (input.reportKind == ProjectReportKind.MONTHLY) month.with(TemporalAdjusters.lastDayOfMonth()) else date.minusDays(1)
                    val result = reports.generate(owner.email, projectId, ProjectReportInput(input.reportKind, from, to, sprintId = sprint?.id, boardId = sprintEntry?.first, sendEmail = true, recipientIds = input.recipientIds), "report:$id:${sprint?.id ?: "$from:$to"}")
                    if (result.deliveries.isNotEmpty() && result.deliveries.values.all { it.status in setOf("ACCEPTED", "SKIPPED") } && sprint != null) { row.lastSprintId = sprint.id; row.lastSprintEnd = sprint.endDate }
                    result.deliveries.values.toList()
                }
                row.lastStatus = if (result.isNotEmpty() && result.all { it.status in setOf("ACCEPTED", "SKIPPED") }) "ACCEPTED" else "DELIVERY_UNAVAILABLE"
            } catch (_: Exception) { row.lastStatus = "FAILED_CHECK_ACCESS_AND_PROVIDERS";if(schedules.configuration(row).cadence==WorkCadence.SPRINT_END)row.nextRunAt=Instant.now().plusSeconds(300) }
            try { records.saveAndFlush(row) } catch (_: org.springframework.orm.ObjectOptimisticLockingFailureException) { /* Preserve a concurrent schedule edit. */ }
        }
    }
    private fun sendCompletedSprints(row: WorkScheduleRecord,input: WorkScheduleInput,owner: User) {
        val projectId=row.projectId ?: throw BadRequestException("Sprint reports need a project")
        val reported=mapper.readValue(row.reportedSprintsJson,object:com.fasterxml.jackson.core.type.TypeReference<MutableSet<String>>() {})
        val pending=schedules.completedSprints(projectId,owner).filter {"${it.first}:${it.second.id}" !in reported}.take(5)
        if(pending.isEmpty()){row.lastStatus="NO_COMPLETED_SPRINT";records.saveAndFlush(row);return}
        var unavailable=false
        pending.forEach { (boardId,sprint) ->
            val key="$boardId:${sprint.id}"
            val result=reports.generate(owner.email,projectId,ProjectReportInput(ProjectReportKind.SPRINT,sprint.startDate,sprint.endDate,
                sprintId=sprint.id,boardId=boardId,sendEmail=true,recipientIds=input.recipientIds),"report:${row.id}:$key")
            if(result.deliveries.isNotEmpty()&&result.deliveries.values.all {it.status in setOf("ACCEPTED","SKIPPED")}) {
                reported.add(key);row.reportedSprintsJson=mapper.writeValueAsString(reported)
            } else unavailable=true
            row.lastStatus=if(unavailable)"DELIVERY_UNAVAILABLE" else "ACCEPTED"
            if(unavailable)row.nextRunAt=Instant.now().plusSeconds(300)
            records.saveAndFlush(row)
        }
    }
}

@RestController @RequestMapping("/api/projects")
class WorkScheduleController(private val schedules: WorkSchedules) {
    @GetMapping("/schedules") fun mine(@AuthenticationPrincipal user: UserDetails) = schedules.list(user.username, null)
    @PostMapping("/schedules") fun createMine(@AuthenticationPrincipal user: UserDetails, @RequestBody input: WorkScheduleInput) = schedules.save(user.username, null, null, input)
    @PutMapping("/schedules/{scheduleId}") fun saveMine(@AuthenticationPrincipal user: UserDetails, @PathVariable scheduleId: UUID, @RequestBody input: WorkScheduleInput) = schedules.save(user.username, null, scheduleId, input)
    @GetMapping("/{id}/schedules") fun list(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = schedules.list(user.username, id)
    @PostMapping("/{id}/schedules") fun create(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: WorkScheduleInput) = schedules.save(user.username, id, null, input)
    @PutMapping("/{id}/schedules/{scheduleId}") fun save(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable scheduleId: UUID, @RequestBody input: WorkScheduleInput) = schedules.save(user.username, id, scheduleId, input)
}
