package com.starter.plugins.discord

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class DiscordPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("discord", "Discord", "1.0.0", "Post to approved Discord channels and deliver private member notifications through a bot.",
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS, PluginExtension.NOTIFICATIONS, PluginExtension.DATABASE), supportedRoles = setOf("ADMIN"),
        tables = listOf(PluginTable("plugin_discord_identities", "Internal member Discord links", setOf("ADMIN"), setOf("ADMIN"))),
        settingsFields = listOf(PluginSettingField("botToken", "Bot token", PluginFieldType.SECRET, true, "Use a Discord bot account, not a personal user token.", 4096),
            PluginSettingField("channelIds", "Approved channel IDs", help = "Comma-separated numeric channel IDs the bot may post to.", maxLength = 2000)))
    override fun validateConfiguration(values: Map<String, String>) {
        IntegrationValidation.matches(values, "botToken", "[A-Za-z0-9._-]{20,4096}")
        com.starter.plugins.integrations.MemberMessaging.validateDestinations("discord", values["channelIds"].orEmpty())
    }
}
