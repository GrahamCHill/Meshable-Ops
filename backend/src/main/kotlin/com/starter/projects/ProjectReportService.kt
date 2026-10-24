package com.starter.projects

import com.starter.documents.WeasyRendererClient
import com.starter.dto.SendEmailRequest
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.notification.NotificationService
import com.starter.plugins.kanban.CardStatus
import com.starter.repository.postgres.UserRepository
import com.starter.service.branding.BrandingPreferencesService
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.http.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ProjectReportService(private val access: ProjectAccess, private val projects: ProjectService, private val work: ProjectWorkService,
    private val reports: ProjectReportRepository, private val users: UserRepository, private val renderer: WeasyRendererClient,
    private val branding: BrandingPreferencesService, private val notifications: NotificationService, private val boards: com.starter.plugins.kanban.KanbanRepository,
    private val boardAccess: ProjectBoardAccess, private val mapper: com.fasterxml.jackson.databind.ObjectMapper,
    private val downloadPolicy: com.starter.service.DocumentAccessService, private val pdfService: com.starter.service.PdfService) {
    fun list(email: String, id: UUID): List<ProjectReportView> { val actor = access.actor(email); access.require(id, actor); return reports.findTop100ByProjectIdOrderByCreatedAtDesc(id).filter { allowedBoards(actor, it.boardIdsJson) }.map { ProjectReportView(it.id, it.projectId, it.kind, it.fromDate, it.toDate, it.createdAt) } }
    fun generate(email: String, id: UUID, request: ProjectReportInput, deliveryKey: String? = null): ProjectReportView {
        val actor = access.actor(email); val project = access.require(id, actor, request.sendEmail)
        validateWindow(request.from, request.to)
        if (request.recipientIds.size > 100 || request.kind == ProjectReportKind.SPRINT && request.sprintId == null) throw BadRequestException("Choose a sprint and up to 100 internal recipients")
        val recipients = if (request.sendEmail) (request.recipientIds + project.leadId).map { target ->
            users.findById(target).orElse(null)?.takeIf { it.active && it.role != "EXTERNAL" && access.canRead(it, project) } ?: throw BadRequestException("Every recipient, including the project lead, must be an active internal project member")
        } else emptyList()
        val cards = work.work(id, actor).filter { item -> (request.boardId == null || item.boardId == request.boardId) && (request.sprintId == null || item.card.sprintId == request.sprintId) && inPeriod(item.card.startDate, item.card.dueDate, item.card.completionDate, request.from, request.to) }
        if (cards.size > 1500) throw BadRequestException("Select a narrower period or board for reports exceeding 1500 tickets")
        val boardIds = cards.map { it.boardId }.toSet()
        if (recipients.any { !allowedBoards(it, mapper.writeValueAsString(boardIds)) }) throw BadRequestException("A report recipient cannot access one of its team-restricted boards. Select a narrower board or different recipients.")
        val names = users.findAllById(cards.flatMap { it.card.reviewerIds + listOfNotNull(it.card.assigneeId) }.toSet()).associate { it.id to (it.name ?: it.username) }
        val sections = cards.groupBy { it.boardName }.map { (board, entries) -> ProjectReportSection(board, listOf("ID / task", "Owner / reviewers", "Status / points", "Dates", "Description"), entries.map { item ->
            val card = item.card
            listOf("${card.reference.ifBlank { card.id.toString() }}\n${card.title}", "${card.assigneeId?.let { names[it] } ?: "Unassigned"}\nReview: ${card.reviewerIds.mapNotNull { names[it] }.joinToString().ifBlank { "—" }}",
                "${card.status.name.replace('_', ' ')}\n${card.storyPoints?.toString() ?: "—"} points${if (card.blocked) "\nBlocked" else ""}",
                "Start: ${card.startDate ?: "—"}\nDue: ${card.dueDate ?: "—"}\nDone: ${card.completionDate ?: "—"}", if (card.description.length > 500) card.description.take(497) + "…" else card.description)
        }) }.ifEmpty { listOf(ProjectReportSection("Project activity", listOf("Result"), listOf(listOf("No tickets matched the selected period or sprint.")))) }
        val context = projects.get(email, id)
        val pdf = renderer.report(ProjectReportDocument("${request.kind.name.lowercase().replaceFirstChar { it.titlecase() }} report", branding.current().applicationName,
            "${project.name} · ${context.clientName}", "${request.from} — ${request.to}", listOf("${cards.size} tickets in scope", "${cards.count { it.card.status == CardStatus.DONE }} completed; ${cards.count { it.card.status == CardStatus.IN_PROGRESS }} in progress",
                "${cards.sumOf { it.card.storyPoints ?: 0.0 }} story points", "Project lead: ${users.findById(project.leadId).orElse(null)?.let { it.name ?: it.username } ?: "Member"}", "Confidential delivery-risk scores and personal calendars are excluded."), sections))
        access.require(id, access.actor(email), request.sendEmail)
        val saved = reports.saveAndFlush(ProjectReportRecord(projectId = id, createdBy = actor.id, kind = request.kind, fromDate = request.from, toDate = request.to, pdf = pdf, boardIdsJson = mapper.writeValueAsString(boardIds)))
        val deliveries = recipients.associate { recipient ->
            val fresh = users.findById(recipient.id).orElseThrow { NotFoundException("Recipient unavailable") }
            if (!access.canRead(fresh, project) || !allowedBoards(fresh, saved.boardIdsJson)) throw BadRequestException("Recipient access changed before sending")
            val message = SendEmailRequest(fresh.email, "${project.name}: ${request.kind.name.lowercase()} report", "Attached is the internal project report for ${request.from} to ${request.to}.")
            val delivered = protectedPdf(pdf, fresh.email)
            if (!allowedBoards(fresh, saved.boardIdsJson)) throw BadRequestException("Recipient board access changed before sending")
            val attachments = listOf(EmailAttachment("project-report-${saved.id}.pdf", delivered))
            recipient.id to if (deliveryKey == null) notifications.sendEmail("projects.report", message, attachments)
                else notifications.deliver("projects.report", "email", com.starter.notification.NotificationTarget(userId = fresh.id, email = fresh.email), mapOf("subject" to message.subject, "message" to message.text), "$deliveryKey:${fresh.id}", attachments)
        }
        return view(saved, deliveries)
    }
    fun pdf(email: String, projectId: UUID, reportId: UUID): ByteArray {
        val actor = access.actor(email); access.require(projectId, actor)
        val row = reports.findById(reportId).orElse(null)?.takeIf { it.projectId == projectId && allowedBoards(actor, it.boardIdsJson) } ?: throw NotFoundException("Report unavailable")
        val bytes = protectedPdf(row.pdf, email)
        val fresh = access.actor(email); access.require(projectId, fresh)
        if (!allowedBoards(fresh, row.boardIdsJson)) throw NotFoundException("Report unavailable")
        return bytes
    }
    private fun protectedPdf(bytes: ByteArray, email: String): ByteArray {
        val policy = downloadPolicy.current()
        if (policy.downloadMode == com.starter.model.postgres.DownloadMode.DISABLED) throw com.starter.exception.ForbiddenException("PDF downloads and report attachments are disabled")
        val result = if (policy.downloadMode == com.starter.model.postgres.DownloadMode.WATERMARKED) pdfService.watermark(bytes, policy.watermarkText, email) else bytes
        if (downloadPolicy.current() != policy) throw com.starter.exception.ForbiddenException("Document policy changed. Refresh before downloading or sending.")
        return result
    }
    private fun allowedBoards(actor: com.starter.model.postgres.User, json: String): Boolean = mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<Set<UUID>>() {}).all { id -> boards.findById(id).orElse(null)?.let { boardAccess.readable(actor, it) } == true }
    private fun view(row: ProjectReportRecord, deliveries: Map<UUID, com.starter.dto.EmailDeliveryResult> = emptyMap()) = ProjectReportView(row.id, row.projectId, row.kind, row.fromDate, row.toDate, row.createdAt, deliveries)
    companion object {
        fun validateWindow(from: LocalDate, to: LocalDate) { if (from.year !in 1900..9999 || to.year !in 1900..9999 || to < from || ChronoUnit.DAYS.between(from, to) > 366) throw BadRequestException("Choose a reporting period of up to 366 days") }
        fun inPeriod(start: LocalDate?, due: LocalDate?, done: LocalDate?, from: LocalDate, to: LocalDate) =
            listOfNotNull(start, due, done).any { it >= from && it <= to } || ((done == null || done > to) && (start == null || start <= to))
    }
}

@RestController @RequestMapping("/api/projects/{id}/reports")
class ProjectReportController(private val reports: ProjectReportService, private val rates: com.starter.security.RateLimits) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = reports.list(user.username, id)
    @PostMapping fun generate(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: ProjectReportInput): ProjectReportView { rates.check("project-report", user.username, 10, 3600); return reports.generate(user.username, id, input) }
    @GetMapping("/{reportId}/pdf") fun pdf(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable reportId: UUID) = ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-report-$reportId.pdf").body(reports.pdf(user.username, id, reportId))
}
