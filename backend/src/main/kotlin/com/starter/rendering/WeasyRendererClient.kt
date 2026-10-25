package com.starter.rendering

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.BadRequestException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import java.net.URI

class DocumentRendererUnavailable : RuntimeException("Document rendering is unavailable. Check the internal WeasyCanvas renderer and its server configuration.")
@Component
class WeasyRendererClient(@Value("\${app.documents.renderer-url:http://weasy-renderer:8000}") private val url: String,
    @Value("\${app.documents.renderer-token:}") private val token: String) {
    private val http = RestTemplate(object : SimpleClientHttpRequestFactory() {
        init { setConnectTimeout(5000); setReadTimeout(55000) }
        override fun prepareConnection(connection: java.net.HttpURLConnection, httpMethod: String) { super.prepareConnection(connection, httpMethod); connection.instanceFollowRedirects = false }
    })
    fun validate(template: JsonNode) { request("/plugins/documents/validate", mapOf("template" to template), String::class.java) }
    fun render(template: JsonNode, values: Map<String, String>): ByteArray = request("/plugins/documents/render", mapOf("template" to template, "values" to values), ByteArray::class.java)
    fun report(value: Any): ByteArray = request("/reports/render", mapOf("report" to value), ByteArray::class.java)
    fun validateReportCover(html: String) {request("/reports/cover/validate",mapOf("cover" to mapOf("html" to html)),String::class.java)}
    fun reportCover(value: Any): ByteArray = request("/reports/cover/preview",mapOf("cover" to value),ByteArray::class.java)
    fun billing(value: Any): ByteArray = request("/plugins/billing/render", mapOf("billing" to value), ByteArray::class.java)
    fun analytics(value: Any): ByteArray = request("/plugins/analytics/render", mapOf("analytics" to value), ByteArray::class.java)
    fun knowledge(value: Any): ByteArray = request("/plugins/knowledge/render", mapOf("knowledge" to value), ByteArray::class.java)
    private fun <T> request(path: String, body: Any, type: Class<T>): T {
        try {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON; if (token.isNotBlank()) setBearerAuth(token) }
            val response = http.exchange(URI(url.trimEnd('/') + path), HttpMethod.POST, HttpEntity(body, headers), type)
            return response.body ?: throw DocumentRendererUnavailable()
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode.value() in setOf(400, 413, 422)) throw BadRequestException("The template or document data cannot be rendered. Check its sections, required fields, images, and text limits.")
            throw DocumentRendererUnavailable()
        } catch (ex: BadRequestException) { throw ex }
          catch (_: Exception) { throw DocumentRendererUnavailable() }
    }
}
