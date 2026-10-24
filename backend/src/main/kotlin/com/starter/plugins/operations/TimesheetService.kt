package com.starter.plugins.operations

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class TimesheetService(private val store:OperationsStore,private val access:OperationsAccess,private val config:PluginConfigurationService,private val rates:BillingRates,private val users:UserRepository,private val visibility:TimeVisibility) {
    fun list(email:String,from:LocalDate,to:LocalDate,project:UUID?=null,team:Boolean=false):List<TimeEntryView> {
        val actor=access.actor(email,"timesheets");OperationsValidation.dates(from,to,93)
        if(team && project==null)throw BadRequestException("Choose a project for team approval")
        if(project!=null){val (_,p)=access.project(email,"timesheets",project,false,false);if(team)visibility.requireSenior(actor,p)}
        val predicate="kind='ENTRY' AND (CAST(data_json AS jsonb)->>'start')::timestamptz>=? AND (CAST(data_json AS jsonb)->>'start')::timestamptz<?"
        val args=mutableListOf<Any>(java.sql.Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()),java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
        val where=predicate+(if(!team){args+=actor.id;" AND owner_id=?"}else "")+(if(project!=null){args+=project;" AND project_id=?"}else "")
        return store.list("timesheets",where,*args.toTypedArray(),limit=5000).map(::view)
    }
    private fun internal(email:String)=access.actor(email,"timesheets",true).also {if(config.current("timesheets")["internalEnabled"]!="true")throw ForbiddenException("An administrator must enable internal time tracking")}
    @Transactional fun save(email:String,id:UUID?,input:TimeEntryInput):TimeEntryView {
        val actor=internal(email);access.project(email,"timesheets",input.projectId,false,true)
        validate(input.start,input.end,input.description)
        store.lock("time-ledger:${actor.id}")
        val row=id?.let {store.get("timesheets",it)}
        val old=row?.let {store.decode(it,TimeEntry::class.java)}
        if(row!=null && (row.ownerId!=actor.id || row.kind!="ENTRY"))throw NotFoundException("Time entry unavailable")
        if(old!=null && (old.source!=TimeSource.INTERNAL || old.status !in setOf(TimeStatus.DRAFT,TimeStatus.REJECTED) || old.billedInvoice!=null))throw ConflictException("Imported, submitted, approved or billed work cannot be edited here")
        noOverlap(actor.id,input.start,input.end,id)
        val e=TimeEntry(actor.id,input.projectId,input.start,input.end,Duration.between(input.start,input.end).seconds,input.description.trim(),billable=input.billable)
        return view(store.save("timesheets",actor.id,input.projectId,"ENTRY",e,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision))
    }
    @Transactional fun action(email:String,id:UUID,input:TimeAction):TimeEntryView {
        val actor=access.actor(email,"timesheets",true);val row=store.get("timesheets",id);val e=store.decode(row,TimeEntry::class.java)
        if(row.kind!="ENTRY")throw NotFoundException("Time entry unavailable")
        val (_,project)=access.project(email,"timesheets",e.projectId,false,true)
        if(input.action in setOf("APPROVE","REJECT"))visibility.requireSenior(actor,project)
        store.lock("time-ledger:${e.userId}")
        if(e.billedInvoice!=null)throw ConflictException("This entry is reserved on an invoice")
        val next=when(input.action) {
            "SUBMIT"->{if(e.userId!=actor.id || e.status !in setOf(TimeStatus.DRAFT,TimeStatus.REJECTED))throw ForbiddenException("Only your draft work can be submitted");noOverlap(e.userId,e.start,e.end,id);e.copy(status=TimeStatus.SUBMITTED)}
            "APPROVE"->{if(e.status!=TimeStatus.SUBMITTED)throw ConflictException("Submit this work before approving it");if(e.userId==actor.id&&!Roles.admin(actor.role))throw ForbiddenException("A different project leader must approve your billable work");noOverlap(e.userId,e.start,e.end,id);val r=rates.resolve(e.userId,e.start.atZone(ZoneOffset.UTC).toLocalDate());e.copy(status=TimeStatus.APPROVED,approvedBy=actor.id,roleId=r?.roleId,rate=r?.amount,currency=r?.currency ?: "USD")}
            "REJECT"->{if(e.status==TimeStatus.DRAFT)throw ConflictException("Draft work has not been submitted");e.copy(status=TimeStatus.REJECTED,approvedBy=null,rate=null,roleId=null)}
            else->throw BadRequestException("Choose SUBMIT, APPROVE or REJECT")
        }
        return view(store.save("timesheets",row.ownerId,row.projectId,"ENTRY",next,id,input.revision))
    }
    @Transactional fun remove(email:String,id:UUID,revision:Long) {
        val actor=access.actor(email,"timesheets",true);val row=store.get("timesheets",id);val e=store.decode(row,TimeEntry::class.java)
        if(row.ownerId!=actor.id||row.kind!="ENTRY")throw NotFoundException("Entry unavailable")
        if(row.revision!=revision||e.status !in setOf(TimeStatus.DRAFT,TimeStatus.REJECTED)||e.billedInvoice!=null||e.source!=TimeSource.INTERNAL)throw ConflictException("Only unchanged internal draft entries can be removed")
        store.remove("timesheets",row)
    }
    @Transactional fun reconcile(email:String,id:UUID,input:TimeEntryInput):TimeEntryView {
        val actor=access.actor(email,"timesheets",true);val row=store.get("timesheets",id)
        if(row.kind!="ENTRY")throw NotFoundException("Entry unavailable")
        val old=store.decode(row,TimeEntry::class.java)
        if(old.source==TimeSource.INTERNAL)return save(email,id,input)
        if(!(Roles.admin(actor.role)||old.source==TimeSource.ACTIVITYWATCH&&row.ownerId==actor.id))throw ForbiddenException("Only an administrator can reconcile TimeCamp imports; members can reconcile their own allocated ActivityWatch work")
        access.project(email,"timesheets",old.projectId,false,true);access.project(email,"timesheets",input.projectId,false,true)
        if(old.billedInvoice!=null)throw ConflictException("Reserved invoice time cannot be changed")
        validate(input.start,input.end,input.description);store.lock("time-ledger:${old.userId}")
        noOverlap(old.userId,input.start,input.end,row.id)
        val value=old.copy(projectId=input.projectId,start=input.start,end=input.end,seconds=Duration.between(input.start,input.end).seconds,description=input.description,billable=input.billable,status=TimeStatus.DRAFT,approvedBy=null,roleId=null,rate=null)
        return view(store.save("timesheets",row.ownerId,input.projectId,"ENTRY",value,row.id,input.revision))
    }
    fun timer(email:String):Map<String,Any?> {val actor=access.actor(email,"timesheets");return mapOf("timer" to store.list("timesheets","owner_id=? AND kind='TIMER'",actor.id).firstOrNull()?.let {store.decode(it,RunningTimer::class.java)},"internalEnabled" to (config.current("timesheets")["internalEnabled"]=="true"))}
    @Transactional fun start(email:String,input:TimerInput):RunningTimer {
        val actor=internal(email);access.project(email,"timesheets",input.projectId,false,true);OperationsValidation.text(input.description,"work description",2000)
        store.lock("time-ledger:${actor.id}");val id=store.stable("timer:${actor.id}")
        val timer=RunningTimer(input.projectId,Instant.now(),input.description,input.billable)
        store.save("timesheets",actor.id,input.projectId,"TIMER",timer,id);return timer
    }
    @Transactional fun stop(email:String):TimeEntryView {
        val actor=internal(email);store.lock("time-ledger:${actor.id}");val row=store.get("timesheets",store.stable("timer:${actor.id}"));val timer=store.decode(row,RunningTimer::class.java)
        val saved=save(email,null,TimeEntryInput(timer.projectId,timer.start,Instant.now(),timer.description,timer.billable));store.remove("timesheets",row);return saved
    }
    /** Called by reviewed importers after their own plugin/mapping authorization. Replays never create a second charge. */
    @Transactional fun imported(email:String,userId:UUID,project:UUID,start:Instant,end:Instant,seconds:Long,description:String,source:TimeSource,key:String,billable:Boolean=true):Boolean {
        val actor=access.actor(email,if(source==TimeSource.ACTIVITYWATCH)"activitywatch" else "timecamp",true)
        if(source==TimeSource.ACTIVITYWATCH && actor.id!=userId || source==TimeSource.TIMECAMP && !Roles.admin(actor.role))throw ForbiddenException("Invalid import owner")
        val target=users.findById(userId).orElse(null) ?: throw BadRequestException("Internal member unavailable")
        access.projects.requireFeature(target);access.projects.require(project,target)
        access.registry.requireTable("timesheets",OperationsStore.table("timesheets"),actor.role,true)
        validate(start,end,description)
        if(seconds !in 1..Duration.between(start,end).seconds)throw BadRequestException("Duration must fit inside the imported time interval")
        store.lock("time-ledger:$userId");val id=store.stable("time:$source:$userId:$key")
        val existing=store.list("timesheets","id=?",id).firstOrNull()
        if(existing!=null) {
            val old=store.decode(existing,TimeEntry::class.java)
            if(old.start!=start||old.end!=end||old.seconds!=seconds||old.projectId!=project||old.description!=description.take(2000))throw ConflictException("An imported entry changed. Reconcile the original entry before importing it again.")
            return false
        }
        noOverlap(userId,start,end,null)
        store.save("timesheets",userId,project,"ENTRY",TimeEntry(userId,project,start,end,seconds,description.take(2000),source,key,billable),id)
        return true
    }
    fun noOverlap(user:UUID,start:Instant,end:Instant,except:UUID?) {
        val count=store.jdbc.queryForObject("SELECT count(*) FROM plugin_timesheets_records WHERE owner_id=? AND kind='ENTRY' AND id<>? AND CAST(data_json AS jsonb)->>'status'<>'REJECTED' AND (CAST(data_json AS jsonb)->>'start')::timestamptz<? AND (CAST(data_json AS jsonb)->>'end')::timestamptz>?",Long::class.java,user,except ?: UUID(0,0),java.sql.Timestamp.from(end),java.sql.Timestamp.from(start)) ?: 0
        if(count>0)throw ConflictException("This time overlaps an existing entry. Reconcile the timesheet before importing or submitting it.")
    }
    fun view(row:OpsRow)=TimeEntryView(row.id,store.decode(row,TimeEntry::class.java),row.revision)
    companion object {fun validate(start:Instant,end:Instant,description:String) {OperationsValidation.text(description,"work description",2000);if(!end.isAfter(start)||Duration.between(start,end).seconds !in 1..86400||end>Instant.now().plusSeconds(60))throw BadRequestException("Work must be a completed interval of up to 24 hours")}}
}
