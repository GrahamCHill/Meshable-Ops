package com.starter.projects

import com.starter.plugins.kanban.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportInsight(val label: String,val count: Int)
data class ReportInsights(val metrics: List<ReportInsight> = emptyList(),val priorities: List<WorkBreakdown> = emptyList(),
    val types: List<WorkBreakdown> = emptyList(),val observations: List<String> = emptyList())
data class ReportLeadTime(val averageDays: Double? = null,val medianDays: Double? = null,val fastestDays: Long? = null,val slowestDays: Long? = null,
    val sampleSize: Int = 0,val completedTickets: Int = 0,val missingStart: Int = 0,val missingCompletion: Int = 0,val invalidDates: Int = 0,
    val distribution: List<WorkBreakdown> = emptyList(),val boards: List<ReportBoardLeadTime> = emptyList())
data class ReportBoardLeadTime(val board: String,val count: Int,val averageDays: Double)
object ProjectReportAnalytics {
    fun insights(items: List<ProjectWorkItem>,today: LocalDate): ReportInsights {
        val cards=items.map {it.card};val open=cards.filter {it.status!=CardStatus.DONE}
        fun group(key:(KanbanCard)->String)=cards.groupingBy(key).eachCount().map {(key,count)->WorkBreakdown(key,key,count)}.sortedByDescending {it.count}
        val blocked=open.count {it.blocked};val overdue=open.count {it.dueDate?.isBefore(today)==true};val unassigned=open.count {it.assigneeId==null}
        return ReportInsights(listOf(ReportInsight("Open tickets",open.size),ReportInsight("Flagged / blocked",blocked),ReportInsight("Overdue open tickets",overdue),
            ReportInsight("Due in the next 7 days",open.count {it.dueDate?.let {date->date>=today && date<=today.plusDays(7)}==true}),
            ReportInsight("Unassigned open tickets",unassigned),ReportInsight("High / urgent open tickets",open.count {it.priority in setOf(TicketPriority.HIGH,TicketPriority.URGENT)}),
            ReportInsight("Completed subtasks",cards.count {it.parentId!=null && it.status==CardStatus.DONE}),ReportInsight("Open tickets without due dates",open.count {it.dueDate==null})),
            group {it.priority.name.replace('_',' ')},group {it.type.name.replace('_',' ')},listOfNotNull(
                "$blocked open tickets are flagged or blocked; review their dependencies.",
                "$overdue open tickets have a recorded due date before $today.",
                "$unassigned open tickets have no assignee. These are delivery facts, not individual performance scores."))
    }
    fun leadTime(items: List<ProjectWorkItem>): ReportLeadTime {
        val done=items.filter {it.card.status==CardStatus.DONE}
        val samples=done.mapNotNull {item->
            val start=item.card.startDate;val end=item.card.completionDate
            if(start==null || end==null || end<start)null else item to ChronoUnit.DAYS.between(start,end)
        }
        val days=samples.map {it.second}.sorted()
        val bounds=listOf("Same day" to (0L..0L),"1-2 days" to (1L..2L),"3-7 days" to (3L..7L),"8-14 days" to (8L..14L),"15+ days" to (15L..Long.MAX_VALUE))
        return ReportLeadTime(days.takeIf {it.isNotEmpty()}?.average(),days.takeIf {it.isNotEmpty()}?.let {if(it.size%2==0)(it[it.size/2-1]+it[it.size/2])/2.0 else it[it.size/2].toDouble()},
            days.firstOrNull(),days.lastOrNull(),days.size,done.size,done.count {it.card.startDate==null},done.count {it.card.completionDate==null},
            done.count {it.card.startDate!=null && it.card.completionDate!=null && it.card.completionDate<it.card.startDate},
            bounds.map {(label,range)->WorkBreakdown(label,label,days.count {it in range})},
            samples.groupBy {it.first.boardId}.values.map {values->ReportBoardLeadTime(values.first().first.boardName,values.size,values.map {it.second}.average())}.sortedByDescending {it.count}.take(8))
    }
    val cellLimits=listOf(160,140,90,110,240)
    fun excerpt(text: String,limit: Int): String {
        val lines=text.replace("\r","").lineSequence().take(8).joinToString("\n")
        return if(text.length>limit || text.count {it=='\n'}>=8)lines.take(limit-3)+"..." else lines
    }
}
