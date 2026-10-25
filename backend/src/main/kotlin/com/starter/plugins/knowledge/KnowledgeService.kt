package com.starter.plugins.knowledge

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.projects.ReportCoverImages
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class KnowledgeService(private val pages: KnowledgePageRepository, private val access: KnowledgeAccess,
    private val markdown: KnowledgeMarkdown, private val mapper: ObjectMapper, private val jdbc: JdbcTemplate) {
    fun list(email: String): List<KnowledgePageSummary> { access.user(email); return pages.summaries() }
    fun page(email: String, id: UUID): KnowledgePageView { access.user(email); return view(find(id)) }
    fun preview(email: String, id: UUID, input: KnowledgePageInput): KnowledgeRendered {
        access.user(email, true)
        return markdown.render(id, input.markdown, ReportCoverImages.normalize(input.assets ?: emptyMap()))
    }
    @Transactional
    fun save(email: String, id: UUID?, input: KnowledgePageInput): KnowledgePageView {
        access.user(email, true)
        if (input.title.isBlank() || input.title.length > 180 || input.title.any(Char::isISOControl)) throw BadRequestException("Enter a document title of up to 180 characters")
        // Serialize hierarchy changes so concurrent moves cannot create cycles or orphaned parents.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('knowledge-page-tree'))", RowCallbackHandler {})
        if (id == null && pages.count() >= 2000) throw BadRequestException("This knowledge base supports up to 2,000 documents")
        val row = id?.let(::find) ?: KnowledgePage()
        if ((if (id == null) -1 else row.revision) != input.revision) throw ConflictException("This document changed. Reload it before saving.")
        var parent = input.parentId; val visited = mutableSetOf(row.id)
        while (parent != null) {
            if (!visited.add(parent) || visited.size > 50) throw BadRequestException("Choose a parent outside this document's descendants, with at most 50 levels")
            parent = find(parent).parentId
        }
        val images = input.assets?.let { ReportCoverImages.normalize(it, assets(row)) } ?: assets(row)
        markdown.render(row.id, input.markdown, images)
        row.title = input.title.trim(); row.parentId = input.parentId; row.markdown = input.markdown
        row.assetsJson = mapper.writeValueAsString(images); row.updatedAt = Instant.now()
        return view(pages.saveAndFlush(row))
    }
    @Transactional
    fun delete(email: String, id: UUID, revision: Long) {
        access.user(email, true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('knowledge-page-tree'))", RowCallbackHandler {})
        val row = find(id)
        if (row.revision != revision) throw ConflictException("This document changed. Reload it before deleting.")
        if (pages.existsByParentId(id)) throw ConflictException("Move or remove child documents before deleting their parent")
        pages.delete(row); pages.flush()
    }
    fun find(id: UUID): KnowledgePage = pages.findById(id).orElse(null) ?: throw NotFoundException("Documentation page unavailable")
    fun assets(page: KnowledgePage): Map<String, String> = mapper.readValue(page.assetsJson, object : TypeReference<Map<String, String>>() {})
    private fun view(page: KnowledgePage) = KnowledgePageView(page.id, page.title, page.parentId, page.markdown, assets(page), page.revision, markdown.render(page.id, page.markdown, assets(page)))
}
