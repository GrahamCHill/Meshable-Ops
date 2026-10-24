package com.starter.plugins.packages.backend

import com.starter.exception.ForbiddenException
import jakarta.persistence.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Entity @Table(name = "workspace_plugin_invocations")
class BackendInvocation(@Id val tokenHash: String = "", val pluginId: String = "", val actorId: UUID = UUID.randomUUID(),
    val packageSha: String = "", val routePath: String = "", val method: String = "POST", val expiresAt: Instant = Instant.now(), var calls: Int = 0)

data class InvocationScope(val pluginId: String, val actorId: UUID, val packageSha: String, val routePath: String, val method: String)

/** Shared across backend replicas. Only a digest of the short-lived callback credential is persisted. */
@Service
class BackendInvocations(private val jdbc: JdbcTemplate) {
    fun create(scope: InvocationScope): String {
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        jdbc.update("INSERT INTO workspace_plugin_invocations (token_hash, plugin_id, actor_id, package_sha, route_path, method, expires_at, calls) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
            hash(token), scope.pluginId, scope.actorId, scope.packageSha, scope.routePath, scope.method, java.sql.Timestamp.from(Instant.now().plusSeconds(75)))
        return token
    }
    fun claim(token: String): InvocationScope {
        if (!token.matches(Regex("[A-Za-z0-9_-]{43}"))) throw ForbiddenException("Plugin invocation unavailable")
        return jdbc.query("UPDATE workspace_plugin_invocations SET calls = calls + 1 WHERE token_hash = ? AND expires_at > now() AND calls < 64 RETURNING plugin_id, actor_id, package_sha, route_path, method", { row, _ ->
            InvocationScope(row.getString("plugin_id"), row.getObject("actor_id", UUID::class.java), row.getString("package_sha"), row.getString("route_path"), row.getString("method"))
        }, hash(token)).singleOrNull() ?: throw ForbiddenException("Plugin invocation expired or exceeded its host-call limit")
    }
    fun finish(token: String) { jdbc.update("DELETE FROM workspace_plugin_invocations WHERE token_hash = ?", hash(token)) }
    @Scheduled(fixedDelay = 60000) fun clean() { jdbc.update("DELETE FROM workspace_plugin_invocations WHERE expires_at < now()") }
    private fun hash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
