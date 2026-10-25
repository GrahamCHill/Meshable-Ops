package com.starter.ai

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.Base64

data class ExtractedPage(val pageNumber: Int?, val text: String, val method: String = "TEXT", val confidence: Double? = null)
data class DocumentChunk(val text: String, val pageNumber: Int? = null)
data class ExtractedDocumentText(val text: String, val truncated: Boolean, val pages: List<ExtractedPage> = emptyList(),
    val engine: String = "text-v1", val languages: String = "", val ocrRequired: Boolean = false)

@Service
class DocumentTextService(
    @Qualifier("documentRestTemplate") private val http: RestTemplate,
    @Value("\${ml.base-url:http://ml-backend:8000}") private val mlBaseUrl: String,
    @Value("\${app.documents.max-bytes:52428800}") val maxBytes: Int = 52428800,
    @Value("\${app.documents.ocr-url:}") private val ocrUrl: String = "",
    @Value("\${app.documents.ocr-token:}") private val ocrToken: String = "",
) {
    private val images = setOf("png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp")
    init { require(maxBytes in 1..104857600) }
    fun supports(filename: String) = filename.substringAfterLast('.', "").lowercase() in
        (images + setOf("pdf", "docx", "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "yaml", "yml", "rst"))

    fun extract(filename: String, bytes: ByteArray): ExtractedDocumentText {
        if (bytes.size > maxBytes) throw AiUnavailableException("Document exceeds the configured extraction limit")
        val body = mapOf("filename" to filename, "content" to Base64.getEncoder().encodeToString(bytes))
        val image = filename.substringAfterLast('.', "").lowercase() in images
        val native = if (image) ExtractedDocumentText("", false, ocrRequired = true) else try {
            read(post("$mlBaseUrl/api/v1/ml/extract", HttpEntity(body)))
        } catch (error: AiUnavailableException) {
            if (ocrUrl.isBlank()) throw error
            ExtractedDocumentText("", false, ocrRequired = true)
        }
        if (!native.ocrRequired || ocrUrl.isBlank()) return native
        val headers = HttpHeaders()
        if (ocrToken.isNotBlank()) headers.setBearerAuth(ocrToken)
        val result = read(post(ocrUrl, HttpEntity(body, headers)), ocr = true)
        // An adapter must return all pages, including text layers in mixed PDFs.
        if (result.pages.isEmpty()) throw AiUnavailableException("The OCR service returned no page results")
        return result
    }

    private fun post(url: String, entity: HttpEntity<*>): JsonNode = try {
        http.postForObject(url, entity, JsonNode::class.java)
            ?: throw AiUnavailableException("No document text was returned")
    } catch (_: RestClientException) { throw AiUnavailableException("Document extraction service is unavailable") }

    private fun read(response: JsonNode, ocr: Boolean = false): ExtractedDocumentText {
        val rawPages = response.path("pages")
        if ((!rawPages.isMissingNode && !rawPages.isArray) || rawPages.size() > 1000)
            throw AiUnavailableException("The extraction service returned invalid pages")
        var remaining = 2_000_000
        var truncated = response.path("truncated").asBoolean()
        val seen = mutableSetOf<Int>()
        val pages = rawPages.map {
            val number = it.path("pageNumber").takeIf { n -> !n.isMissingNode && !n.isNull }?.let { n ->
                if (!n.isInt || n.asInt() !in 1..100000 || !seen.add(n.asInt()))
                    throw AiUnavailableException("The extraction service returned invalid page numbers")
                n.asInt()
            }
            val original = it.path("text").asText()
            truncated = truncated || original.length > remaining
            val text = original.take(remaining)
            remaining = maxOf(0, remaining - text.length - 1)
            val confidence = it.path("confidence").takeIf { n -> n.isNumber }?.asDouble()
            if (confidence != null && (!confidence.isFinite() || confidence !in 0.0..100.0))
                throw AiUnavailableException("The OCR service returned invalid confidence")
            ExtractedPage(number, text, it.path("method").asText(if (ocr) "OCR" else "TEXT").take(32), confidence)
        }
        if (ocr && pages.any { it.pageNumber == null }) throw AiUnavailableException("OCR pages require source page numbers")
        val text = if (pages.isEmpty()) response.path("text").asText() else pages.joinToString("\n") { it.text }
        return ExtractedDocumentText(text.take(2_000_000), truncated || text.length > 2_000_000, pages,
            response.path("engine").asText(if (ocr) "external-ocr" else "text-v2").take(128),
            response.path("languages").asText().take(128), response.path("needsOcr").asBoolean())
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
