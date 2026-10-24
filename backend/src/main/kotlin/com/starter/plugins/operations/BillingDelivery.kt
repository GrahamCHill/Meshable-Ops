package com.starter.plugins.operations

import com.starter.exception.*
import com.starter.notification.*
import com.starter.email.EmailAttachment
import com.starter.service.PdfService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BillingDeliveryClaims(private val store:OperationsStore,private val billing:BillingService,private val profiles:BillingProfiles) {
    @Transactional fun claim(email:String,id:UUID,input:BillingSend):OpsRow {
        val row=billing.get(email,id);profiles.authorize(email,row.projectId!!,true);store.lock("billing-send:$id")
        val current=store.get("billing",id);val d=store.decode(current,BillingSnapshot::class.java)
        if(current.revision!=input.revision)throw ConflictException("Invoice changed. Reload before sending")
        if(d.status !in setOf("DRAFT","ISSUED","DELIVERY_UNCERTAIN"))throw ConflictException("This document was sent, is sending, paid or void")
        if(d.status=="DELIVERY_UNCERTAIN"&&input.retryConfirmation!="RETRY DELIVERY")throw ConflictException("Check the mail provider before retrying; type RETRY DELIVERY if the earlier message was not accepted")
        if(profiles.profile(d.clientId).billingEmail!=d.profile.billingEmail)throw ConflictException("The registered billing email changed. Generate a new document with the current billing details")
        return store.save("billing",current.ownerId,current.projectId,"DOCUMENT",d.copy(status="SENDING"),id,current.revision)
    }
    @Transactional fun finish(row:OpsRow,status:String):BillingDocumentView {
        val d=store.decode(row,BillingSnapshot::class.java)
        return billing.view(store.save("billing",row.ownerId,row.projectId,"DOCUMENT",d.copy(status=status),row.id,row.revision))
    }
}
@Service
class BillingDelivery(private val store:OperationsStore,private val billing:BillingService,private val profiles:BillingProfiles,private val documents:BillingDocuments,
    private val claims:BillingDeliveryClaims,private val notifications:NotificationService,private val pdf:PdfService) {
    fun send(email:String,id:UUID,input:BillingSend):Map<String,Any> {
        val row=billing.get(email,id);profiles.authorize(email,row.projectId!!,true);val d=store.decode(row,BillingSnapshot::class.java)
        if(!notifications.availableFor("billing.document","email"))throw BadRequestException("Enable billing email notifications and configure outbound mail first")
        if(input.pdfPassword.isNotBlank()&&(input.pdfPassword.length !in 12..200||input.pdfPassword.any(Char::isISOControl)))throw BadRequestException("Use a PDF password of 12–200 characters")
        var bytes=documents.bytes(email,d)
        if(input.pdfPassword.isNotBlank())bytes=pdf.protect(bytes,input.pdfPassword)
        val attempt=claims.claim(email,id,input)
        val message="${d.kind.name.lowercase().replaceFirstChar {it.uppercase()}} ${d.number} for ${d.projectName}, ${d.from} to ${d.to}.\nTotal: ${d.totals.currency} ${d.totals.total}.\n"+
            (if(d.kind!=BillingKind.INVOICE)"This document is for review and is not payable.\n" else "Due: ${d.dueDate}.\n")+"The PDF is attached. Reply to this email for changes, cancellation requests or more information."
        val result=try {notifications.deliver("billing.document","email",NotificationTarget(email=d.profile.billingEmail,name=d.clientName),mapOf("subject" to "${d.number} [MO-BILL-${d.replyToken}]","message" to message),"billing:$id:attempt:${attempt.revision}",listOf(EmailAttachment(d.number+".pdf",bytes)))}
            catch(_:Exception){com.starter.dto.EmailDeliveryResult("UNAVAILABLE","Delivery acceptance could not be confirmed")}
        val updated=claims.finish(attempt,if(result.status=="ACCEPTED")"SENT" else "DELIVERY_UNCERTAIN")
        return mapOf("document" to updated,"delivery" to result)
    }
}

@org.springframework.stereotype.Component
class BillingNotifications:NotificationDefinitionProvider {
    override val pluginId="billing"
    override fun definitions()=listOf(NotificationDefinition("billing.document","Invoices and quotes","Requested and scheduled client billing emails with attached PDFs.",NotificationCategory.TRANSACTIONAL,setOf("subject","message","applicationName","recipientName"),"{{subject}}","{{message}}"),
        NotificationDefinition("billing.reply","Client billing replies","Billing replies and cancellation/information requests routed to the Business Manager and project lead.",NotificationCategory.GENERAL,setOf("subject","message","applicationName","recipientName"),"{{subject}}","{{message}}",privateToRecipient=true))
}
