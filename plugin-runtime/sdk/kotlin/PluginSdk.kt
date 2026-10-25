// SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.0
package com.meshable.plugins

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

data class BackendResponse(val body: Any?, val status: Int = 200)

/** Protocol v1. Include Jackson and the Kotlin runtime in the plugin's executable JAR. */
class PluginHost {
    private val json = ObjectMapper()
    private var sequence = 0
    fun call(operation: String, params: Map<String, Any?> = emptyMap()): JsonNode {
        val id = (++sequence).toString()
        val message = json.writeValueAsString(mapOf("type" to "call", "id" to id, "operation" to operation, "params" to params))
        require(message.toByteArray().size < 4 * 1024 * 1024) { "Host call exceeds limits" }
        println(message); System.out.flush()
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val next = System.`in`.read()
            check(next >= 0) { "Host disconnected" }
            if (next == 10) break
            check(bytes.size() < 4 * 1024 * 1024) { "Host response exceeds limits" }
            bytes.write(next)
        }
        val reply = json.readTree(bytes.toByteArray())
        check(reply.path("id").asText() == id && !reply.has("error")) { "Host operation denied or unavailable" }
        return reply.path("result")
    }
    fun list(table: String, offset: Int = 0) = call("data.list", mapOf("table" to table, "offset" to offset))
    fun get(table: String, id: String) = call("data.get", mapOf("table" to table, "id" to id))
    fun create(table: String, data: Any) = call("data.create", mapOf("table" to table, "data" to data))
    fun update(table: String, id: String, data: Any, revision: Long) = call("data.update", mapOf("table" to table, "id" to id, "data" to data, "revision" to revision))
    fun remove(table: String, id: String, revision: Long) = call("data.remove", mapOf("table" to table, "id" to id, "revision" to revision))
    fun sharedSettings(reference: String) = call("settings.shared", mapOf("reference" to reference))
    fun http(method: String, url: String, options: Map<String, Any?> = emptyMap()) = call("http", options + mapOf("method" to method, "url" to url))
}

object PluginWorker {
    /** stdout is reserved for protocol messages; diagnostics may use bounded stderr. */
    fun serve(handler: (JsonNode, PluginHost) -> Any?) {
        try {
            val json = ObjectMapper()
            val request = json.readTree(File(System.getenv("MESHABLE_REQUEST_FILE")))
            val value = handler(request, PluginHost())
            val response = if (value is BackendResponse) value else BackendResponse(value)
            val message = json.writeValueAsString(mapOf("type" to "result", "status" to response.status, "result" to response.body))
            require(message.toByteArray().size < 4 * 1024 * 1024) { "Result exceeds limits" }
            println(message)
        } catch (_: Exception) { println("{\"type\":\"error\"}"); kotlin.system.exitProcess(2) }
    }
}
