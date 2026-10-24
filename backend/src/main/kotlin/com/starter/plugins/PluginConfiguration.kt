package com.starter.plugins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

@Entity @Table(name = "workspace_plugin_configuration")
class PluginConfigurationRecord(@Id val id: String = "",
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class) @Column(columnDefinition = "TEXT") var dataJson: String? = null,
    @Version var revision: Long = 0) {
    override fun toString() = "PluginConfigurationRecord(id=$id)"
}
interface PluginConfigurationRepository : JpaRepository<PluginConfigurationRecord, String>

data class PluginConfigurationView(val values: Map<String, String>, val configuredSecrets: Set<String>,
    val missingRequired: List<String>, val revision: Long, val encryptionConfigured: Boolean)
data class PluginConfigurationRequest(
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val values: Map<String, String> = emptyMap(),
    val clearSecrets: Set<String> = emptySet(), val revision: Long = -1) {
    override fun toString() = "PluginConfigurationRequest(revision=$revision)"
}
