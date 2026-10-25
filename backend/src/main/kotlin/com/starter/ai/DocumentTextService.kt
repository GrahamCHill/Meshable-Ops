package com.starter.ai

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.Base64

data class ExtractedPage(val pageNumber: Int?, val text: String, val method: String = "TEXT", val confidence: Double? = null)
data class DocumentChunk(val text: String, val pageNumber: Int? = null)
data class ExtractedDocumentText(val text: String, val truncated: Boolean, val pages: List<ExtractedPage> = emptyList(), val engine: String = "text-v1", val languages: String = "")

@Service
class DocumentTextService(@Qualifier("documentRestTemplate") private val http: RestTemplate, @Value("\${ml.base-url:http://ml-backend:8000}") private val mlBaseUrl: String,
    @Value("\${app.documents.max-bytes:52428800}") val maxBytes: Int = 52428800) {
    fun supports(filename: String) = filename.substringAfterLast('.', "").lowercase() in setOf("pdf", "docx", "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "yaml", "yml", "rst", "png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp")
    fun extract(filename: String, bytes: ByteArray): ExtractedDocumentText {
        try {
            val response = http.postForObject("$mlBaseUrl/api/v1/ml/extract", mapOf("filename" to filename, "content" to Base64.getEncoder().encodeToString(bytes)), JsonNode::class.java)
                ?: throw AiUnavailableException("No document text was returned")
            val pages = response.path("pages").map {
                ExtractedPage(it.path("pageNumber").takeIf { n -> n.isInt && n.asInt() > 0 }?.asInt(),
                    it.path("text").asText(), it.path("method").asText("TEXT"),
                    it.path("confidence").takeIf { n -> n.isNumber }?.asDouble())
            }
            return ExtractedDocumentText(response.path("text").asText(), response.path("truncated").asBoolean(), pages,
                response.path("engine").asText("text-v1"), response.path("languages").asText())
        } catch (_: RestClientException) {
            throw AiUnavailableException("Document text could not be extracted")
        }
    }
}

internal fun documentChunks(text: String, size: Int = 1600, overlap: Int = 200, maxChunks: Int = 4096): List<String> {
    require(size > overlap && overlap >= 0 && maxChunks > 0)
    var start = 0
    val chunks = mutableListOf<String>()
    while (start < text.length && chunks.size < maxChunks) {
        val end = minOf(start + size, text.length)
        text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
        if (end == text.length) break
        start = end - overlap
    }
    return chunks
}

internal fun pageChunks(extracted: ExtractedDocumentText, maxChunks: Int = 4096): Pair<List<DocumentChunk>, Boolean> {
    val pages = extracted.pages.ifEmpty { listOf(ExtractedPage(null, extracted.text)) }
    val result = mutableListOf<DocumentChunk>()
    var truncated = extracted.truncated
    pages.forEach { page ->
        val chunks = documentChunks(page.text, maxChunks = maxChunks + 1)
        val available = maxChunks - result.size
        truncated = truncated || chunks.size > available
        result += chunks.take(available).map { DocumentChunk(it, page.pageNumber) }
    }
    return result to truncated
}
