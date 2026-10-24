package com.starter.plugins.operations

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.projects.*
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class BillingProfiles(private val store:OperationsStore,private val access:OperationsAccess,private val clients:ProjectService,private val users:UserRepository,private val config:PluginConfigurationService) {
    fun defaults(email:String):Map<String,Any> {
        access.actor(email,"billing")
        val c=config.current("billing")
        return mapOf("currency" to (c["defaultCurrency"] ?: "USD"),"vatRegistered" to (c["vatRegistered"]=="true"),"chargeTax" to (c["vatRegistered"]=="true"&&c["chargeTax"]=="true"),"taxPercent" to (c["defaultTaxPercent"] ?: "0"),"currencies" to java.util.Currency.getAvailableCurrencies().map {it.currencyCode}.sorted())
    }
    fun get(email:String,client:UUID):Map<String,Any?> {
        access.actor(email,"billing");clients.requireClient(email,client)
        val row=store.list("billing","id=? AND kind='PROFILE'",client).firstOrNull()
        return mapOf("profile" to row?.let {store.decode(it,BillingProfile::class.java)},"revision" to (row?.revision ?: -1),"canManage" to runCatching {clients.requireClient(email,client,true)}.isSuccess)
    }
    @Transactional fun save(email:String,input:BillingProfileInput):Map<String,Any?> {
        val actor=access.actor(email,"billing",true);val p=input.profile;clients.requireClient(email,p.clientId,true)
        com.starter.email.validateMailbox(p.billingEmail,"billing email")
        OperationsValidation.text(p.billingAddress,"registered billing address",2000);OperationsValidation.currency(p.currency)
        OperationsValidation.text(p.taxNumber,"client tax number",100,false)
        val manager=users.findById(p.businessManagerId).orElse(null)
        if(manager?.let {it.active && (it.role==Roles.BUSINESS_MANAGER||Roles.admin(it.role))}!=true)throw BadRequestException("Choose an active Business Manager or administrator for billing replies")
        if(p.paymentTermsDays !in 0..365)throw BadRequestException("Payment terms must be 0–365 days")
        store.lock("billing-profile:${p.clientId}")
        val old=store.list("billing","id=? AND kind='PROFILE'",p.clientId).firstOrNull()
        store.save("billing",old?.ownerId ?: actor.id,null,"PROFILE",p,p.clientId,input.revision)
        return get(email,p.clientId)
    }
    fun profile(client:UUID)=store.list("billing","id=? AND kind='PROFILE'",client).firstOrNull()?.let {store.decode(it,BillingProfile::class.java)} ?: throw BadRequestException("Configure the client's registered billing address and Business Manager first")
    fun authorize(email:String,projectId:UUID,write:Boolean=false):Pair<com.starter.model.postgres.User,ProjectRecord> {
        val (actor,project)=access.project(email,"billing",projectId,false,write)
        val client=project.clientId ?: throw BadRequestException("Link this project to a client before billing")
        val manager=runCatching {profile(client).businessManagerId}.getOrNull()
        if(access.projects.role(actor,project)==ProjectRole.MEMBER && manager!=actor.id)throw ForbiddenException("Billing requires a project leader or the client's assigned Business Manager")
        return actor to project
    }
}

