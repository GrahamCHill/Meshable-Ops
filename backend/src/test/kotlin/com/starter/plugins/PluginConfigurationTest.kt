package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.plugins.telegram.TelegramPlugin
import com.starter.plugins.teams.TeamsPlugin
import com.starter.plugins.whatsapp.WhatsAppPlugin
import com.starter.plugins.sharepoint.SharePointPlugin
import com.starter.plugins.onedrive.OneDrivePlugin
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.security.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class PluginConfigurationTest {
    private val mapper = jacksonObjectMapper()
    private val records = mock(PluginConfigurationRepository::class.java)
    private val registry = PluginRegistry(listOf(TelegramPlugin(), TeamsPlugin(), WhatsAppPlugin(), SharePointPlugin(), OneDrivePlugin()), mock(PluginSettingsRepository::class.java))
    private val service = PluginConfigurationService(registry, records, mapper, testEncryption())
    private val row = PluginConfigurationRecord("telegram")
    init {
        `when`(records.findById("telegram")).thenReturn(Optional.of(row))
        `when`(records.saveAndFlush(row)).thenAnswer { row.revision++; row }
    }
    @Test fun `configuration is encrypted and API responses never echo credentials`() {
        val token = "123456:private_canary_token"
        val result = service.save("telegram", PluginConfigurationRequest(mapOf("botToken" to token, "chatId" to "-100123456"), revision = 0))
        assertThat(result.values).containsExactlyEntriesOf(mapOf("chatId" to "-100123456"))
        assertThat(result.configuredSecrets).containsExactly("botToken")
        assertThat(result.missingRequired).isEmpty()
        assertThat(mapper.writeValueAsString(result)).doesNotContain(token)
        val converter = EncryptedSecretConverter(testEncryption())
        val stored = converter.convertToDatabaseColumn(row.dataJson)
        assertThat(stored).startsWith("enc:v1:").doesNotContain(token, "chatId")
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(row.dataJson)
        assertThat(PluginConfigurationRequest(mapOf("botToken" to token)).toString()).doesNotContain(token)
    }
    @Test fun `blank credentials preserve saved values and explicit removal clears them`() {
        row.dataJson = """{"botToken":"123456:keep_this_secret","chatId":"12345"}"""
        service.save("telegram", PluginConfigurationRequest(mapOf("botToken" to "", "chatId" to "67890"), revision = 0))
        assertThat(mapper.readTree(row.dataJson).path("botToken").asText()).isEqualTo("123456:keep_this_secret")
        val cleared = service.save("telegram", PluginConfigurationRequest(clearSecrets = setOf("botToken"), revision = row.revision))
        assertThat(cleared.configuredSecrets).isEmpty()
        assertThat(cleared.missingRequired).contains("Bot token")
        assertThat(row.dataJson).doesNotContain("keep_this_secret")
    }
    @Test fun `stale writes unknown fields invalid values and credential ambiguity are rejected`() {
        row.revision = 4
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(revision = 3)) }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(mapOf("unknown" to "value"), revision = 4)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(mapOf("chatId" to "../../root"), revision = 4)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(mapOf("botToken" to "123456:token"), setOf("botToken"), 4)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(mapOf("chatId" to "1\n2"), revision = 4)) }.isInstanceOf(BadRequestException::class.java)
        verify(records, never()).saveAndFlush(any(PluginConfigurationRecord::class.java))
    }
    @Test fun `removed schema fields never become public accidentally`() {
        row.dataJson = """{"retiredSecret":"private_legacy_secret","chatId":"12345"}"""
        assertThat(mapper.writeValueAsString(service.view("telegram"))).doesNotContain("private_legacy_secret", "retiredSecret")
    }
    @Test fun `an unsaved configuration revision cannot overwrite the first saved record`() {
        `when`(records.findById("telegram")).thenReturn(Optional.empty())
        assertThat(service.view("telegram").revision).isEqualTo(-1)
        `when`(records.findById("telegram")).thenReturn(Optional.of(row))
        assertThatThrownBy { service.save("telegram", PluginConfigurationRequest(mapOf("chatId" to "12345"), revision = -1)) }.isInstanceOf(ConflictException::class.java)
        verify(records, never()).saveAndFlush(any(PluginConfigurationRecord::class.java))
    }
    @Test fun `saving requires a server wrapping key even for partial configuration`() {
        val unconfigured = PluginConfigurationService(registry, records, mapper, SecretEncryption(""))
        assertThatThrownBy { unconfigured.save("telegram", PluginConfigurationRequest(mapOf("chatId" to "12345"))) }.isInstanceOf(BadRequestException::class.java)
        verify(records, never()).saveAndFlush(any(PluginConfigurationRecord::class.java))
    }
    @Test fun `integration manifests remain separate admin only modules and validate external endpoints`() {
        assertThatThrownBy { registry.configure("telegram", PluginSettingsRequest(true)) }.isInstanceOf(BadRequestException::class.java)
        assertThat(registry.definition("sharepoint").settingsFields).isEqualTo(registry.definition("onedrive").settingsFields)
        for (id in listOf("telegram", "teams", "whatsapp", "sharepoint", "onedrive")) assertThat(registry.definition(id).supportedRoles).containsExactly("ADMIN")
        listOf("http://localhost/a", "https://localhost/a", "https://example.org/a", "https://api.powerplatform.com.evil.test/a", "https://user:pwd@a.logic.azure.com/path", "https://a.logic.azure.com:8443/path").forEach { url ->
            assertThatThrownBy { registry.validateConfiguration("teams", mapOf("webhookUrl" to url)) }.isInstanceOf(BadRequestException::class.java)
        }
        registry.validateConfiguration("teams", mapOf("webhookUrl" to "https://tenant.environment.api.powerplatform.com/workflows/example?sig=canary"))
        assertThatThrownBy { registry.validateConfiguration("sharepoint", mapOf("tenantId" to "../../tenant")) }.isInstanceOf(BadRequestException::class.java)
    }
}
