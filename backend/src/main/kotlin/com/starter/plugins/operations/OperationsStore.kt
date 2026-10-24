package com.starter.plugins.operations

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.*
import com.starter.projects.*
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Each optional module owns its table, including credentials and schedules. */
@MappedSuperclass
abstract class OperationsRecord {
    @Id var id: UUID = UUID.randomUUID()
    var ownerId: UUID = UUID.randomUUID()
    var projectId: UUID? = null
    @Column(length=40) var kind: String = ""
    @Column(length=240) var externalKey: String? = null
    @Column(columnDefinition="TEXT") var dataJson: String = "{}"
    @get:JsonIgnore @Convert(converter=EncryptedSecretConverter::class) @Column(columnDefinition="TEXT") var secretJson: String? = null
    var updatedAt: Instant = Instant.now()
    @Version var revision: Long = 0
}
@Entity @Table(name="plugin_timesheets_records", indexes=[Index(columnList="owner_id,kind"),Index(columnList="project_id,kind")]) class TimesheetRecord: OperationsRecord()
@Entity @Table(name="plugin_activitywatch_records", indexes=[Index(columnList="owner_id,kind")]) class ActivityRecord: OperationsRecord()
@Entity @Table(name="plugin_timecamp_records") class TimeCampRecord: OperationsRecord()
@Entity @Table(name="plugin_cloud_costs_records", indexes=[Index(columnList="project_id,kind")]) class CloudCostRecord: OperationsRecord()
@Entity @Table(name="plugin_billing_items_records", indexes=[Index(columnList="project_id,kind")]) class BillingItemRecord: OperationsRecord()
@Entity @Table(name="plugin_billing_records", indexes=[Index(columnList="project_id,kind")]) class BillingRecord: OperationsRecord()
@Entity @Table(name="plugin_on_call_records", indexes=[Index(columnList="project_id,kind")]) class OnCallRecord: OperationsRecord()

data class OpsRow(val id: UUID, val ownerId: UUID, val projectId: UUID?, val kind: String, val dataJson: String, val revision: Long, val updatedAt: Instant)

@Service
class OperationsStore(val jdbc: JdbcTemplate, val mapper: ObjectMapper) {
    companion object {
        val plugins = setOf("timesheets","activitywatch","timecamp","cloud-costs","billing-items","billing","on-call")
        fun table(plugin: String): String { require(plugin in plugins); return "plugin_${plugin.replace('-','_')}_records" }
    }
    fun lock(key: String) { jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",org.springframework.jdbc.core.RowCallbackHandler {},key) }
    fun list(plugin: String, where: String="TRUE", vararg args: Any, limit: Int=1000, truncate:Boolean=false): List<OpsRow> {
        // Predicates and identifiers are reviewed source constants. All request values are parameters.
        val rows=jdbc.query("SELECT id,owner_id,project_id,kind,data_json,revision,updated_at FROM ${table(plugin)} WHERE $where ORDER BY updated_at DESC,id LIMIT ?",
            {rs,_->OpsRow(rs.getObject("id",UUID::class.java),rs.getObject("owner_id",UUID::class.java),rs.getObject("project_id",UUID::class.java),rs.getString("kind"),rs.getString("data_json"),rs.getLong("revision"),rs.getTimestamp("updated_at").toInstant())},*args,limit+1)
        if(rows.size>limit&&!truncate) throw BadRequestException("Too many records. Narrow the date range or project before continuing.")
        return rows.take(limit)
    }
    fun get(plugin: String,id: UUID)=list(plugin,"id = ?",id).firstOrNull() ?: throw NotFoundException("Record unavailable")
    fun <T> decode(row: OpsRow,type: Class<T>): T=mapper.readValue(row.dataJson,type)
    fun save(plugin: String,owner: UUID,project: UUID?,kind: String,value: Any,id: UUID=UUID.randomUUID(),revision: Long = -1): OpsRow {
        val json=mapper.writeValueAsString(value)
        if(json.length>500_000) throw BadRequestException("Record exceeds the supported size")
        if(revision<0) {
            if(jdbc.update("INSERT INTO ${table(plugin)} (id,owner_id,project_id,kind,data_json,revision,updated_at) VALUES (?,?,?,?,?,0,?) ON CONFLICT(id) DO NOTHING",id,owner,project,kind,json,java.sql.Timestamp.from(Instant.now()))!=1)
                throw ConflictException("This operation was already recorded. Reload before retrying.")
        } else if(jdbc.update("UPDATE ${table(plugin)} SET project_id=?,data_json=?,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND kind=? AND revision=?",project,json,java.sql.Timestamp.from(Instant.now()),id,owner,kind,revision)!=1)
            throw ConflictException("This record changed. Reload before saving.")
        return get(plugin,id)
    }
    fun remove(plugin: String,row: OpsRow) { if(jdbc.update("DELETE FROM ${table(plugin)} WHERE id=? AND revision=?",row.id,row.revision)!=1) throw ConflictException("This record changed. Reload before removing.") }
    fun stable(key: String)=UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))
    fun secret(plugin: String,id: UUID):String?=jdbc.queryForObject("SELECT secret_json FROM ${table(plugin)} WHERE id=?",String::class.java,id)
    fun setSecret(plugin: String,id: UUID,encrypted: String?) {jdbc.update("UPDATE ${table(plugin)} SET secret_json=? WHERE id=?",encrypted,id)}
}

@Service
class OperationsAccess(val actors: PluginActor,val registry: PluginRegistry,val projects: ProjectAccess) {
    fun actor(email: String,plugin: String,write: Boolean=false): User {
        val actor=actors.current(email)
        if(actor.role=="EXTERNAL") throw ForbiddenException("This plugin is for internal members")
        registry.requireTable(plugin,OperationsStore.table(plugin),actor.role,write)
        return actor
    }
    fun admin(email: String,plugin: String,write: Boolean=false)=actor(email,plugin,write).also {if(!Roles.admin(it.role)) throw ForbiddenException("An administrator must configure this plugin")}
    fun project(email: String,plugin: String,id: UUID,manage: Boolean=false,write: Boolean=manage): Pair<User,ProjectRecord> {
        val actor=actor(email,plugin,write);projects.requireFeature(actor)
        return actor to projects.require(id,actor,manage)
    }
    fun enabled(plugin: String)=runCatching {registry.requireAvailable(plugin,"ADMIN")}.isSuccess
}

object OperationsValidation {
    fun text(value: String,label: String,max: Int=200,required: Boolean=true): String {
        if(value.length>max || value.any {it.isISOControl() && it!='\n'} || required && value.isBlank()) throw BadRequestException("Enter a valid $label (up to $max characters)")
        return value.trim()
    }
    fun dates(from: LocalDate,to: LocalDate,max: Long=366) {if(to<from || java.time.temporal.ChronoUnit.DAYS.between(from,to)>max) throw BadRequestException("Choose a date range of up to $max days")}
    fun currency(value: String)=value.uppercase().also {if(runCatching {java.util.Currency.getInstance(it)}.isFailure || it.length!=3) throw BadRequestException("Choose a valid ISO currency")}
}
