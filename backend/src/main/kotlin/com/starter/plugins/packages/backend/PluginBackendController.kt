package com.starter.plugins.packages.backend

import com.fasterxml.jackson.databind.JsonNode
import com.starter.plugins.PluginActor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class PluginBackendController(private val backend: PluginBackendService, private val broker: PluginHostBroker, private val actors: PluginActor) {
    @RequestMapping("/api/plugins/{id}/backend/**", method = [RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE])
    fun invoke(@PathVariable id: String, @AuthenticationPrincipal user: UserDetails, request: HttpServletRequest,
        @RequestBody(required = false) body: JsonNode?): ResponseEntity<JsonNode> {
        val path = request.requestURI.removePrefix(request.contextPath).removePrefix("/api/plugins/$id/backend")
        val result = backend.invoke(id, path, request.method, body,
            request.parameterMap.mapValues { it.value.toList() }, actors.current(user.username))
        val status = result.path("status").asInt(0)
        if (status !in 200..299 && status !in 400..599) throw PluginWorkerUnavailable()
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("X-Meshable-Plugin-Response", "1").body(result.get("body"))
    }
    /** Runner-only protocol. No application JWT or arbitrary plugin/user ID is accepted as authority. */
    @PostMapping("/internal/plugins/host-call")
    fun host(@RequestHeader("X-Plugin-Invocation") token: String, @RequestBody call: PluginHostCall): Map<String,Any?> = mapOf("result" to broker.call(token, call))
}
