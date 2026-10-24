package com.starter.plugins.calendar

import com.starter.plugins.*
import com.starter.plugins.integrations.*
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class CalendarPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("calendar", "Calendar", "1.1.1", "Private events, board deadlines, calendar export, and optional connected calendars.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE, PluginExtension.SETTINGS),
        navigationLabel = "Calendar", capabilities = mapOf("calendar.events" to "Calendar events"),
        tables = listOf(PluginTable("plugin_calendar_sync_links", "Personal sync relationships"), PluginTable("plugin_calendar_events", "Calendar events"),
            PluginTable("plugin_calendar_connections", "Personal connections and encrypted credentials")),
        features = listOf(PluginFeature("board-deadlines", "Kanban deadlines and sprints", setOf("kanban.boards")),
            PluginFeature("connected-calendars", "External calendar connections", setOf("calendar.provider"))))
}
@Component
class MicrosoftCalendarPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("calendar-microsoft", "Microsoft Calendar", "1.0.0-a", "Shared Outlook calendars and private two-way member sync through Microsoft Graph.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API,PluginExtension.SETTINGS), defaultRoles = setOf("ADMIN"),
        requiresCapabilities = setOf("calendar.events"), capabilities = mapOf("calendar.provider" to "External calendar connections", "calendar.personal-sync" to "Personal calendar sync"),
        features = listOf(PluginFeature("shared-credentials", "Reuse a SharePoint or OneDrive connection", setOf("microsoft.graph.credentials"))),
        settingsFields = listOf(
            PluginSettingField("credentialPlugin", "Reuse connection from plugin", help = "Optional: onedrive or sharepoint. That plugin must be enabled. Blank uses the credentials below."),
            PluginSettingField("tenantId", "Directory (tenant) ID"), PluginSettingField("clientId", "Application (client) ID"),
            PluginSettingField("clientSecret", "Client secret", PluginFieldType.SECRET, maxLength = 4096),
            PluginSettingField("mailbox", "Mailbox address or user ID", required = true),
            PluginSettingField("calendarId", "Calendar ID", help = "Blank uses this mailbox's default calendar.", maxLength = 1000)))
    override fun validateConfiguration(values: Map<String,String>) {
        values["credentialPlugin"]?.let { if (it !in setOf("onedrive","sharepoint")) throw BadRequestException("Choose onedrive or sharepoint") }
        IntegrationValidation.drive(values.filterKeys { it in setOf("tenantId","clientId") })
    }
    override fun missingConfiguration(values: Map<String,String>): List<String> = manifest.settingsFields.filter {
        (it.required || (values["credentialPlugin"].isNullOrBlank() && it.key in setOf("tenantId","clientId","clientSecret"))) && values[it.key].isNullOrBlank()
    }.map { it.label }
}
@Component
class GoogleCalendarPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("calendar-google", "Google Calendar", "1.0.0-a", "Shared Google calendars and private two-way member sync using offline OAuth grants.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API,PluginExtension.SETTINGS), defaultRoles = setOf("ADMIN"), requiresCapabilities = setOf("calendar.events"),
        capabilities = mapOf("calendar.provider" to "External calendar connections", "calendar.personal-sync" to "Personal calendar sync"), settingsFields = listOf(
            PluginSettingField("clientId", "OAuth client ID", required = true, maxLength = 1000),
            PluginSettingField("clientSecret", "OAuth client secret", PluginFieldType.SECRET, true, maxLength = 4096),
            PluginSettingField("refreshToken", "Offline refresh token", PluginFieldType.SECRET, true, "Grant Calendar read access, plus write access if publishing is needed.", 4096),
            PluginSettingField("calendarId", "Calendar ID", required = true, help = "Use primary or a calendar ID accessible to the authorized Google account.", maxLength = 1000)))
}
@Component
class CalDavCalendarPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("calendar-caldav", "CalDAV Calendar", "1.0.0-a", "Connect a selected CalDAV calendar, including iCloud, Nextcloud, and Fastmail.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API,PluginExtension.SETTINGS), defaultRoles = setOf("ADMIN"), requiresCapabilities = setOf("calendar.events"),
        capabilities = mapOf("calendar.provider" to "External calendar connections", "calendar.personal-sync" to "Personal calendar sync"), settingsFields = listOf(
            PluginSettingField("calendarUrl", "CalDAV calendar collection URL", required = true, help = "The complete HTTPS calendar collection URL, not the account discovery URL.", maxLength = 2000),
            PluginSettingField("username", "Account username", PluginFieldType.SECRET, true, maxLength = 1000),
            PluginSettingField("password", "App password", PluginFieldType.SECRET, true, "Use the provider's app-specific password when required.", 4096)))
    override fun validateConfiguration(values: Map<String,String>) { values["calendarUrl"]?.let { IntegrationHttpClient.endpoint(it) } }
}
