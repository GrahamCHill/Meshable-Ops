package com.starter.plugins.packages.backend

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URI

class PluginWorkerUnavailable : RuntimeException("Plugin backend unavailable. Check the plugin runner, worker images and package entrypoint.")

@Component
class PluginWorkerClient(private val mapper: ObjectMapper,
    @Value("\${app.plugins.runner-url:http://plugin-runner:8090}") private val url: String,
    @Value("\${app.plugins.runner-token:}") private val token: String) {
    val configured get() = token.length >= 32 && url.isNotBlank()
    private val http = RestTemplate(object : SimpleClientHttpRequestFactory() {
        init { setConnectTimeout(3000); setReadTimeout(60000) }
        override fun prepareConnection(connection: java.net.HttpURLConnection, method: String) { super.prepareConnection(connection, method); connection.instanceFollowRedirects = false }
    })
    fun invoke(payload: Map<String, Any?>): JsonNode {
        if (!configured) throw PluginWorkerUnavailable()
        try {
            return http.execute(URI(url.trimEnd('/') + "/execute"), HttpMethod.POST, { request ->
                request.headers.contentType = MediaType.APPLICATION_JSON; request.headers.setBearerAuth(token)
                mapper.writeValue(request.body, payload)
            }, { response ->
                if (!response.statusCode.is2xxSuccessful) throw PluginWorkerUnavailable()
                val bytes = response.body.readNBytes(4 * 1024 * 1024 + 1)
                if (bytes.size > 4 * 1024 * 1024) throw PluginWorkerUnavailable()
                mapper.readTree(bytes)
            }) ?: throw PluginWorkerUnavailable()
        } catch (_: Exception) { throw PluginWorkerUnavailable() }
    }
}
