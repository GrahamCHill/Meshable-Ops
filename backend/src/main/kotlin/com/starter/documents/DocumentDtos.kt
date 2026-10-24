package com.starter.documents

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.starter.security.PasswordBytes
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

enum class DataFieldType { TEXT, MULTILINE, NUMBER, MONEY, DATE, EMAIL, DERIVED }
enum class Calculation { ADD, SUBTRACT, MULTIPLY, DIVIDE, PERCENT }
data class DataFieldRule(@field:Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,119}") val key: String,
    val type: DataFieldType = DataFieldType.TEXT, val operation: Calculation? = null,
    @field:Size(max = 2) val inputs: List<@Size(max = 120) String> = emptyList())
data class TemplateRequest(@field:NotBlank @field:Size(max = 100) val name: String,
    @field:Pattern(regexp = "QUOTE|INVOICE|OTHER") val category: String = "OTHER", val published: Boolean = false,
    val template: JsonNode, @field:Valid @field:Size(max = 50) val fieldRules: List<DataFieldRule> = emptyList(),
    @field:Min(0) val revision: Long = 0)
data class TemplateView(val id: UUID, val name: String, val category: String, val published: Boolean,
    val revision: Long, val updatedAt: Instant, val template: JsonNode, val fieldRules: List<DataFieldRule>)
data class GenerateDocumentRequest(val templateId: UUID, @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Size(max = 50) val values: Map<String, JsonNode> = emptyMap()) {
    override fun toString() = "GenerateDocumentRequest(templateId=$templateId)"
}
data class GeneratedDocumentView(val id: UUID, val ownerId: UUID, val templateId: UUID, val templateRevision: Long,
    val templateName: String, val name: String, val filename: String, val pageCount: Int, val byteSize: Int, val createdAt: Instant)
enum class DocumentEmailProtection { PLAIN, PASSWORD }
data class EmailDocumentRequest(@field:NotBlank @field:Email @field:Size(max = 254) val recipient: String,
    @field:NotBlank @field:Size(max = 200) val subject: String, @field:NotBlank @field:Size(max = 20000) val text: String,
    val protection: DocumentEmailProtection = DocumentEmailProtection.PLAIN,
    @field:PasswordBytes @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val password: String = "") {
    override fun toString() = "EmailDocumentRequest(protection=$protection)"
}
