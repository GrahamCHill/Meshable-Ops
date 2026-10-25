package com.starter.plugins.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.projects.ReportCoverImages
import com.starter.rendering.WeasyRendererClient
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class KnowledgeExportService(private val access: KnowledgeAccess, private val pages: KnowledgeService,
    private val markdown: KnowledgeMarkdown, private val covers: KnowledgeSettingsService, private val renderer: WeasyRendererClient,
    private val mapper: ObjectMapper, private val downloads: DocumentAccessService, private val pdfs: PdfService) {
    fun prepare(email: String, input: KnowledgeExportInput): List<KnowledgePreparedPage> {
        access.requireExport(email); requireDownloads()
        return selection(input).map { page ->
            KnowledgePreparedPage(page.id, page.title, page.revision, markdown.render(page.id, page.markdown, pages.assets(page)).diagrams)
        }
    }

    // Deliberately outside a transaction: authorization and revisions are fetched again after worker I/O.
    fun export(email: String, input: KnowledgeExportInput): ByteArray {
        val settings = access.requireExport(email)
        val policy = requireDownloads()
        val selected = selection(input)
        val ids = selected.map { it.id }.toSet()
        val expected = selected.flatMap { markdown.render(it.id, it.markdown, pages.assets(it)).diagrams }.map { it.key }.toSet()
        if (expected.size > 32 || input.diagrams.keys != expected || input.diagrams.values.sumOf { it.length.toLong() } > 2_800_000) {
            throw BadRequestException("Supply exactly the selected pages' Mermaid diagrams, up to 32 diagrams and 2 MiB")
        }
        val images = input.diagrams.mapValues { (_, value) ->
            if (value.length > 350000 || !value.startsWith("data:image/png;base64,")) throw BadRequestException("Export diagrams as bounded PNG images")
            val bytes = try { Base64.getDecoder().decode(value.substringAfter(',')) } catch (_: IllegalArgumentException) { throw BadRequestException("Invalid diagram image") }
            if (bytes.size > 262500) throw BadRequestException("Optimise each diagram to at most 256 KiB")
            ReportCoverImages.encode(bytes)
        }
        val content = selected.map { page ->
            mapOf("id" to page.id.toString(), "title" to page.title, "html" to markdown.render(page.id, page.markdown, pages.assets(page), ids, images).html)
        }
        val book = mapOf("title" to input.title.trim(), "cover" to covers.cover(settings, input.title.trim(), selected.size), "pages" to content)
        if (mapper.writeValueAsBytes(book).size > 3_800_000) throw BadRequestException("Select fewer documents or smaller images for an export under 3.6 MiB of source data")
        val bytes = KnowledgePdf.finish(renderer.knowledge(book), input.title.trim())
        val result = if (policy.downloadMode == DownloadMode.WATERMARKED) pdfs.watermark(bytes, policy.watermarkText, email) else bytes
        val live = access.requireExport(email)
        if (live.revision != settings.revision || downloads.current() != policy) throw ForbiddenException("Export permissions or document policy changed. Start the export again.")
        selection(input) // Suppress stale/deleted document snapshots, including image and hierarchy changes.
        return result
    }

    private fun selection(input: KnowledgeExportInput): List<KnowledgePage> {
        if (input.title.isBlank() || input.title.length > 180 || input.title.any(Char::isISOControl)) throw BadRequestException("Enter a book title of up to 180 characters")
        if (input.pages.isEmpty() || input.pages.size > 50 || input.pages.map { it.id }.distinct().size != input.pages.size) throw BadRequestException("Select 1-50 different documents in export order")
        return input.pages.map { reference ->
            pages.find(reference.id).also { if (it.revision != reference.revision) throw ConflictException("A selected document changed. Refresh the selection before exporting.") }
        }
    }
    private fun requireDownloads() = downloads.current().also {
        if (it.downloadMode == DownloadMode.DISABLED) throw ForbiddenException("PDF downloads are disabled by document policy")
    }
}
