package com.starter.plugins.integrations

import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Read-only selected-root metadata; no sync, content download, or search ingestion. */
@Service
class MicrosoftDriveService(private val registry: PluginRegistry, private val settings: PluginConfigurationService,
    private val http: IntegrationHttpClient) {
    fun files(id: String): ExternalDrivePage {
        if (id !in setOf("sharepoint", "onedrive")) throw NotFoundException("File integration unavailable")
        registry.requireAvailable(id, "ADMIN")
        val values = settings.current(id)
        if (registry.definition(id).settingsFields.any { it.required && values[it.key].isNullOrBlank() }) throw BadRequestException("Complete this plugin's connection settings first")
        registry.validateConfiguration(id, values)
        val form = mapOf("client_id" to values.getValue("clientId"), "client_secret" to values.getValue("clientSecret"),
            "scope" to "https://graph.microsoft.com/.default", "grant_type" to "client_credentials")
            .entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val token = http.request("POST", "https://login.microsoftonline.com/${values.getValue("tenantId")}/oauth2/v2.0/token",
            body = form, contentType = "application/x-www-form-urlencoded").body?.path("access_token")?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IntegrationUnavailable("Microsoft did not return an access token")
        val root = values["rootItemId"]?.let { "items/${encode(it)}" } ?: "root"
        registry.requireAvailable(id, "ADMIN")
        val page = http.request("GET", "https://graph.microsoft.com/v1.0/drives/${encode(values.getValue("driveId"))}/$root/children?\$top=100&\$select=id,name,size,folder",
            headers = mapOf("Authorization" to "Bearer $token")).body ?: throw IntegrationUnavailable("Microsoft did not return file metadata")
        if (!page.path("value").isArray) throw IntegrationUnavailable("Microsoft returned an unsupported file listing")
        return ExternalDrivePage(page.path("value").take(100).map { ExternalDriveItem(it.path("id").asText(), it.path("name").asText(),
            it.path("size").asLong(), it.path("folder").isObject) }, page.path("@odata.nextLink").isTextual && page.path("@odata.nextLink").asText().isNotBlank())
    }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
data class ExternalDriveItem(val id: String, val name: String, val size: Long, val folder: Boolean)
data class ExternalDrivePage(val items: List<ExternalDriveItem>, val hasMore: Boolean)
