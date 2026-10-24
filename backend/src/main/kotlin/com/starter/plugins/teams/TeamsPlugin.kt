package com.starter.plugins.teams

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class TeamsPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("teams", "Microsoft Teams", "2.0.0", "Post to Teams Workflows or approved Graph chats, with internal member identity links for private notifications.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS, PluginExtension.NOTIFICATIONS, PluginExtension.DATABASE), supportedRoles = setOf("ADMIN"),
        tables = listOf(PluginTable("plugin_teams_identities", "Internal member Teams links", setOf("ADMIN"), setOf("ADMIN"))), settingsFields = listOf(
            PluginSettingField("mode", "Delivery mode", help = "WEBHOOK (default) for explicit shared posts, or GRAPH for existing chats and private member delivery."),
            PluginSettingField("webhookUrl", "Workflows webhook URL", PluginFieldType.SECRET, false,
                "Required in WEBHOOK mode. Shared posts only; member notifications never fall back to this destination.", 4096),
            PluginSettingField("accessToken", "Graph delegated access token", PluginFieldType.SECRET, false, "GRAPH mode requires delegated ChatMessage.Send and chat/member read permissions for each existing chat. Refresh/replace expired tokens in configuration.", 4096),
            PluginSettingField("channelIds", "Approved Graph chat IDs", help = "Comma-separated existing chat IDs. The delegated account must belong to these chats.", maxLength = 4096)))
    override fun validateConfiguration(values: Map<String, String>) {
        if (values["mode"]?.uppercase()?.let { it !in setOf("WEBHOOK", "GRAPH") } == true) throw com.starter.exception.BadRequestException("Choose WEBHOOK or GRAPH delivery mode")
        values["webhookUrl"]?.let { IntegrationValidation.teamsUrl(it) }
        com.starter.plugins.integrations.MemberMessaging.validateDestinations("teams", values["channelIds"].orEmpty())
    }
    override fun missingConfiguration(values: Map<String, String>) = if (values["mode"]?.uppercase() == "GRAPH") listOf("Graph delegated access token").filter { values["accessToken"].isNullOrBlank() }
        else listOf("Workflows webhook URL").filter { values["webhookUrl"].isNullOrBlank() }
}
