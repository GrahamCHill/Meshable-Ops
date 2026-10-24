package com.starter.plugins.telegram

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationValidation
import org.springframework.stereotype.Component

@Component
class TelegramPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("telegram", "Telegram", "1.0.0", "Send text notifications through a Telegram bot to a configured chat.",
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS, PluginExtension.NOTIFICATIONS), supportedRoles = setOf("ADMIN"), settingsFields = listOf(
            PluginSettingField("botToken", "Bot token", PluginFieldType.SECRET, true, "Obtain the token from BotFather.", 2048),
            PluginSettingField("chatId", "Destination chat ID", required = true, help = "The bot must already have access to this chat.")))
    override fun validateConfiguration(values: Map<String, String>) {
        IntegrationValidation.matches(values, "botToken", "[0-9]+:[A-Za-z0-9_-]+")
        IntegrationValidation.matches(values, "chatId", "-?[0-9]+|@[A-Za-z0-9_]{5,64}")
    }
}
