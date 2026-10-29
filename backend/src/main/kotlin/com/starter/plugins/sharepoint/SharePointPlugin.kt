package com.starter.plugins.sharepoint

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class SharePointPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("sharepoint", "SharePoint", "1.0.0-a", "List files in a selected SharePoint document library folder using Microsoft Graph.",
        compatibleVersions = listOf("1.0.0", "1.1.0"),
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS), supportedRoles = setOf("ADMIN"), capabilities = mapOf("microsoft.graph.credentials" to "Microsoft Graph credentials"), settingsFields = IntegrationValidation.driveFields())
    override fun validateConfiguration(values: Map<String, String>) = IntegrationValidation.drive(values)
}
