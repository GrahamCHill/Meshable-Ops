package com.starter.plugins.operations

import com.starter.exception.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.math.BigDecimal
import java.util.UUID

@Service
class BillingItems(private val store:OperationsStore,private val access:OperationsAccess) {
    fun list(email:String,project:UUID):List<Map<String,Any>> {access.project(email,"billing-items",project,true,false);return store.list("billing-items","kind='ITEM' AND project_id=?",project).map {mapOf("id" to it.id,"item" to store.decode(it,CatalogItem::class.java),"revision" to it.revision)}}
    @Transactional fun save(email:String,id:UUID?,input:CatalogInput):OpsRow {
        val (actor,_)=access.project(email,"billing-items",input.projectId,true,true);val item=input.item
        OperationsValidation.text(item.name,"item name",200);OperationsValidation.currency(item.currency)
        if(item.amount.signum()<0||item.amount>BigDecimal("1000000000")||item.amount.scale()>6||item.quantity.signum()<=0||item.quantity>BigDecimal("100000000")||item.quantity.scale()>6||item.activeTo?.isBefore(item.activeFrom)==true)throw BadRequestException("Choose valid item prices, quantities and effective dates")
        val row=id?.let {store.get("billing-items",it).also {r->if(r.kind!="ITEM"||r.projectId!=input.projectId)throw NotFoundException("Billing item unavailable")}}
        return store.save("billing-items",row?.ownerId ?: actor.id,input.projectId,"ITEM",item,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    fun sources(email:String,input:BillingGenerate):List<BillingLine> {
        val result=mutableListOf<BillingLine>()
        if(input.includeItems && access.enabled("billing-items")) {
            access.project(email,"billing-items",input.projectId,false,false)
            store.list("billing-items","kind='ITEM' AND project_id=?",input.projectId).forEach {row->val item=store.decode(row,CatalogItem::class.java)
                if(!item.enabled||!BillingRates.overlaps(item.activeFrom,item.activeTo,input.from,input.to))return@forEach
                if(item.currency!=input.currency)throw BadRequestException("Billing item currencies differ from this invoice; currency conversion is never automatic")
                if(item.recurrence==ChargeRecurrence.ONCE) {if(item.activeFrom in input.from..input.to)result+=BillingLine("item:${row.id}:once",item.name,item.quantity,item.amount,item.pricesIncludeTax,"ITEM",row.id)}
                else {
                    var month=YearMonth.from(input.from)
                    while(month<=YearMonth.from(input.to)) {
                        if(BillingRates.overlaps(item.activeFrom,item.activeTo,month.atDay(1),month.atEndOfMonth()))result+=BillingLine("item:${row.id}:$month","${item.name} ($month)",item.quantity,item.amount,item.pricesIncludeTax,"ITEM",row.id)
                        month=month.plusMonths(1)
                    }
                }
            }
        }
        if(input.includeHours&&access.enabled("timesheets")) {
            access.project(email,"timesheets",input.projectId,false,false)
            val rows=store.list("timesheets","kind='ENTRY' AND project_id=? AND CAST(data_json AS jsonb)->>'status'='APPROVED' AND (CAST(data_json AS jsonb)->>'start')::timestamptz>=? AND (CAST(data_json AS jsonb)->>'start')::timestamptz<?",input.projectId,java.sql.Timestamp.from(input.from.atStartOfDay(ZoneOffset.UTC).toInstant()),java.sql.Timestamp.from(input.to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),limit=5000)
            rows.forEach {row->val e=store.decode(row,TimeEntry::class.java)
                if(!e.billable)return@forEach
                if(e.rate==null||e.roleId==null)throw BadRequestException("Approved billable work has no rate snapshot. Assign an effective developer rate, then reject and reapprove that work.")
                if(e.currency!=input.currency)throw BadRequestException("Approved time uses another currency")
                result+=BillingLine("time:${row.id}","${e.start.atZone(ZoneOffset.UTC).toLocalDate()}: ${e.description}",BigDecimal(e.seconds).divide(BigDecimal(3600),6,java.math.RoundingMode.HALF_UP),e.rate,false,"TIME",row.id)
            }
        }
        if(input.includeCloudCosts) {
            access.project(email,"cloud-costs",input.projectId,false,false)
            store.list("cloud-costs","kind='SPEND' AND project_id=? AND CAST(data_json AS jsonb)->>'date'>=? AND CAST(data_json AS jsonb)->>'date'<=?",input.projectId,input.from.toString(),input.to.toString(),limit=5000).forEach {row->val cost=store.decode(row,CloudSpend::class.java)
                if(cost.currency!=input.currency)throw BadRequestException("Cloud spend uses another currency; review conversion outside automatic billing")
                result+=BillingLine("cloud:${row.id}","${cost.provider} ${cost.service} ${cost.date}${if(cost.estimated)" (estimated)" else ""}",BigDecimal.ONE,cost.amount,false,"CLOUD",row.id)
            }
        }
        return result
    }
}
