package com.starter.webhook

import com.starter.exception.BadRequestException
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

@Component
class WebhookHttpClient(@Value("\${app.webhooks.allow-private-targets:false}") private val allowPrivate: Boolean) {
    fun validate(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri == null || uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null ||
            uri.scheme !in setOf("https", "http") || (uri.scheme == "http" && !allowPrivate))
            throw BadRequestException("Use an HTTPS webhook URL without credentials, query parameters, or fragments")
        return uri
    }
    internal fun resolve(host: String): Array<InetAddress> = InetAddress.getAllByName(host).also { values ->
        if (!allowPrivate && values.any { blocked(it) }) throw UnknownHostException("Private webhook targets are disabled")
    }
    internal fun blocked(ip: InetAddress): Boolean {
        val bytes = ip.address.map { it.toInt() and 255 }
        return ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress || ip.isSiteLocalAddress || ip.isMulticastAddress ||
            (bytes.size == 16 && bytes[0] and 254 == 252) ||
            (bytes.size == 4 && (bytes[0] == 0 || bytes[0] >= 224 || (bytes[0] == 100 && bytes[1] in 64..127) ||
                (bytes[0] == 198 && bytes[1] in 18..19)))
    }
    fun post(url: String, headers: Map<String, String>, body: String): Int {
        val uri = validate(url)
        // When private targets are enabled, plaintext HTTP is still restricted to private networks.
        fun target(host: String) = resolve(host).also { addresses ->
            if (uri.scheme == "http" && addresses.any { !blocked(it) }) throw UnknownHostException("Use HTTPS for public webhook destinations")
        }
        target(uri.host)
        val resolver = object : DnsResolver {
            override fun resolve(host: String) = target(host)
            override fun resolveCanonicalHostname(host: String) = host
        }
        val manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(10)).setSocketTimeout(Timeout.ofSeconds(20)).build()).build()
        HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries()
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build()).build().use { client ->
                val post = HttpPost(uri).apply { headers.forEach { (key, value) -> setHeader(key, value) }; entity = StringEntity(body, ContentType.APPLICATION_JSON) }
                // Abort after response headers: remote response bodies are neither stored nor drained without bounds.
                return client.executeOpen(null, post, null).use { response -> response.code.also { post.abort() } }
            }
    }
}
