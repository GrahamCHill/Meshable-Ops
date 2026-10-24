package com.starter.plugins.integrations

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.webhook.WebhookHttpClient
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.util.Timeout
import org.springframework.stereotype.Component
import java.net.URI
import org.springframework.beans.factory.annotation.Value

class IntegrationUnavailable(message: String) : RuntimeException(message)
data class IntegrationResponse(val status: Int, val body: JsonNode?, val text: String = "", val headers: Map<String,String> = emptyMap()) {
    override fun toString() = "IntegrationResponse(status=$status)"
}

/** No redirects/retries, bounded bodies, public DNS targets, and no credential URLs in errors. */
@Component
class IntegrationHttpClient(private val mapper: ObjectMapper, @Value("\${app.plugins.allow-private-targets:false}") private val allowPrivate: Boolean = false) {
    private val network = WebhookHttpClient(allowPrivate)
    fun request(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null,
        contentType: String = "application/json"): IntegrationResponse = execute(method, validate(url), headers, body, contentType, emptySet())

    /** The base is administrator configuration, never a URL taken from provider responses or API callers. */
    fun scoped(method: String, base: String, path: String = "", headers: Map<String, String> = emptyMap(), body: String? = null,
        contentType: String = "application/json", allowedStatuses: Set<Int> = emptySet()): IntegrationResponse {
        val origin = endpoint(base)
        if (path.startsWith("//") || path.contains(Regex("(^|/)\\.\\.(/|$)")) || path.contains('\\')) throw IntegrationUnavailable("Unsupported integration path")
        return execute(method, if (path.isEmpty()) origin else URI(origin.toString().trimEnd('/') + "/" + path.trimStart('/')), headers, body, contentType, allowedStatuses)
    }
    private fun execute(method: String, uri: URI, headers: Map<String, String>, body: String?, contentType: String, allowedStatuses: Set<Int>): IntegrationResponse {
        val resolver = object : DnsResolver {
            override fun resolve(host: String) = network.resolve(host)
            override fun resolveCanonicalHostname(host: String) = host
        }
        val manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(10)).setSocketTimeout(Timeout.ofSeconds(20)).build()).build()
        try {
            HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries()
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build()).build().use { client ->
                    val request = HttpUriRequestBase(method, uri).apply {
                        headers.forEach { (key, value) -> setHeader(key, value) }
                        if (body != null) entity = StringEntity(body, ContentType.parse(contentType))
                    }
                    return client.executeOpen(null, request, null).use { response ->
                        try {
                            if (response.code !in 200..299 && response.code !in allowedStatuses) throw IntegrationUnavailable("Provider returned HTTP ${response.code}. Check the plugin credentials and permissions.")
                            val bytes = response.entity?.content?.readNBytes(1_048_577) ?: byteArrayOf()
                            if (bytes.size > 1_048_576) throw IntegrationUnavailable("Provider response exceeded the supported size")
                            IntegrationResponse(response.code, if (bytes.isEmpty()) null else runCatching { mapper.readTree(bytes) }.getOrNull(), String(bytes, Charsets.UTF_8),
                                response.getFirstHeader("ETag")?.value?.let { mapOf("ETag" to it) } ?: emptyMap())
                        } finally { request.abort() }
                    }
                }
        } catch (failure: IntegrationUnavailable) { throw failure }
          catch (_: Exception) { throw IntegrationUnavailable("Provider connection failed. Check credentials, network access, and plugin configuration.") }
    }
    internal fun validate(url: String): URI {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        val fixed = setOf("api.telegram.org", "discord.com", "graph.facebook.com", "graph.microsoft.com", "login.microsoftonline.com", "oauth2.googleapis.com", "www.googleapis.com", "api.github.com")
        if (uri == null || uri.scheme != "https" || uri.port !in listOf(-1,443) || uri.userInfo != null || uri.fragment != null ||
            (host !in fixed && !host.endsWith(".api.powerplatform.com") && !host.endsWith(".logic.azure.com")))
            throw IntegrationUnavailable("Unsupported integration endpoint")
        return uri
    }
    companion object {
        fun endpoint(value: String): URI {
            val uri = runCatching { URI(value.trim()) }.getOrNull()
            if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null ||
                uri.port !in -1..65535 || uri.port == 0 || uri.rawPath.orEmpty().split('/').any { part ->
                    val decoded = java.net.URLDecoder.decode(part.replace("+","%2B"),Charsets.UTF_8)
                    decoded in listOf(".","..") || decoded.any { it in "/\\%\r\n\u0000" }
                })
                throw com.starter.exception.BadRequestException("Use an HTTPS service URL without credentials, query parameters, or traversal")
            return uri
        }
    }
}
