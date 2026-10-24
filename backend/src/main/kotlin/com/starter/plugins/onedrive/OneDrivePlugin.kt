package com.starter.plugins.onedrive

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class OneDrivePlugin : WorkspacePlugin {
    override val manifest = PluginManifest("onedrive", "OneDrive", "1.0.0", "List files in a selected OneDrive for Business folder using Microsoft Graph.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS), supportedRoles = setOf("ADMIN"), capabilities = mapOf("microsoft.graph.credentials" to "Microsoft Graph credentials"), settingsFields = IntegrationValidation.driveFields())
    override fun validateConfiguration(values: Map<String, String>) = IntegrationValidation.drive(values)
}
