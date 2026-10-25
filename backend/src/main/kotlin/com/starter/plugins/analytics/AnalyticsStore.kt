package com.starter.plugins.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.PluginRegistry
import com.starter.projects.ProjectAccess
import com.starter.projects.ProjectService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AnalyticsRow(val id: UUID,val label: String,val payload: AnalyticsPayload,val revision: Long)

@Service
class AnalyticsStore(private val jdbc: JdbcTemplate,private val mapper: ObjectMapper,private val projects: ProjectAccess,
    private val clients: ProjectService,private val plugins: PluginRegistry) {
    fun authorize(email: String,provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID,write: Boolean=false): com.starter.model.postgres.User {
        val actor=projects.actor(email)
        if(kind==AnalyticsContext.PROJECT)projects.require(contextId,actor,write) else clients.requireClient(email,contextId,write)
        plugins.requireTable(provider.id,provider.table,actor.role,write)
        return actor
    }
    fun list(email: String,provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID): List<AnalyticsRow> {
        authorize(email,provider,kind,contextId)
        return rows(provider,kind,contextId)
    }
    fun parentClient(email: String,kind: AnalyticsContext,contextId: UUID): UUID? =
        if(kind==AnalyticsContext.CLIENT)contextId else projects.require(contextId,projects.actor(email)).clientId
    private fun rows(provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID)=jdbc.query(
        "SELECT id,label,payload_json,revision FROM ${provider.table} WHERE context_kind=? AND context_id=? ORDER BY label,id LIMIT 101",
        {rs,_->AnalyticsRow(rs.getObject("id",UUID::class.java),rs.getString("label"),mapper.readValue(rs.getString("payload_json"),AnalyticsPayload::class.java),rs.getLong("revision"))},kind.name,contextId)
    fun get(email: String,provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID,id: UUID)=list(email,provider,kind,contextId).find {it.id==id} ?: throw NotFoundException("Analytics link unavailable")
    @Transactional
    fun save(email: String,provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID,id: UUID?,label: String,payload: AnalyticsPayload,revision: Long): AnalyticsRow {
        authorize(email,provider,kind,contextId,true)
        if(label.isBlank() || label.length>120 || label.any(Char::isISOControl))throw BadRequestException("Use a link name of 1–120 characters")
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",org.springframework.jdbc.core.RowCallbackHandler {},"analytics:${provider.id}:$kind:$contextId")
        val all=rows(provider,kind,contextId);val old=id?.let {key->all.find {it.id==key} ?: throw NotFoundException("Analytics link unavailable")}
        if((old?.revision ?: -1)!=revision)throw ConflictException("This analytics link changed. Reload before saving.")
        if(old==null && all.size>=100)throw BadRequestException("A client or project supports up to 100 links per analytics connector")
        if(all.any {it.id!=id && it.payload.selection==payload.selection})throw ConflictException("These metrics are already linked here")
        val json=mapper.writeValueAsString(payload)
        if(json.length>50000)throw BadRequestException("Analytics snapshot exceeded its size limit")
        val key=id ?: UUID.randomUUID();val now=java.sql.Timestamp.from(Instant.now())
        if(old==null)jdbc.update("INSERT INTO ${provider.table} (id,context_kind,context_id,label,payload_json,updated_at,revision) VALUES (?,?,?,?,?,?,0)",key,kind.name,contextId,label.trim(),json,now)
        else if(jdbc.update("UPDATE ${provider.table} SET label=?,payload_json=?,updated_at=?,revision=revision+1 WHERE id=? AND context_kind=? AND context_id=? AND revision=?",label.trim(),json,now,key,kind.name,contextId,revision)!=1)
            throw ConflictException("This analytics link changed. Reload before saving.")
        return AnalyticsRow(key,label.trim(),payload,old?.revision?.plus(1) ?: 0)
    }
    @Transactional
    fun remove(email: String,provider: AnalyticsProvider,kind: AnalyticsContext,contextId: UUID,id: UUID,revision: Long) {
        authorize(email,provider,kind,contextId,true)
        if(jdbc.update("DELETE FROM ${provider.table} WHERE id=? AND context_kind=? AND context_id=? AND revision=?",id,kind.name,contextId,revision)!=1)
            throw ConflictException("This analytics link changed. Reload before removing it.")
    }
}
