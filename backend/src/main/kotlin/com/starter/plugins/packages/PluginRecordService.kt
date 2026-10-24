package com.starter.plugins.packages

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.Roles
import com.starter.ai.LocalTextVectors
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.util.UUID

data class PluginRecord(val id: UUID, val data: JsonNode, val revision: Long, val updatedAt: String)
data class PluginRecordInput(val data: JsonNode, val revision: Long = -1)

@Service
class PluginRecordService(private val registry: PluginRegistry, private val packages: InstalledPluginCatalogue,
    private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    private fun table(id: String, table: String, actor: User, write: Boolean = false) {
        if (packages.find(id) == null) throw NotFoundException("Managed plugin data unavailable")
        registry.requireTable(id, table, actor.role, write)
    }
    fun list(id: String, table: String, actor: User, offset: Int): List<PluginRecord> {
        table(id, table, actor)
        if (offset !in 0..100000) throw BadRequestException("Invalid record offset")
        return jdbc.query("SELECT id, data, revision, updated_at FROM \"$table\" WHERE (? OR owner_id = ?) ORDER BY updated_at DESC, id LIMIT 100 OFFSET ?",
            { rs, _ -> row(rs) }, Roles.admin(actor.role), actor.id, offset)
    }
    fun get(id: String, table: String, record: UUID, actor: User): PluginRecord {
        table(id, table, actor)
        return jdbc.query("SELECT id, data, revision, updated_at FROM \"$table\" WHERE id = ? AND (? OR owner_id = ?)",
            { rs, _ -> row(rs) }, record, Roles.admin(actor.role), actor.id).firstOrNull() ?: throw NotFoundException("Plugin record unavailable")
    }
    @Transactional
    fun create(id: String, table: String, input: PluginRecordInput, actor: User): PluginRecord {
        table(id, table, actor, true)
        val json = validate(input.data)
        val text = text(input.data)
        val record = UUID.randomUUID()
        jdbc.update("INSERT INTO \"$table\" (id, owner_id, data, search_text, search_embedding) VALUES (?, ?, CAST(? AS jsonb), ?, CAST(? AS vector))",
            record, actor.id, json, text, vector(text))
        return get(id, table, record, actor)
    }
    @Transactional
    fun update(id: String, table: String, record: UUID, input: PluginRecordInput, actor: User): PluginRecord {
        table(id, table, actor, true)
        get(id, table, record, actor)
        val json = validate(input.data); val text = text(input.data)
        val changed = jdbc.update("UPDATE \"$table\" SET data = CAST(? AS jsonb), revision = revision + 1, updated_at = now(), search_text = ?, search_embedding = CAST(? AS vector) WHERE id = ? AND revision = ? AND (? OR owner_id = ?)",
            json, text, vector(text), record, input.revision, Roles.admin(actor.role), actor.id)
        if (changed != 1) throw ConflictException("This plugin record changed. Reload before saving.")
        return get(id, table, record, actor)
    }
    @Transactional
    fun delete(id: String, table: String, record: UUID, revision: Long, actor: User) {
        table(id, table, actor, true)
        get(id, table, record, actor)
        if (jdbc.update("DELETE FROM \"$table\" WHERE id = ? AND revision = ? AND (? OR owner_id = ?)", record, revision, Roles.admin(actor.role), actor.id) != 1) throw ConflictException("This plugin record changed. Reload before deleting.")
    }
    private fun row(rs: ResultSet) = PluginRecord(rs.getObject("id", UUID::class.java), mapper.readTree(rs.getString("data")), rs.getLong("revision"), rs.getTimestamp("updated_at").toInstant().toString())
    private fun validate(data: JsonNode): String {
        val value = mapper.writeValueAsString(data)
        if (!data.isObject || value.toByteArray().size > 32768) throw BadRequestException("Plugin records must be JSON objects of at most 32 KB")
        return value
    }
    private fun text(data: JsonNode) = data.toString().take(16000)
    private fun vector(text: String) = LocalTextVectors.embed(text).joinToString(",", "[", "]")
}
