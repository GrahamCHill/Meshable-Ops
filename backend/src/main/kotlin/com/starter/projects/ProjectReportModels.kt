package com.starter.projects

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ProjectReportKind { SPRINT, MONTHLY, PERIOD }
data class ProjectReportInput(val kind: ProjectReportKind = ProjectReportKind.PERIOD, val from: LocalDate, val to: LocalDate,
    val sprintId: UUID? = null, val boardId: UUID? = null, val sendEmail: Boolean = false, val recipientIds: Set<UUID> = emptySet(),
    val title: String = "", val includeDescriptions: Boolean = true, val includeAiSummary: Boolean = true)
data class ProjectReportSection(val title: String, val columns: List<String>, val rows: List<List<String>>)
data class ProjectReportDocument(val title: String, val organization: String, val subtitle: String, val period: String,
    val summary: List<String>, val sections: List<ProjectReportSection>, val kanban: ProjectWorkSummary? = null,
    val insights: ReportInsights = ReportInsights(),val leadTime: ReportLeadTime = ReportLeadTime(),val coverHtml: String? = null,
    val coverAssets: Map<String,String> = emptyMap(),val siteName: String = "",val companyName: String = "",val siteLogo: String = "",
    val sprintMonitor: ReportSprintMonitor = ReportSprintMonitor(),val projectName: String = "",val clientLogo: String = "",val aiSummary: String? = null)
@Entity @Table(name = "workspace_project_reports", indexes = [Index(columnList = "project_id,created_at")])
class ProjectReportRecord(@Id val id: UUID = UUID.randomUUID(), val projectId: UUID = UUID.randomUUID(), val createdBy: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val kind: ProjectReportKind = ProjectReportKind.PERIOD, val fromDate: LocalDate = LocalDate.now(), val toDate: LocalDate = LocalDate.now(),
    val createdAt: Instant = Instant.now(), @get:JsonIgnore @Column(columnDefinition = "bytea", nullable = false) val pdf: ByteArray = byteArrayOf(),
    @get:JsonIgnore @Column(columnDefinition = "TEXT DEFAULT '[]'", nullable = false) val boardIdsJson: String = "[]")
interface ProjectReportMetadata { val id: UUID; val projectId: UUID; val kind: ProjectReportKind; val fromDate: LocalDate; val toDate: LocalDate; val createdAt: Instant; val boardIdsJson: String }
interface ProjectReportRepository : JpaRepository<ProjectReportRecord, UUID> { fun findTop100ByProjectIdOrderByCreatedAtDesc(projectId: UUID): List<ProjectReportMetadata> }
data class ProjectReportView(val id: UUID, val projectId: UUID, val kind: ProjectReportKind, val from: LocalDate, val to: LocalDate, val createdAt: Instant,
    val deliveries: Map<UUID, com.starter.dto.EmailDeliveryResult> = emptyMap())
