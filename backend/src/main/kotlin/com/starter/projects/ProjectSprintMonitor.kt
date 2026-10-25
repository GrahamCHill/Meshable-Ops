package com.starter.projects

import com.starter.plugins.kanban.CardStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportSprintDuration(val reference: String,val title: String,val board: String,val sprint: String,
    val sprintStart: LocalDate,val measuredThrough: LocalDate,val days: Long,val completed: Boolean)
data class ReportSprintSummary(val name: String,val board: String,val startDate: LocalDate,val endDate: LocalDate,
    val total: Int,val completed: Int,val completedAverageDays: Double?,val openAverageDays: Double?,val completedSampleSize: Int = 0,val openSampleSize: Int = 0)
data class ReportSprintMonitor(val assignedTickets: Int = 0,val completedAverageDays: Double? = null,val completedSampleSize: Int = 0,
    val openAverageDays: Double? = null,val openSampleSize: Int = 0,val oldestOpenDays: Long? = null,val completionPercent: Double? = null,
    val noSprint: Int = 0,val missingSprint: Int = 0,val missingCompletion: Int = 0,val invalidDates: Int = 0,val notStarted: Int = 0,
    val finishedAfterEnd: Int = 0,val totalSprints: Int = 0,val sprints: List<ReportSprintSummary> = emptyList(),val longestTickets: List<ReportSprintDuration> = emptyList())

object ProjectSprintMonitor {
    fun duration(item: ProjectWorkItem,today: LocalDate): ReportSprintDuration? {
        val sprint=item.sprint ?: return null
        if(item.card.sprintId!=sprint.id)return null
        val completed=item.card.status==CardStatus.DONE
        val end=if(completed)item.card.completionDate ?: return null else today
        if(end<sprint.startDate)return null
        return ReportSprintDuration(item.card.reference.ifBlank {item.card.id.toString()},item.card.title,item.boardName,sprint.name,
            sprint.startDate,end,ChronoUnit.DAYS.between(sprint.startDate,end),completed)
    }
    fun summarize(items: List<ProjectWorkItem>,today: LocalDate): ReportSprintMonitor {
        val assigned=items.filter {it.card.sprintId!=null}
        val known=assigned.filter {it.sprint?.id==it.card.sprintId}
        val samples=known.mapNotNull {item->duration(item,today)?.let {item to it}}
        val done=samples.map {it.second}.filter {it.completed};val open=samples.map {it.second}.filter {!it.completed}
        fun average(values: List<ReportSprintDuration>)=values.takeIf {it.isNotEmpty()}?.map {it.days}?.average()
        val groups=known.groupBy {it.boardId to it.sprint!!.id}
        return ReportSprintMonitor(assigned.size,average(done),done.size,average(open),open.size,open.maxOfOrNull {it.days},
            assigned.takeIf {it.isNotEmpty()}?.let {scope->scope.count {it.card.status==CardStatus.DONE}*100.0/scope.size},
            items.size-assigned.size,assigned.size-known.size,known.count {it.card.status==CardStatus.DONE && it.card.completionDate==null},
            known.count {it.card.status==CardStatus.DONE && it.card.completionDate?.isBefore(it.sprint!!.startDate)==true},
            known.count {it.card.status!=CardStatus.DONE && it.sprint!!.startDate>today},
            samples.count {it.second.completed && it.second.measuredThrough>it.first.sprint!!.endDate},groups.size,
            groups.values.sortedByDescending {it.size}.take(6).map {scope->
                val sprint=scope.first().sprint!!;val measured=scope.mapNotNull {duration(it,today)}
                ReportSprintSummary(sprint.name,scope.first().boardName,sprint.startDate,sprint.endDate,scope.size,scope.count {it.card.status==CardStatus.DONE},
                    average(measured.filter {it.completed}),average(measured.filter {!it.completed}),measured.count {it.completed},measured.count {!it.completed})
            },samples.map {it.second}.sortedByDescending {it.days}.take(6))
    }
}
