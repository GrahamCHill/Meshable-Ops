package com.starter.plugins.operations

import com.starter.plugins.documents.*
import com.starter.rendering.WeasyRendererClient
import com.starter.service.*
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BillingDocuments(private val store:OperationsStore,private val access:OperationsAccess,private val renderer:WeasyRendererClient,
    private val templates:DocumentTemplateService,private val fields:DocumentFields,private val generated:GeneratedDocumentRepository,
    private val pdf:PdfService,private val policy:DocumentAccessService) {
    fun render(email:String,owner:UUID,d:BillingSnapshot):UUID {
        val actor=access.actor(email,"billing",true)
        access.registry.requireTable("documents","generated_documents",actor.role,true,true)
        val paymentText=d.payment?.let {listOf(it.label,it.instructions,it.paymentUrl).filter(String::isNotBlank).joinToString("\n")}.orEmpty()
        val values=mapOf("payment_instructions" to paymentText,"invoice_number" to d.number,"document_kind" to d.kind.name,"client_name" to d.clientName,"billing_email" to d.profile.billingEmail,"billing_address" to d.profile.billingAddress,"project_name" to d.projectName,"period_from" to d.from.toString(),"period_to" to d.to.toString(),"currency" to d.totals.currency,"subtotal" to d.totals.net.toPlainString(),"vat" to d.totals.tax.toPlainString(),"total" to d.totals.total.toPlainString(),"invoice_lines" to d.totals.lines.joinToString("\n"){"${it.source.description} | ${it.source.quantity} × ${it.netUnitPrice} | ${it.net} + ${it.tax}"})
        val template=d.templateId?.let {templates.get(email,it)}
        val itemized=renderer.billing(mapOf("paymentInstructions" to paymentText,"number" to d.number,"kind" to d.kind,"issuer" to d.issuerName,"issuerAddress" to d.issuerAddress,"vatNumber" to d.vatNumber,"client" to d.clientName,"billingAddress" to d.profile.billingAddress,"billingEmail" to d.profile.billingEmail,"project" to d.projectName,"period" to "${d.from} – ${d.to}","issued" to d.createdAt.toString().take(10),"due" to d.dueDate.toString(),"currency" to d.totals.currency,"taxPercent" to d.totals.taxPercent.toPlainString(),"net" to d.totals.net.toPlainString(),"tax" to d.totals.tax.toPlainString(),"total" to d.totals.total.toPlainString(),"lines" to d.totals.lines.map {mapOf("description" to it.source.description,"quantity" to it.source.quantity.stripTrailingZeros().toPlainString(),"unit" to it.netUnitPrice.stripTrailingZeros().toPlainString(),"net" to it.net.toPlainString(),"tax" to it.tax.toPlainString(),"total" to it.total.toPlainString())}))
            val bytes=if(template==null)itemized else {val source=store.mapper.readTree(template.templateJson);val keys=source.path("apiFields").map {it.path("key").asText()}.toSet();val coverValues=values.filterKeys {it in keys}.mapValues {store.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(if(it.key=="invoice_lines"&&it.value.length>3800)it.value.take(3800)+"\nFull itemization follows on the invoice pages." else it.value)};pdf.append(renderer.render(source,fields.resolve(source,templates.rules(template),coverValues)),itemized)}
        val finalBytes=if(d.kind!=BillingKind.INVOICE)pdf.watermark(bytes,"${d.kind.name} - NOT PAYABLE",d.number)else bytes
        val info=pdf.inspect(finalBytes,d.number+".pdf")
        if(finalBytes.size>com.starter.email.EmailAttachment.MAX_BYTES)throw BadRequestException("Billing PDF exceeds 10 MB")
        access.registry.requireTable("documents","generated_documents",access.actors.current(email).role,true,true)
        return generated.saveAndFlush(GeneratedDocument(ownerId=owner,templateId=template?.id ?: store.stable("billing-default-layout"),templateRevision=template?.revision ?: 0,templateName=template?.name ?: "Billing layout",name=d.number,filename=d.number+".pdf",pageCount=info.pageCount,byteSize=finalBytes.size,valuesJson=store.mapper.writeValueAsString(values),templateSnapshot=template?.templateJson ?: "{}",pdfBytes=finalBytes)).id
    }
    fun bytes(email:String,d:BillingSnapshot):ByteArray {
        val actor=access.actor(email,"billing")
        access.registry.requireTable("documents","generated_documents",actor.role,false,true)
        val p=policy.current();if(p.downloadMode==DownloadMode.DISABLED)throw ForbiddenException("Document downloads are disabled")
        val file=generated.findById(d.generatedDocumentId ?: throw NotFoundException("PDF unavailable")).orElseThrow {NotFoundException("PDF unavailable")}
        return if(p.downloadMode==DownloadMode.WATERMARKED)pdf.watermark(file.pdfBytes,p.watermarkText,email)else file.pdfBytes
    }
}