@Service
class BillingService(private val store:OperationsStore,private val access:OperationsAccess,private val profiles:BillingProfiles,private val sources:BillingItems,
    private val clients:ClientRepository,private val config:PluginConfigurationService,private val documents:BillingDocuments) {
    fun list(email:String,project:UUID):List<BillingDocumentView> {profiles.authorize(email,project);return store.list("billing","project_id=? AND kind='DOCUMENT'",project).map(::view)}
    fun get(email:String,id:UUID):OpsRow {
        val row=store.get("billing",id);if(row.kind!="DOCUMENT"||row.projectId==null)throw NotFoundException("Billing document unavailable")
        profiles.authorize(email,row.projectId);return row
    }
    fun preview(email:String,input:BillingGenerate):Map<String,Any> {
        val (_,project)=profiles.authorize(email,input.projectId);val profile=profiles.profile(project.clientId!!)
        validate(input,profile)
        val lines=lines(email,input);val totals=BillingMath.calculate(lines,input.currency,input.chargeTax,input.taxPercent)
        return mapOf("totals" to totals,"alreadyReserved" to duplicates(lines),"provisional" to (input.kind==BillingKind.PROVISIONAL),"taxRegistered" to (config.current("billing")["vatRegistered"]=="true"))
    }
    @Transactional fun generate(email:String,input:BillingGenerate):BillingDocumentView {
        val (actor,project)=profiles.authorize(email,input.projectId,true);val profile=profiles.profile(project.clientId!!)
        validate(input,profile)
        store.lock("billing-project:${project.id}")
        // A repeated browser request returns the same document, including after a network timeout.
        val id=store.stable("billing-request:${actor.id}:${input.requestId}")
        store.list("billing","id=?",id).firstOrNull()?.let {if(it.projectId!=project.id)throw ConflictException("Request ID already used for another project");return view(it)}
        val lines=lines(email,input);val duplicate=if(input.kind==BillingKind.INVOICE)duplicates(lines)else emptyList()
        if(input.kind==BillingKind.INVOICE&&lines.any {it.source=="TIME"})access.registry.requireTable("timesheets",OperationsStore.table("timesheets"),actor.role,true)
        if(duplicate.isNotEmpty()&&(input.duplicateConfirmation!="BILL AGAIN"||input.duplicateReason.trim().length<10))throw ConflictException("Some source items are already reserved or invoiced. Use a provisional document, or type BILL AGAIN and record a reason of at least 10 characters.")
        if(input.duplicateReason.length>1000)throw BadRequestException("Duplicate billing reason is too long")
        val settings=config.current("billing")
        val totals=BillingMath.calculate(lines,input.currency,input.chargeTax,input.taxPercent)
        val now=Instant.now();val number="${when(input.kind){BillingKind.INVOICE->"INV";BillingKind.QUOTE->"QUO";BillingKind.PROVISIONAL->"PRO"}}-${LocalDate.now(ZoneOffset.UTC).year}-${id.toString().take(8).uppercase()}"
        val name=clients.findById(project.clientId!!).orElseThrow {NotFoundException("Client unavailable")}.name
        var snapshot=BillingSnapshot(number,input.kind,project.id,project.clientId!!,name,project.name,input.from,input.to,now,LocalDate.now(ZoneOffset.UTC).plusDays(profile.paymentTermsDays.toLong()),profile,
            settings["issuerName"].orEmpty().ifBlank {"Meshable Ops"},settings["issuerAddress"].orEmpty(),if(input.chargeTax)settings["vatNumber"].orEmpty()else "",totals,duplicateReason=input.duplicateReason.trim(),templateId=input.templateId)
        if(input.kind==BillingKind.INVOICE)reserve(id,actor.id,project.id,lines,duplicate.toSet())
        // Both the default structured layout and optional WeasyCanvas template create a real generated PDF.
        val document=documents.render(email,actor.id,snapshot)
        snapshot=snapshot.copy(generatedDocumentId=document)
        return view(store.save("billing",actor.id,project.id,"DOCUMENT",snapshot,id))
    }
    private fun validate(input:BillingGenerate,profile:BillingProfile) {
        OperationsValidation.dates(input.from,input.to);OperationsValidation.currency(input.currency)
        if(profile.currency!=input.currency)throw BadRequestException("Use the currency registered on this client's billing profile")
        if(input.chargeTax && config.current("billing")["vatRegistered"]!="true")throw BadRequestException("Enable VAT registration in billing settings before charging VAT")
        if(input.manualLines.size>100)throw BadRequestException("Use up to 100 manual lines")
        if(input.to>LocalDate.now(ZoneOffset.UTC)&&input.kind!=BillingKind.QUOTE)throw BadRequestException("Invoices and provisional statements can include completed periods only")
    }
    private fun lines(email:String,input:BillingGenerate)=sources.sources(email,input)+input.manualLines.mapIndexed {index,line->line.copy(key="manual:${input.requestId}:$index",source="MANUAL",sourceId=null)}
    private fun duplicates(lines:List<BillingLine>)=lines.filter {it.source!="MANUAL"}.filter {store.list("billing","id=? AND kind='CLAIM'",store.stable("billing-claim:${it.key}")).isNotEmpty()}.map {it.key}
    private fun reserve(invoice:UUID,owner:UUID,project:UUID,lines:List<BillingLine>,duplicates:Set<String>) {
        val users=lines.filter {it.source=="TIME"}.mapNotNull {it.sourceId}.map {store.decode(store.get("timesheets",it),TimeEntry::class.java).userId}.distinct().sortedBy {it.toString()}
        users.forEach {store.lock("time-ledger:$it")}
        lines.filter {it.source!="MANUAL"}.forEach {line->
            val key=if(line.key in duplicates)"billing-claim:${line.key}:duplicate:$invoice" else "billing-claim:${line.key}"
            store.save("billing",owner,project,"CLAIM",mapOf("invoiceId" to invoice,"sourceKey" to line.key),store.stable(key))
            if(line.source=="TIME") {
                val row=store.get("timesheets",line.sourceId!!);val time=store.decode(row,TimeEntry::class.java)
                if(time.status!=TimeStatus.APPROVED)throw ConflictException("Work approval changed during billing")
                if(time.billedInvoice==null)store.save("timesheets",row.ownerId,row.projectId,"ENTRY",time.copy(billedInvoice=invoice),row.id,row.revision)
            }
        }
    }
    @Transactional fun state(email:String,id:UUID,input:BillingState):BillingDocumentView {
        val row=get(email,id);profiles.authorize(email,row.projectId!!,true);val d=store.decode(row,BillingSnapshot::class.java)
        val status=when(input.action){"MARK_PAID"->{if(d.kind!=BillingKind.INVOICE||d.status !in setOf("SENT","ISSUED","DELIVERY_UNCERTAIN"))throw ConflictException("Only an issued invoice can be marked paid");"PAID"}
            "VOID"->{if(d.status in setOf("SENDING","PAID","VOID"))throw ConflictException("This document cannot be voided");"VOID"}
            else->throw BadRequestException("Choose MARK_PAID or VOID")}
        // Voiding never silently frees reserved charges; a reviewed BILL AGAIN action is required.
        return view(store.save("billing",row.ownerId,row.projectId,"DOCUMENT",d.copy(status=status),row.id,input.revision))
    }
    fun view(row:OpsRow)=BillingDocumentView(row.id,store.decode(row,BillingSnapshot::class.java),row.revision)
}
