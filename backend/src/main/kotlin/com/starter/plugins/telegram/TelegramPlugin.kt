package com.starter.plugins.telegram

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class TelegramPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("telegram", "Telegram", "2.0.0", "Post to approved Telegram chats and send private member notifications through linked bot chats.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS, PluginExtension.NOTIFICATIONS, PluginExtension.DATABASE), supportedRoles = setOf("ADMIN"),
        tables = listOf(PluginTable("plugin_telegram_identities", "Internal member Telegram links", setOf("ADMIN"), setOf("ADMIN"))), settingsFields = listOf(
            PluginSettingField("botToken", "Bot token", PluginFieldType.SECRET, true, "Obtain the token from BotFather.", 2048),
            PluginSettingField("chatId", "Default destination chat ID", help = "Optional shared destination for explicit admin posts; never used for private member notifications."),
            PluginSettingField("channelIds", "Other approved chat IDs", help = "Comma-separated chat IDs or @channel names the bot may post to.", maxLength = 2000)))
    override fun validateConfiguration(values: Map<String, String>) {
        IntegrationValidation.matches(values, "botToken", "[0-9]+:[A-Za-z0-9_-]+")
        IntegrationValidation.matches(values, "chatId", "-?[0-9]+|@[A-Za-z0-9_]{5,64}")
        com.starter.plugins.integrations.MemberMessaging.validateDestinations("telegram", values["channelIds"].orEmpty())
    }
}
