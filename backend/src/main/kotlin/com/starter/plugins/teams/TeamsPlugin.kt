package com.starter.plugins.teams

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class TeamsPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("teams", "Microsoft Teams", "1.0.0", "Post notification cards through a Teams Workflows webhook.",
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS), supportedRoles = setOf("ADMIN"), settingsFields = listOf(
            PluginSettingField("webhookUrl", "Workflows webhook URL", PluginFieldType.SECRET, true,
                "Use a Teams Workflows trigger that accepts this server's unauthenticated webhook request. The full URL is stored as a credential.", 4096)))
    override fun validateConfiguration(values: Map<String, String>) { values["webhookUrl"]?.let { IntegrationValidation.teamsUrl(it) } }
}
