package com.starter.plugins.operations

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.notification.NotificationPublisher
import com.starter.plugins.PluginConfigurationService
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class OperationsJobClaims(private val store:OperationsStore) {
    @Transactional fun once(plugin:String,key:String,owner:UUID):OpsRow? {
        store.lock("ops-job:$plugin:$key");val id=store.stable("ops-job:$plugin:$key")
        if(store.list(plugin,"id=?",id).isNotEmpty())return null
        return store.save(plugin,owner,null,"JOB",mapOf("key" to key,"status" to "RUNNING"),id)
    }
    @Transactional fun finish(plugin:String,row:OpsRow,status:String) {store.save(plugin,row.ownerId,null,"JOB",mapOf("status" to status),row.id,row.revision)}
    @Transactional fun alert(id:UUID):OpsRow? {
        store.lock("on-call-alert:$id");val row=store.get("on-call",id);val a=store.decode(row,ServiceAlert::class.java)
        if(!a.confirmed||a.status!="OPEN"||a.nextPageAt==null||a.nextPageAt>Instant.now())return null
        val seconds=when(a.severity){AlertSeverity.SEV1->60L;AlertSeverity.SEV2->300L;AlertSeverity.SEV3->1800L;AlertSeverity.SEV4->0L}
        return store.save("on-call",row.ownerId,row.projectId,"ALERT",a.copy(pageCount=a.pageCount+1,nextPageAt=if(seconds==0L)null else Instant.now().plusSeconds(seconds),lastDelivery="PAGING"),row.id,row.revision)
    }
    @Transactional fun pageResult(row:OpsRow,status:String) {
        val current=store.get("on-call",row.id)
        if(current.revision!=row.revision)return // Acknowledgment/severity edits take precedence over a late provider result.
        val a=store.decode(current,ServiceAlert::class.java)
        store.save("on-call",row.ownerId,row.projectId,"ALERT",a.copy(lastDelivery=status),row.id,row.revision)
    }
}

