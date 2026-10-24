package com.starter.plugins

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.security.SecretEncryption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PluginConfigurationService(private val registry: PluginRegistry, private val records: PluginConfigurationRepository,
    private val mapper: ObjectMapper, private val encryption: SecretEncryption, private val jdbc: org.springframework.jdbc.core.JdbcTemplate? = null) {
    @Transactional(readOnly = true)
    fun view(id: String): PluginConfigurationView {
        val manifest = registry.definition(id)
        val record = records.findById(id).orElse(null)
        return response(manifest, decode(record), record?.revision ?: -1)
    }
    @Transactional
    fun save(id: String, request: PluginConfigurationRequest): PluginConfigurationView {
        val manifest = registry.definition(id)
        val fields = manifest.settingsFields.associateBy { it.key }
        if (request.values.keys.any { it !in fields } || request.clearSecrets.any { fields[it]?.type != PluginFieldType.SECRET })
            throw BadRequestException("Use only this plugin's declared settings fields")
        encryption.requireConfigured()
        // Serialize initial creation too: an absent row cannot be locked with
        // SELECT FOR UPDATE. This lock is shared across application instances.
        jdbc?.query("SELECT pg_advisory_xact_lock(hashtext(?))", org.springframework.jdbc.core.RowCallbackHandler { }, "plugin-configuration:$id")
        val existing = records.findById(id).orElse(null)
        if ((existing?.revision ?: -1) != request.revision) throw ConflictException("Plugin configuration changed. Reload before saving.")
        val record = existing ?: PluginConfigurationRecord(id)
        val values = decode(record).filterKeys { it in fields }.toMutableMap()
        request.clearSecrets.forEach(values::remove)
        request.values.forEach { (key, raw) ->
            val field = fields.getValue(key)
            if (raw.length > field.maxLength || raw.any { it.isISOControl() }) throw BadRequestException("Invalid ${field.label.lowercase()}")
            val value = raw.trim()
            if (field.type == PluginFieldType.SECRET && value.isBlank()) return@forEach // blank preserves an existing secret
            if (key in request.clearSecrets && value.isNotBlank()) throw BadRequestException("Clear or replace a credential, not both")
            if (value.isEmpty()) values.remove(key) else values[key] = value
        }
        registry.validateConfiguration(id, values)
        record.dataJson = mapper.writeValueAsString(values)
        val saved = records.saveAndFlush(record)
        return response(manifest, values, saved.revision)
    }
    /** Server-side only. Never expose this map in a controller response or log. */
    @Transactional(readOnly = true)
    fun current(id: String): Map<String, String> {
        registry.definition(id)
        return decode(records.findById(id).orElse(null))
    }
    private fun decode(row: PluginConfigurationRecord?): Map<String, String> = row?.dataJson?.let {
        mapper.readValue(it, object : TypeReference<Map<String, String>>() {})
    } ?: emptyMap()
    private fun response(manifest: PluginManifest, values: Map<String, String>, revision: Long): PluginConfigurationView {
        val secrets = manifest.settingsFields.filter { it.type == PluginFieldType.SECRET }.map { it.key }.toSet()
        val publicKeys = manifest.settingsFields.filter { it.type != PluginFieldType.SECRET }.map { it.key }.toSet()
        return PluginConfigurationView(values.filterKeys { it in publicKeys }, secrets.filter { !values[it].isNullOrBlank() }.toSet(),
            registry.missingConfiguration(manifest.id, values), revision, encryption.configured)
    }
}
