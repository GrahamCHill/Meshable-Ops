package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.documents.WeasyRendererClient
import com.starter.dto.*
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.model.postgres.User
import com.starter.notification.NotificationService
import com.starter.plugins.kanban.*
import com.starter.repository.postgres.UserRepository
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.branding.BrandingPreferencesService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class ProjectReportDeliveryTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val access = mock(ProjectAccess::class.java)
    private val projects = mock(ProjectService::class.java)
    private val work = mock(ProjectWorkService::class.java)
    private val users = mock(UserRepository::class.java)
    private val lead = User(email = "lead@example.test")
    private val member = User(email = "member@example.test")
    private val external = User(email = "external@example.test", role = "EXTERNAL")
    private val project = ProjectRecord(leadId = lead.id, name = "Delivery")
    private val board = KanbanBoard(projectId = project.id)
    private val boards = mock(KanbanRepository::class.java)
    private val boardAccess = mock(ProjectBoardAccess::class.java)
    private val renderer = mock(WeasyRendererClient::class.java) { call -> if (call.method.name == "report") "%PDF-fixture".toByteArray() else Answers.RETURNS_DEFAULTS.answer(call) }
    private val reports = mock(ProjectReportRepository::class.java) { call -> if (call.method.name == "saveAndFlush") call.arguments[0] else Answers.RETURNS_DEFAULTS.answer(call) }
    private val sent = mutableListOf<Pair<SendEmailRequest, List<EmailAttachment>>>()
    private val notifications = mock(NotificationService::class.java) { call ->
        if (call.method.name == "sendEmail") { sent += call.getArgument<SendEmailRequest>(1) to call.getArgument<List<EmailAttachment>>(2); EmailDeliveryResult("ACCEPTED", "Test sink") }
        else Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val branding = mock(BrandingPreferencesService::class.java)
    private val policy = mock(DocumentAccessService::class.java)
    private val service = ProjectReportService(access, projects, work, reports, users, renderer, branding, notifications, boards, boardAccess, mapper, policy, mock(PdfService::class.java))
    private val request = ProjectReportInput(kind = ProjectReportKind.PERIOD, from = LocalDate.parse("2026-10-01"), to = LocalDate.parse("2026-10-31"), sendEmail = true, recipientIds = setOf(member.id))
    init {
        `when`(access.actor(lead.email)).thenReturn(lead)
        `when`(access.require(project.id, lead, true)).thenReturn(project)
        listOf(lead, member, external).forEach {
            `when`(users.findById(it.id)).thenReturn(Optional.of(it))
            `when`(access.canRead(it, project)).thenReturn(true)
            `when`(boardAccess.readable(it, board)).thenReturn(true)
        }
        `when`(boards.findById(board.id)).thenReturn(Optional.of(board))
        `when`(users.findAllById(emptySet<UUID>())).thenReturn(emptyList())
        `when`(work.work(project.id, lead)).thenReturn(listOf(ProjectWorkItem(board.id, "Board", KanbanCard(UUID.randomUUID(), UUID.randomUUID(), "Task"))))
        `when`(projects.get(lead.email, project.id)).thenReturn(ProjectView(project.id, project.name, null, "Business", lead.id, ProjectCategory.ACTIVE, ProjectDetails(), emptyList(), 0, true, project.updatedAt))
        `when`(branding.current()).thenReturn(BrandingPreferences())
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
    }
    @Test fun `report email always includes lead and a PDF attachment for each authorized recipient`() {
        val result = service.generate(lead.email, project.id, request)
        assertThat(result.deliveries.keys).containsExactlyInAnyOrder(member.id, lead.id)
        assertThat(sent.map { it.first.recipient }).containsExactlyInAnyOrder(member.email, lead.email)
        assertThat(sent.all { it.second.size == 1 && it.second.single().filename.endsWith(".pdf") }).isTrue()
    }
    @Test fun `external recipients are refused before PDF generation or delivery`() {
        assertThatThrownBy { service.generate(lead.email, project.id, request.copy(recipientIds = setOf(external.id))) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(renderer, notifications)
    }
    @Test fun `a recipient without access to every source board cannot receive the report`() {
        `when`(boardAccess.readable(member, board)).thenReturn(false)
        assertThatThrownBy { service.generate(lead.email, project.id, request) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(renderer, notifications)
    }
}
