package com.starter.documents

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DocumentTemplateService(private val templates: DocumentTemplateRepository, private val access: DocumentAccess,
    private val fields: DocumentFields, private val renderer: WeasyRendererClient, private val mapper: ObjectMapper) {
    fun list(email: String, admin: Boolean = false): List<TemplateView> {
        if (admin) access.admin(email) else access.actor(email)
        return (if (admin) templates.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 100)) else templates.findByPublishedTrueOrderByUpdatedAtDesc(PageRequest.of(0, 100))).map(::view)
    }
    fun get(email: String, id: UUID): DocumentTemplateRecord {
        val actor = access.actor(email)
        return templates.visibleById(id, actor.role == "ADMIN").orElseThrow { NotFoundException("Template unavailable") }
    }
    fun rules(saved: DocumentTemplateRecord): List<DataFieldRule> = mapper.readValue(saved.fieldRulesJson, object : TypeReference<List<DataFieldRule>>() {})
    fun save(email: String, id: UUID?, request: TemplateRequest): TemplateView {
        val actor = access.admin(email)
        if (request.template.toString().toByteArray().size > 1_900_000) throw BadRequestException("Templates must be smaller than 1.9 MB including embedded images")
        fields.validate(request.template, request.fieldRules)
        if (request.template.path("signatures").any { it.path("required").asBoolean(false) }) throw BadRequestException("Required electronic signatures are not supported in this first document-generation version. Use optional signing lines.")
        // Validate before opening a write transaction or persisting anything.
        renderer.validate(request.template)
        access.admin(email)
        val saved = id?.let { templates.findById(it).orElseThrow { NotFoundException("Template unavailable") } } ?: DocumentTemplateRecord(createdBy = actor.id)
        if (id != null && saved.revision != request.revision) throw ConflictException("The template changed. Reload before saving.")
        saved.name = request.name.trim(); saved.category = request.category; saved.published = request.published
        saved.templateJson = mapper.writeValueAsString(request.template); saved.fieldRulesJson = mapper.writeValueAsString(request.fieldRules); saved.updatedAt = Instant.now()
        return view(templates.saveAndFlush(saved))
    }
    fun view(saved: DocumentTemplateRecord) = TemplateView(saved.id, saved.name, saved.category, saved.published, saved.revision, saved.updatedAt, mapper.readTree(saved.templateJson), rules(saved))
}
