package com.starter.plugins.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.EmailDeliveryResult
import com.starter.exception.*
import com.starter.notification.ChannelMessage
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import com.starter.repository.postgres.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.util.UUID

data class MemberMessageIdentity(val userId: UUID, val remoteUserId: String, val remoteName: String, val destinationId: String, val revision: Long)
data class MemberIdentityInput(val remoteUserId: String, val remoteName: String = "", val destinationId: String = "", val revision: Long = -1)
data class ChannelPostInput(val destination: String, val text: String)

@Service
class MemberMessaging(private val registry: PluginRegistry, private val configuration: PluginConfigurationService,
    private val users: UserRepository, private val jdbc: JdbcTemplate, private val http: IntegrationHttpClient, private val mapper: ObjectMapper) {
    private fun table(id: String): String { if (id !in IDS) throw NotFoundException("Member messaging plugin unavailable"); return "plugin_${id}_identities" }
    fun identities(id: String): List<MemberMessageIdentity> { val table = table(id); return jdbc.query("SELECT * FROM $table ORDER BY remote_name, user_id", { rs, _ -> MemberMessageIdentity(rs.getObject("user_id", UUID::class.java), rs.getString("remote_user_id"), rs.getString("remote_name"), rs.getString("destination_id"), rs.getLong("revision")) }) }
    fun options() = users.findAll().filter { it.active && it.role != "EXTERNAL" }.map { mapOf("id" to it.id, "name" to (it.name ?: it.username), "email" to it.email) }
    @Transactional
    fun save(id: String, userId: UUID, input: MemberIdentityInput): MemberMessageIdentity {
        val table = table(id)
        if (users.findById(userId).orElse(null)?.let { it.active && it.role != "EXTERNAL" } != true) throw BadRequestException("Choose an active internal member")
        if (input.remoteName.length > 160 || input.remoteName.any(Char::isISOControl)) throw BadRequestException("Use a display name up to 160 characters")
        val destination = if (id == "telegram") input.destinationId.ifBlank { input.remoteUserId } else input.destinationId
        when (id) {
            "discord" -> if (!input.remoteUserId.matches(Regex("[0-9]{7,25}")) || destination.isNotBlank()) throw BadRequestException("Use the Discord numeric user ID; direct message channels are resolved by the bot")
            "telegram" -> if (!input.remoteUserId.matches(Regex("[1-9][0-9]{3,24}")) || destination != input.remoteUserId) throw BadRequestException("Private Telegram delivery requires the member's positive user/chat ID, not a shared group")
            "teams" -> if (runCatching { UUID.fromString(input.remoteUserId) }.isFailure || !validDestination("teams", destination)) throw BadRequestException("Use the Entra user UUID and an existing one-to-one Graph chat ID")
        }
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", org.springframework.jdbc.core.RowCallbackHandler { }, "message-identity:$id")
        val prior = identities(id).find { it.userId == userId }
        if ((prior?.revision ?: -1) != input.revision) throw ConflictException("This identity link changed. Reload before saving.")
        if (identities(id).any { it.userId != userId && it.remoteUserId == input.remoteUserId }) throw ConflictException("That remote identity is already linked to another member")
        if (prior == null) jdbc.update("INSERT INTO $table (user_id, remote_user_id, remote_name, destination_id) VALUES (?, ?, ?, ?)", userId, input.remoteUserId, input.remoteName, destination)
        else jdbc.update("UPDATE $table SET remote_user_id = ?, remote_name = ?, destination_id = ?, revision = revision + 1 WHERE user_id = ?", input.remoteUserId, input.remoteName, destination, userId)
        return identities(id).first { it.userId == userId }
    }
    @Transactional
    fun remove(id: String, userId: UUID, revision: Long) { if (jdbc.update("DELETE FROM ${table(id)} WHERE user_id = ? AND revision = ?", userId, revision) != 1) throw ConflictException("The identity link changed. Reload before removing it.") }
    fun available(id: String): Boolean = runCatching {
        registry.requireAvailable(id, "ADMIN")
        configuration.view(id).missingRequired.isEmpty() && (id != "teams" || configuration.current(id)["mode"]?.uppercase() == "GRAPH")
    }.getOrDefault(false)
    fun reachable(id: String, userId: UUID) = available(id) && identities(id).any { it.userId == userId } && users.findById(userId).orElse(null)?.let { it.active && it.role != "EXTERNAL" } == true
    fun sendMember(id: String, message: ChannelMessage): EmailDeliveryResult {
        val userId = runCatching { UUID.fromString(message.recipient) }.getOrElse { throw BadRequestException("Select a linked internal member") }
        if (!reachable(id, userId)) return EmailDeliveryResult("UNAVAILABLE", "The member has no available private delivery link")
        val identity = identities(id).first { it.userId == userId }; val config = configuration.current(id)
        return attempt(id, message.text) {
            when (id) {
                "discord" -> {
                    val dm = http.request("POST", "https://discord.com/api/v10/users/@me/channels", mapOf("Authorization" to "Bot ${config.getValue("botToken")}"), mapper.writeValueAsString(mapOf("recipient_id" to identity.remoteUserId))).body?.path("id")?.asText().orEmpty()
                    if (!validDestination("discord", dm)) throw IntegrationUnavailable("Discord could not create a direct conversation")
                    discord(config, dm, message.text)
                }
                "telegram" -> telegram(config, identity.destinationId, message.text)
                "teams" -> {
                    val path = "https://graph.microsoft.com/v1.0/chats/${encode(identity.destinationId)}"
                    val headers = mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}")
                    val chat = http.request("GET", path, headers).body
                    if (chat?.path("chatType")?.asText() != "oneOnOne") throw IntegrationUnavailable("Private member delivery requires a one-to-one Teams chat")
                    val members = http.request("GET", "$path/members", headers).body?.path("value")
                    if (members?.isArray != true || members.size() > 2 || members.none { it.path("userId").asText().equals(identity.remoteUserId, true) }) throw IntegrationUnavailable("The linked member is not in this private Teams chat")
                    teams(config, identity.destinationId, message.text)
                }
            }
        }
    }
    fun destinations(id: String): List<String> {
        table(id); val config = configuration.current(id)
        if (id == "teams" && config["mode"]?.uppercase() != "GRAPH") return listOf("default")
        return (config["channelIds"].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() } + listOfNotNull(config["chatId"])).distinct()
    }
    fun post(id: String, input: ChannelPostInput): EmailDeliveryResult {
        table(id); registry.requireAvailable(id, "ADMIN")
        if (configuration.view(id).missingRequired.isNotEmpty()) return EmailDeliveryResult("UNAVAILABLE", "Complete the plugin configuration")
        if (input.destination !in destinations(id)) throw BadRequestException("Choose an administrator-approved destination")
        val config = configuration.current(id)
        return attempt(id, input.text) {
            when (id) {
                "discord" -> discord(config, input.destination, input.text)
                "telegram" -> telegram(config, input.destination, input.text)
                "teams" -> if (config["mode"]?.uppercase() == "GRAPH") teams(config, input.destination, input.text) else {
                    http.request("POST", config.getValue("webhookUrl"), body = mapper.writeValueAsString(mapOf("type" to "message", "attachments" to listOf(mapOf("contentType" to "application/vnd.microsoft.card.adaptive", "content" to mapOf("type" to "AdaptiveCard", "version" to "1.2", "body" to listOf(mapOf("type" to "TextBlock", "text" to input.text, "wrap" to true))))))))
                }
            }
        }
    }
    private fun attempt(id: String, text: String, action: () -> Unit): EmailDeliveryResult {
        if (text.isBlank() || text.length > (if (id == "discord") 2000 else 4000) || text.any { it.isISOControl() && it !in "\r\n\t" }) throw BadRequestException("Use a bounded plain-text message")
        return try { registry.requireAvailable(id, "ADMIN"); action(); EmailDeliveryResult("ACCEPTED", "Accepted by the provider; recipient delivery is not confirmed") }
        catch (_: Exception) { EmailDeliveryResult("UNAVAILABLE", "The provider could not confirm acceptance. Check the destination, permissions, and credentials before retrying.") }
    }
    private fun discord(config: Map<String, String>, channel: String, text: String) { http.request("POST", "https://discord.com/api/v10/channels/$channel/messages", mapOf("Authorization" to "Bot ${config.getValue("botToken")}"), mapper.writeValueAsString(mapOf("content" to text, "allowed_mentions" to mapOf("parse" to emptyList<String>())))) }
    private fun telegram(config: Map<String, String>, chat: String, text: String) { if (http.request("POST", "https://api.telegram.org/bot${config.getValue("botToken")}/sendMessage", body = mapper.writeValueAsString(mapOf("chat_id" to chat, "text" to text))).body?.path("ok")?.asBoolean() != true) throw IntegrationUnavailable("Telegram did not accept the message") }
    private fun teams(config: Map<String, String>, chat: String, text: String) { http.request("POST", "https://graph.microsoft.com/v1.0/chats/${encode(chat)}/messages", mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}"), mapper.writeValueAsString(mapOf("body" to mapOf("contentType" to "text", "content" to text)))) }
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    companion object {
        val IDS = setOf("discord", "telegram", "teams")
        fun validDestination(id: String, value: String) = when (id) { "discord" -> value.matches(Regex("[0-9]{7,25}")); "telegram" -> value.matches(Regex("-?[0-9]{4,25}|@[A-Za-z0-9_]{5,64}")); "teams" -> value.matches(Regex("[A-Za-z0-9:@._-]{5,200}")); else -> false }
        fun validateDestinations(id: String, raw: String) { val ids = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }; if (ids.size > 30 || ids.any { !validDestination(id, it) }) throw BadRequestException("Use up to 30 comma-separated valid destination IDs") }
    }
}
