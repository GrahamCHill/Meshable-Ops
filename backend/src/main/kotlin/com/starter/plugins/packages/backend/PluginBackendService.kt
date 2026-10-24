package com.starter.plugins.packages.backend

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.AccessControlService
import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.*
import com.starter.plugins.packages.*
import com.starter.repository.postgres.UserRepository
import com.starter.security.RateLimits
import org.springframework.stereotype.Service
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Service
class PluginBackendService(private val packages: InstalledPluginCatalogue, private val registry: PluginRegistry,
    private val controls: AccessControlService, private val users: UserRepository, private val configuration: PluginConfigurationService,
    private val tickets: BackendInvocations, private val worker: PluginWorkerClient, private val mapper: ObjectMapper, private val rates: RateLimits) {
    data class Authorized(val actor: User, val record: PluginPackageRecord, val descriptor: PluginPackageDescriptor, val route: PluginBackendRoute, val method: String)
    fun authorize(id: String, path: String, method: String, actor: User, sha: String? = null): Authorized {
        val fresh = users.findById(actor.id).orElse(null)?.takeIf { it.active } ?: throw ForbiddenException("Active account required")
        if ("plugin.$id" !in controls.features(fresh)) throw ForbiddenException("This plugin is not available to your account")
        registry.requireAvailable(id, fresh.role)
        val record = packages.find(id)?.takeIf { sha == null || it.sha256 == sha } ?: throw NotFoundException("Plugin backend unavailable")
        val descriptor = packages.descriptor(record)
        val route = descriptor.backend?.routes?.find { it.path == path && method in it.methods && Roles.plugin(fresh.role) in it.roles }
            ?: throw NotFoundException("Plugin backend route unavailable")
        return Authorized(fresh, record, descriptor, route, method)
    }
    fun invoke(id: String, path: String, method: String, body: JsonNode?, query: Map<String,List<String>>, actor: User): JsonNode {
        val auth = authorize(id, path, method, actor)
        rates.check("plugin-backend", "${actor.id}:$id", 30, 60)
        if (mapper.writeValueAsBytes(body).size > 65536 || mapper.writeValueAsBytes(query).size > 8192) throw BadRequestException("Plugin requests support at most 64 KiB JSON and 8 KiB query parameters")
        val scope = InvocationScope(id, auth.actor.id, auth.record.sha256, path, method)
        val ticket = tickets.create(scope)
        try {
            val module = auth.descriptor.backend!!.modules.getValue(auth.route.module)
            val files: Map<String,String> = mapper.readValue(auth.record.filesJson, object: TypeReference<Map<String,String>>() {})
            val result = worker.invoke(mapOf("runtime" to module.runtime, "entrypoint" to module.entrypoint,
                "files" to files.filterKeys { it.startsWith("backend/") }, "callbackToken" to ticket,
                "request" to mapOf("plugin" to mapOf("id" to id, "version" to auth.descriptor.manifest.version),
                    "actor" to mapOf("id" to auth.actor.id, "role" to auth.actor.role), "method" to method, "path" to path, "query" to query, "body" to body,
                    "configuration" to configuration.view(id).values)))
            authorize(id, path, method, auth.actor, auth.record.sha256)
            return result
        } finally { tickets.finish(ticket) }
    }
    /** A runner callback is bound to a live account, immutable package and declared route, never a caller-supplied identity. */
    fun <T> callback(token: String, action: (Authorized) -> T): T {
        val scope = tickets.claim(token)
        val actor = users.findById(scope.actorId).orElse(null)?.takeIf { it.active } ?: throw ForbiddenException("Account unavailable")
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(actor.email, null, listOf(SimpleGrantedAuthority("ROLE_${actor.role}")))
        SecurityContextHolder.setContext(context)
        try { return action(authorize(scope.pluginId, scope.routePath, scope.method, actor, scope.packageSha)) }
        finally { SecurityContextHolder.setContext(previous) }
    }
}
