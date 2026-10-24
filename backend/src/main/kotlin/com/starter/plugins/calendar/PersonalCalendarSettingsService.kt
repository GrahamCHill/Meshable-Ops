package com.starter.plugins.calendar

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.*
import com.starter.security.SecretEncryption
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PersonalCalendarSettingsService(private val connections: PersonalCalendarRepository,private val links: CalendarSyncLinkRepository,
    private val events: CalendarEventRepository,private val actors: PluginActor,private val registry: PluginRegistry,private val mapper: ObjectMapper,
    private val encryption: SecretEncryption,private val jdbc: JdbcTemplate) {
    fun settings(email: String): PersonalCalendarSettings {
        val actor = actor(email)
        val active = registry.available(actor.role).filter { "calendar.personal-sync" in it.manifest.capabilities }.map { it.manifest.id }
        val calendar = registry.available(actor.role).first { it.manifest.id == "calendar" }
        val canWrite = calendar.manifest.tables.all { actor.role in calendar.tableAccess.getValue(it.name).writeRoles }
        return PersonalCalendarSettings(connections.findByOwnerId(actor.id)?.let(::view),PersonalCalendarSchemas.all.filter { it.id in active },canWrite)
    }
    @Transactional
    fun save(email: String,request: PersonalCalendarRequest): PersonalCalendarView {
        val actor = actor(email,true)
        val fields = PersonalCalendarSchemas.schema(request.providerId).fields.associateBy { it.key }
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",org.springframework.jdbc.core.RowCallbackHandler {},"personal-calendar:${actor.id}")
        val existing = connections.findByOwnerId(actor.id)?.let { connections.locked(it.id).orElseThrow() }
        if ((existing?.configurationRevision ?: -1) != request.revision) throw ConflictException("Your connection changed. Reload before saving.")
        if (existing != null && existing.providerId != request.providerId) throw BadRequestException("Disconnect your previous calendar before choosing another provider")
        if (request.values.keys.any { it !in fields } || request.clearSecrets.any { fields[it]?.type != PluginFieldType.SECRET }) throw BadRequestException("Use only this provider's personal connection fields")
        encryption.requireConfigured()
        val values = values(existing).toMutableMap()
        request.clearSecrets.forEach(values::remove)
        request.values.forEach { (key,raw) ->
            val field = fields.getValue(key)
            if (raw.length > field.maxLength || raw.any { it.isISOControl() }) throw BadRequestException("Invalid ${field.label}")
            if (field.type == PluginFieldType.SECRET && raw.isBlank()) return@forEach
            if (key in request.clearSecrets && raw.isNotBlank()) throw BadRequestException("Clear or replace a credential, not both")
            if (raw.isBlank()) values.remove(key) else values[key] = raw.trim()
        }
        PersonalCalendarSchemas.validate(request.providerId,values)
        if (request.enabled) {
            registry.requireCapability("calendar","calendar.personal-sync",request.providerId,actor.role)
            if (PersonalCalendarSchemas.missing(request.providerId,values).isNotEmpty()) throw BadRequestException("Complete your connection before enabling two-way sync")
        }
        val row = existing ?: PersonalCalendarConnection(ownerId=actor.id,providerId=request.providerId)
        row.credentialsJson=mapper.writeValueAsString(values);row.enabled=request.enabled;row.nextSyncAt=Instant.now()
        if (existing!=null) row.configurationRevision++
        if (!row.enabled) row.status="Sync paused"
        return view(connections.saveAndFlush(row))
    }
    @Transactional
    fun disconnect(email: String,revision: Long) {
        val actor = actor(email,true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",org.springframework.jdbc.core.RowCallbackHandler {},"personal-calendar:${actor.id}")
        val row = connections.findByOwnerId(actor.id)?.let { connections.locked(it.id).orElseThrow() } ?: return
        if (row.configurationRevision != revision) throw ConflictException("Your connection changed. Reload before disconnecting.")
        events.findAllByOriginConnectionId(row.id).forEach { it.originConnectionId=null;it.syncExcluded=true }
        links.deleteByConnection_Id(row.id)
        connections.delete(row) // Provider data is deliberately retained.
    }
    fun owned(email: String) = connections.findByOwnerId(actor(email,true).id) ?: throw NotFoundException("No personal calendar connection")
    internal fun values(row: PersonalCalendarConnection?): Map<String,String> = row?.let { mapper.readValue(it.credentialsJson,object : TypeReference<Map<String,String>>() {}) } ?: emptyMap()
    internal fun actor(email: String,write: Boolean = false) = actors.current(email).also { actor ->
        listOf("plugin_calendar_events","plugin_calendar_connections","plugin_calendar_sync_links").forEach { registry.requireTable("calendar",it,actor.role,write) }
    }
    internal fun view(row: PersonalCalendarConnection): PersonalCalendarView {
        val values = values(row);val fields = PersonalCalendarSchemas.schema(row.providerId).fields
        return PersonalCalendarView(row.providerId,row.enabled,row.configurationRevision,values.filterKeys { key -> fields.any { it.key==key && it.type != PluginFieldType.SECRET } },
            fields.filter { it.type==PluginFieldType.SECRET && !values[it.key].isNullOrBlank() }.map { it.key }.toSet(),PersonalCalendarSchemas.missing(row.providerId,values),row.lastSyncAt,row.status)
    }
}
