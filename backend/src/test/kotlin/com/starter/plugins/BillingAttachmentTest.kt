package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.operations.*
import com.starter.email.EmailAttachment
import com.starter.dto.EmailDeliveryResult
import com.starter.notification.NotificationService
import com.starter.model.postgres.User
import com.starter.projects.ProjectRecord
import com.starter.service.PdfService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class BillingAttachmentTest {
    @Test fun `invoice emails contain the PDF attachment with optional password and no password in message`() {
        val mapper=jacksonObjectMapper().findAndRegisterModules();val id=UUID.randomUUID();val user=User(email="manager@example.test",password="unused");val project=ProjectRecord()
        val profile=BillingProfile(UUID.randomUUID(),"billing@example.test","Client address",user.id)
        val totals=BillingMath.calculate(listOf(BillingLine("item","Services",BigDecimal.ONE,BigDecimal(100))),"USD",false,BigDecimal.ZERO)
        val d=BillingSnapshot("INV-TEST",BillingKind.INVOICE,project.id,profile.clientId,"Client","Project",LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-31"),Instant.now(),LocalDate.now().plusDays(30),profile,"Issuer","Issuer address","",totals,signedOffBy=user.id,signedOffAt=Instant.now(),status="ISSUED")
        val row=OpsRow(id,user.id,project.id,"DOCUMENT",mapper.writeValueAsString(d),0,Instant.now())
        val store=mock(OperationsStore::class.java);`when`(store.decode(row,BillingSnapshot::class.java)).thenReturn(d)
        val billing=mock(BillingService::class.java);`when`(billing.get(user.email,id)).thenReturn(row)
        val profiles=mock(BillingProfiles::class.java);`when`(profiles.authorize(user.email,project.id,true)).thenReturn(user to project)
        val bytes=org.apache.pdfbox.pdmodel.PDDocument().use {doc->doc.addPage(org.apache.pdfbox.pdmodel.PDPage());java.io.ByteArrayOutputStream().use {out->doc.save(out);out.toByteArray()}}
        val documents=mock(BillingDocuments::class.java);`when`(documents.bytes(user.email,d)).thenReturn(bytes)
        val input=BillingSend(0,"test-password-12345")
        val claims=mock(BillingDeliveryClaims::class.java);`when`(claims.claim(user.email,id,input)).thenReturn(row)
        `when`(claims.finish(row,"SENT")).thenReturn(BillingDocumentView(id,d.copy(status="SENT"),1))
        var attachments=listOf<EmailAttachment>();var message=""
        val notifications=mock(NotificationService::class.java){call->when(call.method.name){"availableFor"->true;"deliver"->{@Suppress("UNCHECKED_CAST") val values=call.arguments[3] as Map<String,String>;message=values["message"].orEmpty();@Suppress("UNCHECKED_CAST") val files=call.arguments[5] as List<EmailAttachment>;attachments=files;EmailDeliveryResult("ACCEPTED","Local fixture accepted")};else->null}}
        BillingDelivery(store,billing,profiles,documents,claims,notifications,PdfService()).send(user.email,id,input)
        assertThat(attachments).hasSize(1);assertThat(attachments.single().filename).isEqualTo("INV-TEST.pdf")
        org.apache.pdfbox.Loader.loadPDF(attachments.single().bytes,input.pdfPassword).use {assertThat(it.isEncrypted).isTrue()}
        assertThat(message).contains("PDF is attached").doesNotContain(input.pdfPassword)
        assertThat(mapper.writeValueAsString(input)).doesNotContain("test-password", "pdfPassword")
    }
}
