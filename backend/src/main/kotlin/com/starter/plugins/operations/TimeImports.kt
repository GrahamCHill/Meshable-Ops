package com.starter.plugins.operations

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.integrations.IntegrationHttpClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class ActivityWatchService(private val store:OperationsStore,private val access:OperationsAccess,private val time:TimesheetService) {
    fun list(email:String)=access.actor(email,"activitywatch").let {actor->store.list("activitywatch","owner_id=? AND kind='ACTIVITY'",actor.id,limit=2500).map {mapOf("id" to it.id,"activity" to store.decode(it,ActivitySlice::class.java),"revision" to it.revision)}}
    @Transactional fun import(email:String,export:JsonNode):ImportResult {
        val actor=access.actor(email,"activitywatch",true)
        val slices=parse(export)
        store.lock("activity-import:${actor.id}")
        var imported=0;var skipped=0
        slices.forEach {slice->val id=store.stable("activity:${actor.id}:${slice.bucket}:${slice.eventId}")
            if(store.list("activitywatch","id=?",id).isNotEmpty())skipped++ else {store.save("activitywatch",actor.id,null,"ACTIVITY",slice,id);imported++}}
        return ImportResult(imported,skipped)
    }
    @Transactional fun allocate(email:String,id:UUID,input:ActivityAllocation):TimeEntryView {
        val actor=access.actor(email,"activitywatch",true);val row=store.get("activitywatch",id)
        if(row.ownerId!=actor.id || row.kind!="ACTIVITY")throw NotFoundException("Activity unavailable")
        val slice=store.decode(row,ActivitySlice::class.java)
        if(slice.allocated || row.revision!=input.revision)throw ConflictException("This activity was already allocated or changed")
        access.project(email,"activitywatch",input.projectId,false,true)
        val key="${slice.bucket}:${slice.eventId}"
        time.imported(email,actor.id,input.projectId,slice.start,slice.end,Duration.between(slice.start,slice.end).seconds,input.description,TimeSource.ACTIVITYWATCH,key,input.billable)
        store.save("activitywatch",actor.id,null,"ACTIVITY",slice.copy(allocated=true),row.id,input.revision)
        return time.view(store.get("timesheets",store.stable("time:${TimeSource.ACTIVITYWATCH}:${actor.id}:$key")))
    }
    @Transactional fun remove(email:String,id:UUID,revision:Long) {
        val actor=access.actor(email,"activitywatch",true);val row=store.get("activitywatch",id)
        if(row.ownerId!=actor.id||row.kind!="ACTIVITY")throw NotFoundException("Activity unavailable")
        if(row.revision!=revision)throw ConflictException("Activity changed")
        store.remove("activitywatch",row) // Only the private suggestion is removed; allocated work remains in the ledger.
    }
    companion object {
        fun parse(export:JsonNode):List<ActivitySlice> {
            if(export.toString().length>2_000_000)throw BadRequestException("Import a filtered ActivityWatch export up to 2 MB")
            val buckets=export.path("buckets").takeIf {it.isObject} ?: if(export.has("events")) com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().createObjectNode().set<JsonNode>(export.path("id").asText("bucket"),export) else throw BadRequestException("Use an ActivityWatch bucket or complete JSON export")
            val result=mutableListOf<ActivitySlice>()
            buckets.fields().forEach { (name,bucket)->
                if(name.contains("afk",true))return@forEach
                if(!bucket.path("events").isArray)throw BadRequestException("Each exported bucket must contain events")
                bucket.path("events").forEach {event->
                    val start=runCatching {OffsetDateTime.parse(event.path("timestamp").asText()).toInstant()}.getOrElse {throw BadRequestException("Activity timestamps need a timezone")}
                    val seconds=event.path("duration").asDouble(-1.0)
                    if(!seconds.isFinite()||seconds<0||seconds>86400)throw BadRequestException("Activity duration is outside the supported range")
                    if(seconds<1)return@forEach
                    val end=start.plusSeconds(seconds.toLong())
                    if(end>Instant.now().plusSeconds(60))throw BadRequestException("Future activity cannot be imported")
                    val app=event.path("data").path("app").asText("Activity").take(160)
                    // Window titles, URLs, filenames and keystrokes are deliberately never retained.
                    val key=event.path("id").asText().ifBlank {start.toString()}
                    if(name.length>200||key.length>200)throw BadRequestException("Invalid activity identifiers")
                    result+=ActivitySlice(start,end,app,name,key)
                    if(result.size>2000)throw BadRequestException("Select up to 2000 activity events per import")
                }
            }
            if(result.isEmpty())throw BadRequestException("No completed non-AFK activity was found")
            return result
        }
    }
}

