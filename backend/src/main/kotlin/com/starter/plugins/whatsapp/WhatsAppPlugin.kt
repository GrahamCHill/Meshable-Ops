package com.starter.plugins.whatsapp

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class WhatsAppPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("whatsapp", "WhatsApp Business", "1.0.0", "Send approved template messages through Meta's WhatsApp Cloud API.",
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS), supportedRoles = setOf("ADMIN"), settingsFields = listOf(
            PluginSettingField("accessToken", "Access token", PluginFieldType.SECRET, true, "Token with WhatsApp Business messaging permission.", 4096),
            PluginSettingField("phoneNumberId", "Sender phone number ID", required = true),
            PluginSettingField("apiVersion", "Graph API version", required = true, help = "A supported version for your Meta app, for example vNN.0."),
            PluginSettingField("templateName", "Approved template name", required = true, help = "This adapter supplies text parameters in the template body only."),
            PluginSettingField("languageCode", "Template language code", required = true, help = "For example en_US. Must match an approved translation.")))
    override fun validateConfiguration(values: Map<String, String>) {
        IntegrationValidation.matches(values, "phoneNumberId", "[0-9]{1,40}")
        IntegrationValidation.matches(values, "apiVersion", "v[0-9]{1,3}\\.0")
        IntegrationValidation.matches(values, "templateName", "[a-z0-9_]{1,200}")
        IntegrationValidation.matches(values, "languageCode", "[a-z]{2,3}(_[A-Z]{2})?")
    }
}
