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
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.util.Timeout
import org.springframework.stereotype.Component
import java.net.URI
import org.springframework.beans.factory.annotation.Value

class IntegrationUnavailable(message: String) : RuntimeException(message)
data class IntegrationResponse(val status: Int, val body: JsonNode?, val text: String = "", val headers: Map<String,String> = emptyMap()) {
    override fun toString() = "IntegrationResponse(status=$status)"
}
data class IntegrationBytesResponse(val status: Int, val bytes: ByteArray, val headers: Map<String, String> = emptyMap()) {
    override fun toString() = "IntegrationBytesResponse(status=$status)"
}

/** No redirects/retries, bounded bodies, public DNS targets, and no credential URLs in errors. */
@Component
class IntegrationHttpClient(private val mapper: ObjectMapper, @Value("\${app.plugins.allow-private-targets:false}") private val allowPrivate: Boolean = false) {
    private val network = WebhookHttpClient(allowPrivate)
    fun request(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null,
        contentType: String = "application/json"): IntegrationResponse = execute(method, validate(url), headers, body, contentType, emptySet())

    fun binary(method: String, url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null,
        contentType: String = "application/pdf", allowedStatuses: Set<Int> = emptySet(), maxBytes: Int = 1_048_576): IntegrationBytesResponse =
        executeBytes(method, validate(url), headers, body, contentType, allowedStatuses, maxBytes)

    /** Only a Graph-returned preauthenticated Microsoft content URL, with no bearer token or redirects. */
    fun microsoftDownload(url: String, maxBytes: Int): ByteArray {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        if (uri == null || uri.scheme != "https" || uri.port !in listOf(-1,443) || uri.userInfo != null || uri.fragment != null ||
            !(host.endsWith(".sharepoint.com") || host.endsWith(".sharepoint-df.com") || host.endsWith(".1drv.com")))
            throw IntegrationUnavailable("Microsoft returned an unsupported download location")
        return executeBytes("GET", uri, emptyMap(), null, "application/pdf", emptySet(), maxBytes).bytes
    }

    /** The base is administrator configuration, never a URL taken from provider responses or API callers. */
    fun scoped(method: String, base: String, path: String = "", headers: Map<String, String> = emptyMap(), body: String? = null,
        contentType: String = "application/json", allowedStatuses: Set<Int> = emptySet()): IntegrationResponse {
        val origin = endpoint(base)
        if (path.startsWith("//") || path.contains(Regex("(^|/)\\.\\.(/|$)")) || path.contains('\\')) throw IntegrationUnavailable("Unsupported integration path")
        return execute(method, if (path.isEmpty()) origin else URI(origin.toString().trimEnd('/') + "/" + path.trimStart('/')), headers, body, contentType, allowedStatuses)
    }
    private fun execute(method: String, uri: URI, headers: Map<String, String>, body: String?, contentType: String, allowedStatuses: Set<Int>): IntegrationResponse {
        val response = executeBytes(method, uri, headers, body?.toByteArray(Charsets.UTF_8), contentType, allowedStatuses, 1_048_576)
        return IntegrationResponse(response.status, if (response.bytes.isEmpty()) null else runCatching { mapper.readTree(response.bytes) }.getOrNull(),
            String(response.bytes, Charsets.UTF_8), response.headers)
    }
    private fun executeBytes(method: String, uri: URI, headers: Map<String, String>, body: ByteArray?, contentType: String,
        allowedStatuses: Set<Int>, maxBytes: Int): IntegrationBytesResponse {
        require(maxBytes in 1..com.starter.service.PdfService.MAX_BYTES)
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
                        if (body != null) entity = ByteArrayEntity(body, ContentType.parse(contentType))
                    }
                    return client.executeOpen(null, request, null).use { response ->
                        try {
                            if (response.code !in 200..299 && response.code !in allowedStatuses) throw IntegrationUnavailable("Provider returned HTTP ${response.code}. Check the plugin credentials and permissions.")
                            val bytes = response.entity?.content?.readNBytes(maxBytes + 1) ?: byteArrayOf()
                            if (bytes.size > maxBytes) throw IntegrationUnavailable("Provider response exceeded the supported size")
                            IntegrationBytesResponse(response.code, bytes, listOf("ETag", "Location").mapNotNull { key ->
                                response.getFirstHeader(key)?.value?.let { key to it }
                            }.toMap())
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