@Service
class TimeCampService(private val store:OperationsStore,private val access:OperationsAccess,private val config:PluginConfigurationService,private val http:IntegrationHttpClient,private val time:TimesheetService) {
    fun mappings(email:String)=access.admin(email,"timecamp").let {store.list("timecamp","kind='MAPPING'").map {row->mapOf("id" to row.id,"mapping" to store.decode(row,TimeCampMapping::class.java),"revision" to row.revision)}}
    @Transactional fun map(email:String,input:TimeCampMapping):OpsRow {
        val actor=access.admin(email,"timecamp",true);access.project(email,"timecamp",input.projectId,true,true)
        if(input.taskId<1||input.providerUserId<1)throw BadRequestException("Choose numeric TimeCamp task and user IDs")
        val id=store.stable("timecamp-map:${input.providerUserId}:${input.taskId}")
        store.lock("timecamp-map:$id")
        if(store.list("timecamp","id=?",id).isNotEmpty())throw ConflictException("This provider user/task mapping already exists; remove it before replacing it")
        return store.save("timecamp",actor.id,input.projectId,"MAPPING",input,id)
    }
    @Transactional fun remove(email:String,id:UUID,revision:Long) {
        access.admin(email,"timecamp",true);val row=store.get("timecamp",id)
        if(row.kind!="MAPPING"||row.revision!=revision)throw ConflictException("Mapping changed or is unavailable")
        store.remove("timecamp",row)
    }
    fun sync(email:String,input:TimeCampSyncInput):ImportResult {
        access.admin(email,"timecamp",true);access.registry.requireTable("timesheets",OperationsStore.table("timesheets"),"ADMIN",true)
        OperationsValidation.dates(input.from,input.to,31)
        val settings=config.current("timecamp");val token=settings["apiToken"]?.takeIf {it.isNotBlank()} ?: throw BadRequestException("Configure the TimeCamp API token")
        val zone=runCatching {ZoneId.of(settings["timeZone"] ?: "UTC")}.getOrElse {throw BadRequestException("Choose a valid TimeCamp timezone")}
        val mapping=store.list("timecamp","kind='MAPPING'").map {store.decode(it,TimeCampMapping::class.java)}
        if(mapping.isEmpty())throw BadRequestException("Map TimeCamp users and tasks to internal members and projects first")
        var added=0;var skipped=0;val warnings=mutableListOf<String>()
        // One bounded day at a time, using the current v3 endpoint rather than the retired v1 API.
        var date=input.from
        while(date<=input.to) {
            val body=store.mapper.writeValueAsString(mapOf("startDate" to date,"endDate" to date,"userIds" to mapping.map {it.providerUserId}.distinct(),"taskIds" to mapping.map {it.taskId}.distinct()))
            val response=http.scoped("POST","https://app.timecamp.com/third_party/api","v3/time-entries/list",mapOf("Authorization" to "Bearer $token","Accept" to "application/json"),body).body
                ?: throw BadRequestException("TimeCamp returned no data")
            if(!response.isArray||response.size()>2000)throw BadRequestException("TimeCamp response was incomplete or too large; narrow the import")
            response.forEach {entry->
                val target=mapping.singleOrNull {it.providerUserId==entry.path("user").path("id").asLong() && it.taskId==entry.path("task").path("id").asLong()}
                if(target==null){skipped++;return@forEach}
                val key=entry.path("id").asText();if(!key.matches(Regex("[0-9]{1,30}")))throw BadRequestException("TimeCamp returned an invalid entry ID")
                val day=LocalDate.parse(entry.path("date").asText())
                if(day!=date)throw BadRequestException("TimeCamp returned work outside the requested day")
                val start=day.atTime(LocalTime.parse(entry.path("start_time").asText())).atZone(zone).toInstant()
                var end=day.atTime(LocalTime.parse(entry.path("end_time").asText())).atZone(zone).toInstant();if(end<=start)end=day.plusDays(1).atTime(LocalTime.parse(entry.path("end_time").asText())).atZone(zone).toInstant()
                if(config.current("timecamp")!=settings)throw ConflictException("TimeCamp settings changed during import")
                try {if(time.imported(email,target.userId,target.projectId,start,end,entry.path("duration").asLong(),entry.path("description").asText("").ifBlank {"TimeCamp work"}.take(2000),TimeSource.TIMECAMP,key,entry.path("billable").asBoolean(true)))added++ else skipped++}
                catch(_:ConflictException){skipped++;warnings+="Entry $key needs reconciliation; existing or overlapping time was preserved."}
            }
            date=date.plusDays(1)
        }
        return ImportResult(added,skipped,warnings.take(50))
    }
}
