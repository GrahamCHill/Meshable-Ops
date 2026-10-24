package com.starter.plugins.packages.backend

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginFieldType
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.packages.*
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

data class PluginHostCall(val operation: String, val params: JsonNode)

/** The worker has no network or database socket. Each requested host operation is checked here. */
@Service
class PluginHostBroker(private val backend: PluginBackendService, private val records: PluginRecordService,
    private val configuration: PluginConfigurationService, private val http: IntegrationHttpClient, private val mapper: ObjectMapper) {
    fun call(token: String, call: PluginHostCall): Any? = backend.callback(token) { auth ->
        val input = call.params
        if (!input.isObject || mapper.writeValueAsBytes(input).size > 1_048_576) throw BadRequestException("Invalid plugin host call")
        val permission = when (call.operation) { "data.list", "data.get" -> "data.read"; "data.create", "data.update", "data.remove" -> "data.write"; "http" -> "http"; else -> throw BadRequestException("Unsupported host operation") }
        if (permission !in auth.route.permissions) throw ForbiddenException("This backend route has not declared that permission")
        val id = auth.record.id
        if (permission == "http") return@callback outbound(auth, input)
        val table = input.path("table").asText()
        val record = input.path("id").asText()
        fun uuid() = runCatching { UUID.fromString(record) }.getOrElse { throw BadRequestException("Invalid record ID") }
        when (call.operation) {
            "data.list" -> records.list(id, table, auth.actor, input.path("offset").asInt(0))
            "data.get" -> records.get(id, table, uuid(), auth.actor)
            "data.create" -> records.create(id, table, PluginRecordInput(input.path("data")), auth.actor)
            "data.update" -> records.update(id, table, uuid(), PluginRecordInput(input.path("data"), input.path("revision").asLong(-1)), auth.actor)
            "data.remove" -> { records.delete(id, table, uuid(), input.path("revision").asLong(-1), auth.actor); null }
            else -> throw BadRequestException("Unsupported host operation")
        }
    }
    private fun outbound(auth: PluginBackendService.Authorized, input: JsonNode): Any {
        val url = input.path("url").asText()
        val uri = runCatching { URI(url) }.getOrNull() ?: throw BadRequestException("Invalid HTTP URL")
        val origin = PluginBackendValidation.origin(url)
        if (origin !in auth.descriptor.backend!!.allowedOrigins) throw ForbiddenException("HTTP origin not declared by this plugin")
        val method = input.path("method").asText("GET")
        if (method !in PluginBackendValidation.methods || auth.method == "GET" && method != "GET") throw ForbiddenException("HTTP method not permitted")
        val headers = input.path("headers").takeIf { it.isObject }?.fields()?.asSequence()?.associate { it.key to it.value.asText() }?.toMutableMap() ?: mutableMapOf()
        val forbidden = setOf("host", "cookie", "set-cookie", "content-length", "transfer-encoding", "connection", "proxy-authorization", "proxy-connection")
        if (headers.size > 20 || headers.keys.map(String::lowercase).distinct().size != headers.size || headers.any { (key, value) ->
            !key.matches(Regex("[A-Za-z][A-Za-z0-9-]{0,79}")) || key.lowercase() in forbidden || value.length > 4096 || value.any(Char::isISOControl) }) throw BadRequestException("Unsupported HTTP headers")
        input.path("credential").takeIf { it.isObject }?.let { credential ->
            val key = credential.path("key").asText(); val header = credential.path("header").asText("Authorization"); val prefix = credential.path("prefix").asText("Bearer ")
            if (auth.descriptor.manifest.settingsFields.none { it.key == key && it.type == PluginFieldType.SECRET } ||
                !header.matches(Regex("[A-Za-z][A-Za-z0-9-]{0,79}")) || header.lowercase() in forbidden || prefix.length > 40 || prefix.any(Char::isISOControl)) throw BadRequestException("Choose a declared secret and supported credential header")
            val secret = configuration.current(auth.record.id)[key]?.takeIf { it.isNotBlank() } ?: throw BadRequestException("Configure the plugin credential first")
            headers.keys.filter { it.equals(header, true) }.toList().forEach(headers::remove)
            headers[header] = prefix + secret // Credentials are injected by the host, never given to page/worker code.
        }
        val body = input.get("body")?.takeUnless { it.isNull }?.let { if (it.isTextual) it.asText() else mapper.writeValueAsString(it) }
        if (url.length > 4096 || body?.toByteArray()?.size?.let { it > 65536 } == true) throw BadRequestException("HTTP request exceeds plugin limits")
        val response = http.scoped(method, origin, uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: ""), headers, body)
        return mapOf("status" to response.status, "body" to response.body, "text" to response.text)
    }
}
