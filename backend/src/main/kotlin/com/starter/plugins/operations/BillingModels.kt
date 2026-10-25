package com.starter.plugins.operations

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.*
import java.util.UUID

@JsonIgnoreProperties(value=["recurrence","activeFrom","activeTo"])
data class CatalogItem(val name:String,val amount:BigDecimal,val currency:String="USD",val quantity:BigDecimal=BigDecimal.ONE,
    val pricesIncludeTax:Boolean=false,val enabled:Boolean=true)
data class CatalogInput(val item:CatalogItem,val projectId:UUID?=null,val revision:Long = -1)
data class BillingItemLink(val itemId:UUID,val projectId:UUID?=null,val clientId:UUID?=null,val baseItem:CatalogItem,val baseRevision:Long,
    val proposedItem:CatalogItem?=null,val proposalVersion:Long=0,val approvedOverrides:Map<UUID,CatalogItem> = emptyMap(),val approvals:Map<UUID,Long> = emptyMap())
data class BillingLinkInput(val itemId:UUID,val projectId:UUID?=null,val clientId:UUID?=null)
data class BillingOverrideInput(val item:CatalogItem,val revision:Long)
data class BillingOverrideApproval(val projectId:UUID,val revision:Long,val proposalVersion:Long)
data class BillingProfile(val clientId:UUID,val billingEmail:String,val billingAddress:String,val businessManagerId:UUID,val currency:String="USD",val taxNumber:String="",val paymentTermsDays:Int=30)
data class BillingProfileInput(val profile:BillingProfile,val revision:Long = -1)
enum class BillingKind { INVOICE, QUOTE, PROVISIONAL }
data class BillingLine(val key:String,val description:String,val quantity:BigDecimal,val unitPrice:BigDecimal,val pricesIncludeTax:Boolean=false,val source:String="MANUAL",val sourceId:UUID?=null)
data class CalculatedLine(val source:BillingLine,val netUnitPrice:BigDecimal,val net:BigDecimal,val tax:BigDecimal,val total:BigDecimal)
data class BillingTotals(val lines:List<CalculatedLine>,val net:BigDecimal,val tax:BigDecimal,val total:BigDecimal,val currency:String,val taxPercent:BigDecimal)
data class BillingGenerate(val projectId:UUID,val from:LocalDate,val to:LocalDate,val kind:BillingKind=BillingKind.INVOICE,
    val currency:String="USD",val chargeTax:Boolean=false,val taxPercent:BigDecimal=BigDecimal.ZERO,val includeItems:Boolean=true,val includeHours:Boolean=true,
    val includeCloudCosts:Boolean=false,val manualLines:List<BillingLine> = emptyList(),val duplicateConfirmation:String="",val duplicateReason:String="",val requestId:UUID=UUID.randomUUID(),val templateId:UUID?=null,val paymentProviderId:String?=null)
data class BillingSnapshot(val number:String,val kind:BillingKind,val projectId:UUID,val clientId:UUID,val clientName:String,val projectName:String,
    val from:LocalDate,val to:LocalDate,val createdAt:Instant,val dueDate:LocalDate,val profile:BillingProfile,val issuerName:String,val issuerAddress:String,val vatNumber:String,
    val totals:BillingTotals,val replyToken:UUID=UUID.randomUUID(),val status:String="DRAFT",val generatedDocumentId:UUID?=null,val duplicateReason:String="",val templateId:UUID?=null,
    val signedOffBy:UUID?=null,val signedOffAt:Instant?=null,val payment:BillingPayment?=null)
data class BillingDocumentView(val id:UUID,val document:BillingSnapshot,val revision:Long)
data class BillingSend(val revision:Long,@get:JsonProperty(access=JsonProperty.Access.WRITE_ONLY) val pdfPassword:String="",val retryConfirmation:String="") {override fun toString()="BillingSend(revision=$revision)"}
data class BillingSchedule(val projectId:UUID,val enabled:Boolean=false,val autoSend:Boolean=false,val dayOfMonth:Int=1,val time:String="08:00",val timeZone:String="UTC",val chargeTax:Boolean=false,val taxPercent:BigDecimal=BigDecimal.ZERO,val includeCloudCosts:Boolean=false,val nextRunAt:Instant?=null,val lastStatus:String="NEVER",val lastRunAt:Instant?=null)
data class BillingScheduleInput(val schedule:BillingSchedule,val revision:Long = -1)
data class BillingReply(val invoiceId:UUID,val clientId:UUID,val sender:String,val subject:String,val body:String,val receivedAt:Instant,val category:String="INFORMATION",val routedTo:Set<UUID> = emptySet(),val status:String="NEW")
data class BillingState(val revision:Long,val action:String)

object BillingMath {
    fun calculate(lines:List<BillingLine>,currency:String,vat:Boolean,rate:BigDecimal):BillingTotals {
        OperationsValidation.currency(currency)
        if(rate.signum()<0||rate>BigDecimal(100)||rate.scale()>4||lines.isEmpty()||lines.size>500)throw com.starter.exception.BadRequestException("Use 1–500 lines and a tax percentage from 0 to 100")
        val scale=java.util.Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
        val percent=if(vat)rate else BigDecimal.ZERO
        val factor=BigDecimal.ONE.add(percent.divide(BigDecimal(100)))
        val result=lines.map {line->
            OperationsValidation.text(line.description,"line description",500)
            if(line.quantity.signum()<=0||line.quantity>BigDecimal("100000000")||line.quantity.scale()>6||line.unitPrice.abs()>BigDecimal("1000000000")||line.unitPrice.scale()>8)throw com.starter.exception.BadRequestException("Use bounded quantities and prices")
            val unit=if(line.pricesIncludeTax&&vat)line.unitPrice.divide(factor,8,java.math.RoundingMode.HALF_UP) else line.unitPrice
            val gross=line.unitPrice.multiply(line.quantity)
            val net=(if(line.pricesIncludeTax&&vat)gross.divide(factor,12,java.math.RoundingMode.HALF_UP)else gross).setScale(scale,java.math.RoundingMode.HALF_UP)
            val tax=(if(line.pricesIncludeTax&&vat)gross.setScale(scale,java.math.RoundingMode.HALF_UP).subtract(net)else net.multiply(percent).divide(BigDecimal(100))).setScale(scale,java.math.RoundingMode.HALF_UP)
            CalculatedLine(line,unit,net,tax,net+tax)
        }
        val net=result.fold(BigDecimal.ZERO){a,l->a+l.net};val tax=result.fold(BigDecimal.ZERO){a,l->a+l.tax}
        if(net+tax<BigDecimal.ZERO)throw com.starter.exception.BadRequestException("Invoice total must not be negative")
        return BillingTotals(result,net,tax,net+tax,currency,percent)
    }
}
