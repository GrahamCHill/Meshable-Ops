package com.starter.plugins.operations

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TimeSource { INTERNAL, ACTIVITYWATCH, TIMECAMP }
enum class TimeStatus { DRAFT, SUBMITTED, APPROVED, REJECTED }
data class TimeEntry(val userId: UUID, val projectId: UUID, val start: Instant, val end: Instant, val seconds: Long,
    val description: String, val source: TimeSource=TimeSource.INTERNAL, val sourceKey: String="", val billable: Boolean=true,
    val status: TimeStatus=TimeStatus.DRAFT, val approvedBy: UUID?=null, val roleId: UUID?=null, val rate: BigDecimal?=null,
    val currency: String="USD",val billedInvoice: UUID?=null)
data class TimeEntryInput(val projectId: UUID,val start: Instant,val end: Instant,val description: String,val billable: Boolean=true,val revision: Long = -1)
data class TimeEntryView(val id: UUID,val entry: TimeEntry,val revision: Long)
data class TimeAction(val revision: Long,val action: String)
data class RunningTimer(val projectId: UUID,val start: Instant,val description: String,val billable: Boolean=true)
data class TimerInput(val projectId: UUID,val description: String,val billable: Boolean=true)
data class ActivitySlice(val start: Instant,val end: Instant,val description: String,val bucket: String,val eventId: String,val allocated: Boolean=false)
data class ActivityAllocation(val projectId: UUID,val description: String,val revision: Long,val billable: Boolean=true)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value=["effectiveFrom","effectiveTo"])
data class DeveloperRate(val name: String,val amount: BigDecimal,val currency: String="USD",val roleId: UUID=UUID.randomUUID(),val enabled:Boolean=true)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value=["effectiveFrom","effectiveTo"])
data class RateAssignment(val userId: UUID,val roleId: UUID,val enabled:Boolean=true)

data class ProjectMemberRate(val userId:UUID,val amount:BigDecimal,val currency:String="USD",val enabled:Boolean=true)
data class ProjectMemberRateInput(val rate:ProjectMemberRate,val revision:Long = -1)
data class RateInput(val rate: DeveloperRate,val revision: Long = -1)
data class AssignmentInput(val assignment: RateAssignment,val revision: Long = -1)
data class TimeCampMapping(val taskId: Long,val providerUserId: Long,val userId: UUID,val projectId: UUID)
data class TimeCampSyncInput(val from: LocalDate,val to: LocalDate)
data class ImportResult(val imported: Int,val skipped: Int,val warnings: List<String> = emptyList())
