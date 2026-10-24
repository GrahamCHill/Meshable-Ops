package com.starter.plugins.integrations

import com.starter.exception.BadRequestException
import com.starter.plugins.*
import java.net.URI

object IntegrationValidation {
    fun matches(values: Map<String, String>, key: String, pattern: String) {
        values[key]?.let { if (!it.matches(Regex(pattern))) throw BadRequestException("Invalid integration field: $key") }
    }
    fun teamsUrl(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        if (uri == null || uri.scheme != "https" || uri.port !in listOf(-1, 443) || uri.userInfo != null || uri.fragment != null ||
            !(host.endsWith(".api.powerplatform.com") || host.endsWith(".logic.azure.com")) || uri.path.isNullOrBlank())
            throw BadRequestException("Use an HTTPS Teams Workflows URL hosted by Power Platform or Azure Logic Apps")
        return uri
    }
    fun driveFields() = listOf(
        PluginSettingField("tenantId", "Directory (tenant) ID", required = true, help = "Microsoft Entra tenant UUID."),
        PluginSettingField("clientId", "Application (client) ID", required = true),
        PluginSettingField("clientSecret", "Client secret", PluginFieldType.SECRET, true, "Application secret value, not the secret ID.", 4096),
        PluginSettingField("driveId", "Drive ID", required = true, help = "Document library or OneDrive for Business drive ID."),
        PluginSettingField("rootItemId", "Root folder item ID", help = "Leave blank to list the drive root. Otherwise only this folder is listed."))
    fun drive(values: Map<String, String>) {
        for (key in listOf("tenantId", "clientId")) matches(values, key, "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
        for (key in listOf("driveId", "rootItemId")) matches(values, key, "[A-Za-z0-9!_,.-]{1,200}")
    }
}
