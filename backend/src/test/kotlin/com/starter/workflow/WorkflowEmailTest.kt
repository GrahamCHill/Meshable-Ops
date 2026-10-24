package com.starter.workflow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.documents.*
import com.starter.dto.*
import com.starter.email.*
import com.starter.exception.*
import com.starter.model.postgres.*
import com.starter.notification.*
import com.starter.security.RateLimits
import com.starter.service.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream
import java.util.*

class WorkflowEmailTest {
    private val rows=mock(WorkflowRunRepository::class.java)
    private val definitions=mock(WorkflowRepository::class.java)
    private val access=mock(DocumentAccess::class.java)
    private val documents=mock(GeneratedDocumentRepository::class.java)
    private val notifications=mock(NotificationService::class.java)
    private val policy=mock(DocumentAccessService::class.java)
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val runtime=WorkflowRuntime(mapper)
    private val guard=WorkflowRunAccess(rows,definitions,access,documents,runtime)
    private val actor=User(email="member@example.test")
    private val pdf=PDDocument().use {it.addPage(PDPage());ByteArrayOutputStream().use {out -> it.save(out);out.toByteArray()}}
    private val document=GeneratedDocument(name="Quote",filename="quote.pdf",pdfBytes=pdf)
    private val email=WorkflowBlock("email",BlockKind.EMAIL,"Send quote",documentId=document.id,recipient="customer@example.test",subject="Your quote",note="Please find attached",protection=DocumentEmailProtection.PASSWORD)
    private val definition=WorkflowDefinition(state=WorkflowState.READY,published=true)
    private val graph=WorkflowGraph(listOf(email,WorkflowBlock("end",BlockKind.END,"End")),listOf(WorkflowEdge("out","email","end")))
    private val row=WorkflowRun(ownerId=actor.id,workflowId=definition.id,graphJson=mapper.writeValueAsString(graph),currentNodeId="email")
    private val runner=WorkflowRunService(definitions,rows,guard,access,documents,runtime,WorkflowValidation(),mapper,mock(RateLimits::class.java))
    private val claims=WorkflowEmailClaims(rows,guard,runtime)
    private val service=WorkflowEmailService(claims,runner,guard,GeneratedDocumentDelivery(access,PdfService(),policy,notifications,mock(RateLimits::class.java)),notifications,policy)
    private val password="Example-secret-for-test-123"
    private var attachments=emptyList<EmailAttachment>()
    private var message: SendEmailRequest?=null
    init {
        `when`(access.actor(actor.email)).thenReturn(actor)
        `when`(rows.findById(row.id)).thenReturn(Optional.of(row));`when`(rows.locked(row.id)).thenReturn(Optional.of(row))
        `when`(rows.saveAndFlush(any(WorkflowRun::class.java))).thenAnswer {row.revision++;row}
        `when`(definitions.findById(definition.id)).thenReturn(Optional.of(definition))
        `when`(documents.findById(document.id)).thenReturn(Optional.of(document))
        `when`(notifications.available(NotificationChannel.EMAIL)).thenReturn(true)
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        `when`(notifications.send(any<NotificationChannel>() ?: NotificationChannel.EMAIL,any<SendEmailRequest>() ?: SendEmailRequest("a@b.test","Subject","Text"),anyList())).thenAnswer {
            message=it.getArgument(1);attachments=it.getArgument(2);EmailDeliveryResult("ACCEPTED","Accepted")
        }
    }
    private fun request() = WorkflowSendRequest(row.revision,"email",password)
    @Test fun `explicit send uses fixed design defaults and attaches a protected PDF without persisting its password`() {
        val result=service.send(actor.email,row.id,request())
        assertThat(result.current?.kind).isEqualTo(BlockKind.END)
        assertThat(result.history.single().outcome).isEqualTo("EMAIL_ACCEPTED")
        assertThat(message?.recipient).isEqualTo(email.recipient);assertThat(message?.subject).isEqualTo(email.subject)
        assertThat(message?.text).isEqualTo(email.note)
        assertThat(attachments).hasSize(1)
        assertThatThrownBy {Loader.loadPDF(attachments.single().bytes)}.isInstanceOf(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException::class.java)
        Loader.loadPDF(attachments.single().bytes,password).use {assertThat(it.isEncrypted).isTrue()}
        assertThat(row.graphJson+row.historyJson+row.valuesJson+row.documentOverridesJson).doesNotContain(password)
        assertThat(mapper.writeValueAsString(request())).doesNotContain(password)
        assertThat(document.pdfBytes).isEqualTo(pdf)
    }
    @Test fun `uncertain external result leaves a durable claim and rejects a second send`() {
        `when`(notifications.send(any<NotificationChannel>() ?: NotificationChannel.EMAIL,any<SendEmailRequest>() ?: SendEmailRequest("a@b.test","Subject","Text"),anyList())).thenThrow(EmailDeliveryException("Timeout"))
        assertThatThrownBy {service.send(actor.email,row.id,request())}.isInstanceOf(EmailDeliveryException::class.java)
        assertThat(row.emailState).isEqualTo(WorkflowEmailState.UNCERTAIN);assertThat(row.currentNodeId).isEqualTo("email")
        assertThatThrownBy {service.send(actor.email,row.id,request())}.isInstanceOf(ConflictException::class.java)
        verify(notifications,times(1)).send(any<NotificationChannel>() ?: NotificationChannel.EMAIL,any<SendEmailRequest>() ?: SendEmailRequest("a@b.test","Subject","Text"),anyList())
        val result=runner.advance(actor.email,row.id,WorkflowAdvanceRequest(row.revision,"email",WorkflowAction.CONFIRM_EMAIL,note="Confirmed in provider outbox"))
        assertThat(result.current?.kind).isEqualTo(BlockKind.END)
        assertThat(result.history.single().outcome).isEqualTo("DELIVERY_CONFIRMED_MANUALLY")
    }
    @Test fun `missing email invalid password and disabled downloads fail before claiming or sending`() {
        assertThatThrownBy {service.send(actor.email,row.id,request().copy(password="short"))}.isInstanceOf(BadRequestException::class.java)
        `when`(notifications.available(NotificationChannel.EMAIL)).thenReturn(false)
        assertThatThrownBy {service.send(actor.email,row.id,request())}.isInstanceOf(BadRequestException::class.java)
        `when`(notifications.available(NotificationChannel.EMAIL)).thenReturn(true)
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(downloadMode=DownloadMode.DISABLED))
        assertThatThrownBy {service.send(actor.email,row.id,request())}.isInstanceOf(ForbiddenException::class.java)
        verify(rows,never()).saveAndFlush(any(WorkflowRun::class.java));assertThat(attachments).isEmpty()
    }
    @Test fun `only one durable claim can be taken and a mismatched completion cannot advance it`() {
        val claim=claims.claim(actor.email,row.id,request())
        assertThat(row.emailState).isEqualTo(WorkflowEmailState.SENDING)
        assertThatThrownBy {claims.claim(actor.email,row.id,request())}.isInstanceOf(ConflictException::class.java)
        claims.finish(row.id,claim.copy(attemptId=UUID.randomUUID()),true)
        assertThat(row.currentNodeId).isEqualTo("email")
        claims.finish(row.id,claim,false)
        assertThat(row.emailState).isEqualTo(WorkflowEmailState.UNCERTAIN)
        claims.finish(row.id,claim,true)
        assertThat(row.currentNodeId).isEqualTo("email")
    }
}