@Service
class BillingSchedules(private val store:OperationsStore,private val profiles:BillingProfiles,private val billing:BillingService,private val delivery:BillingDelivery,private val users:UserRepository) {
    fun list(email:String,project:UUID):List<Map<String,Any>> {profiles.authorize(email,project);return store.list("billing","kind='SCHEDULE' AND project_id=?",project).map {mapOf("id" to it.id,"schedule" to store.decode(it,BillingSchedule::class.java),"revision" to it.revision)}}
    @Transactional fun save(email:String,id:UUID?,input:BillingScheduleInput):OpsRow {
        val s=input.schedule;val (actor,p)=profiles.authorize(email,s.projectId,true);profiles.profile(p.clientId!!)
        validate(s)
        val old=id?.let {store.get("billing",it).also {row->if(row.kind!="SCHEDULE"||row.projectId!=s.projectId)throw NotFoundException("Schedule unavailable")}}
        if(old==null&&store.list("billing","kind='SCHEDULE' AND project_id=?",s.projectId).isNotEmpty())throw ConflictException("Edit the existing project schedule")
        val value=s.copy(nextRunAt=if(s.enabled)next(s,Instant.now())else null,lastStatus=old?.let {store.decode(it,BillingSchedule::class.java).lastStatus} ?: "NEVER",lastRunAt=old?.let {store.decode(it,BillingSchedule::class.java).lastRunAt})
        return store.save("billing",old?.ownerId ?: actor.id,s.projectId,"SCHEDULE",value,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
    }
    @Transactional fun claim(id:UUID):OpsRow? {
        store.lock("billing-schedule:$id");val row=store.get("billing",id);val s=store.decode(row,BillingSchedule::class.java)
        if(!s.enabled||s.nextRunAt==null||s.nextRunAt>Instant.now())return null
        return store.save("billing",row.ownerId,row.projectId,"SCHEDULE",s.copy(nextRunAt=next(s,Instant.now()),lastRunAt=Instant.now(),lastStatus="RUNNING"),id,row.revision)
    }
    fun run(row:OpsRow):String {
        val s=store.decode(row,BillingSchedule::class.java);val owner=users.findById(row.ownerId).orElse(null)?.takeIf {it.active&&it.role!="EXTERNAL"} ?: return "OWNER_UNAVAILABLE"
        profiles.authorize(owner.email,s.projectId,true)
        val month=YearMonth.from((s.lastRunAt ?: Instant.now()).atZone(ZoneId.of(s.timeZone))).minusMonths(1)
        val profile=profiles.profile(profiles.authorize(owner.email,s.projectId).second.clientId!!)
        val request=BillingGenerate(s.projectId,month.atDay(1),month.atEndOfMonth(),currency=profile.currency,chargeTax=s.chargeTax,taxPercent=s.taxPercent,includeCloudCosts=s.includeCloudCosts,requestId=store.stable("billing-schedule:${row.id}:$month"))
        val document=billing.generate(owner.email,request)
        if(!s.autoSend)return "DRAFT_GENERATED"
        val latest=store.get("billing",row.id);if(latest.revision!=row.revision||!store.decode(latest,BillingSchedule::class.java).enabled)return "DRAFT_SETTINGS_CHANGED"
        val response=delivery.send(owner.email,document.id,BillingSend(document.revision))
        return (response["delivery"] as com.starter.dto.EmailDeliveryResult).status
    }
    @Transactional fun finish(row:OpsRow,status:String) {
        val current=store.get("billing",row.id);if(current.revision!=row.revision)return
        val s=store.decode(current,BillingSchedule::class.java)
        store.save("billing",row.ownerId,row.projectId,"SCHEDULE",s.copy(lastStatus=status),row.id,row.revision)
    }
    companion object {
        fun validate(s:BillingSchedule) {if(s.dayOfMonth !in 1..28||runCatching {LocalTime.parse(s.time)}.isFailure||runCatching {ZoneId.of(s.timeZone)}.isFailure||s.taxPercent.signum()<0||s.taxPercent>java.math.BigDecimal(100))throw BadRequestException("Choose month day 1–28, valid time/timezone and VAT percentage")}
        fun next(s:BillingSchedule,after:Instant):Instant {validate(s);val z=ZoneId.of(s.timeZone);var d=after.atZone(z).toLocalDate().withDayOfMonth(s.dayOfMonth);if(!d.atTime(LocalTime.parse(s.time)).atZone(z).toInstant().isAfter(after))d=d.plusMonths(1);return d.atTime(LocalTime.parse(s.time)).atZone(z).toInstant()}
    }
}

@Service
class OperationsJobs(private val store:OperationsStore,private val access:OperationsAccess,private val claims:OperationsJobClaims,private val config:PluginConfigurationService,
    private val users:UserRepository,private val timecamp:TimeCampService,private val cloud:CloudCosts,private val inbox:BillingInbox,private val schedules:BillingSchedules,
    private val oncall:OnCallService,private val publisher:NotificationPublisher,private val callers:List<OnCallCallingProvider>) {
    @Scheduled(fixedDelay=60000,initialDelay=90000) fun tick() {
        val admin=users.findAll().firstOrNull {it.active&&Roles.admin(it.role)} ?: return
        for(plugin in listOf("timecamp","cloud-costs"))runCatching {
            if(!access.enabled(plugin))return@runCatching
            val c=config.current(plugin);if(c["dailyEnabled"]!="true")return@runCatching
            val now=Instant.now().atZone(ZoneId.of(c["timeZone"] ?: "UTC"));if(now.toLocalTime()<LocalTime.parse(c["dailyTime"] ?: "00:00"))return@runCatching
            val job=claims.once(plugin,"daily:${now.toLocalDate()}",admin.id) ?: return@runCatching
            val status=runCatching {
                if(plugin=="timecamp")timecamp.sync(admin.email,TimeCampSyncInput(now.toLocalDate().minusDays(7),now.toLocalDate().minusDays(1)))
                else store.list("cloud-costs","kind='CONNECTION'").filter {store.decode(it,CloudConnection::class.java).let {c->c.enabled&&cloud.providerEnabled(c.provider)}}.forEach {cloud.refresh(admin.email,it.id,CloudRefresh(now.toLocalDate().minusDays(7),now.toLocalDate().minusDays(1)))}
                "COMPLETED"
            }.getOrDefault("FAILED_CHECK_CONFIGURATION_AND_RECONCILIATION")
            claims.finish(plugin,job,status)
        }
        if(access.enabled("billing")) {
            if(config.current("billing")["inboxEnabled"]=="true")runCatching {val job=claims.once("billing","inbox:${Instant.now().epochSecond/60}",admin.id) ?: return@runCatching;claims.finish("billing",job,runCatching {inbox.poll(admin.email);"COMPLETED"}.getOrDefault("FAILED_CHECK_IMAPS"))}
            store.list("billing","kind='SCHEDULE' AND CAST(data_json AS jsonb)->>'enabled'='true' AND (CAST(data_json AS jsonb)->>'nextRunAt')::timestamptz<=?",java.sql.Timestamp.from(Instant.now()),limit=100,truncate=true).forEach {row->runCatching {val claim=schedules.claim(row.id) ?: return@runCatching;schedules.finish(claim,runCatching {schedules.run(claim)}.getOrDefault("FAILED_REVIEW_ITEMS_ACCESS_AND_DELIVERY"))}}
        }
        if(access.enabled("on-call"))runCatching {
            store.list("on-call","kind='ALERT' AND CAST(data_json AS jsonb)->>'confirmed'='true' AND CAST(data_json AS jsonb)->>'status'='OPEN' AND (CAST(data_json AS jsonb)->>'nextPageAt')::timestamptz<=?",java.sql.Timestamp.from(Instant.now()),limit=100,truncate=true).forEach {row->runCatching {
                val claim=claims.alert(row.id) ?: return@runCatching;val alert=store.decode(claim,ServiceAlert::class.java)
                val project=access.projects.require(claim.projectId!!,admin)
                val recipients=oncall.currentRecipients(project.id,alert.copy(pageCount=alert.pageCount-1),Instant.now()).filter {id->users.findById(id).orElse(null)?.let {it.active&&it.role!="EXTERNAL"&&access.projects.canRead(it,project)}==true}
                var accepted=0
                recipients.forEach {id->
                    val current=store.decode(store.get("on-call",claim.id),ServiceAlert::class.java)
                    if(current.status!="OPEN"||!access.enabled("on-call"))return@forEach
                    val message="${alert.severity}: ${alert.title}\n${alert.description.take(6000)}\nProject: ${project.name}. Escalation ${alert.pageCount}. Acknowledge in Alerts & on-call to stop repeated paging."
                    val results=publisher.publish("on-call.alert",id,mapOf("subject" to "${alert.severity}: ${alert.title}","message" to message),"on-call:${claim.id}:${alert.pageCount}:$id",splitLongText=true)
                    accepted+=results.values.count {it.status=="ACCEPTED"}
                    if(alert.severity<=AlertSeverity.SEV2)callers.filter {it.available()&&access.enabled(it.pluginId)}.forEach {caller->runCatching {if(caller.call(id,claim.id,alert.severity,message).status=="ACCEPTED")accepted++}}
                }
                claims.pageResult(claim,if(recipients.isEmpty())"NO_ELIGIBLE_ON_CALL_MEMBERS" else if(accepted>0)"ACCEPTED_BY_PROVIDER" else "NO_AVAILABLE_DELIVERY_METHOD")
            }}
        }
        // Only execution markers expire; financial claims, time, alerts and replies remain intact.
        OperationsStore.plugins.forEach {plugin->store.jdbc.update("DELETE FROM ${OperationsStore.table(plugin)} WHERE kind='JOB' AND updated_at<?",java.sql.Timestamp.from(Instant.now().minusSeconds(90L*86400)))}
    }
}
