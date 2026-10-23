package com.starter.ai

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.Base64

data class ExtractedDocumentText(val text: String, val truncated: Boolean)

@Service
class DocumentTextService(@Qualifier("aiRestTemplate") private val http: RestTemplate, @Value("\${ml.base-url:http://ml-backend:8000}") private val mlBaseUrl: String) {
    fun supports(filename: String) = filename.substringAfterLast('.', "").lowercase() in setOf("pdf", "docx", "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "yaml", "yml", "rst")
    fun extract(filename: String, bytes: ByteArray): ExtractedDocumentText {
        try {
            val response = http.postForObject("$mlBaseUrl/api/v1/ml/extract", mapOf("filename" to filename, "content" to Base64.getEncoder().encodeToString(bytes)), JsonNode::class.java)
                ?: throw AiUnavailableException("No document text was returned")
            return ExtractedDocumentText(response.path("text").asText().take(120000), response.path("truncated").asBoolean())
        } catch (_: RestClientException) {
            throw AiUnavailableException("Document text could not be extracted")
        }
    }
}

internal fun documentChunks(text: String, size: Int = 1600, overlap: Int = 200): List<String> {
    require(size > overlap && overlap >= 0)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length && chunks.size < 32) {
        val end = (start + size).coerceAtMost(text.length)
        text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
        if (end == text.length) break
        start = end - overlap
    }
    return chunks
}
