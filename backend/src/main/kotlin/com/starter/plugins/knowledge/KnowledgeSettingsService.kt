package com.starter.plugins.knowledge

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.projects.ProjectReportCoverService
import com.starter.projects.ReportCoverImages
import com.starter.rendering.WeasyRendererClient
import com.starter.repository.postgres.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KnowledgeSettingsService(private val rows: KnowledgeSettingsRepository, private val access: KnowledgeAccess,
    private val users: UserRepository, private val mapper: ObjectMapper, private val renderer: WeasyRendererClient,
    private val reportCovers: ProjectReportCoverService, private val jdbc: JdbcTemplate) {
    fun view(email: String): KnowledgeSettingsView {
        access.admin(email)
        val row = rows.findById(1).orElse(null)
        val exporters = access.exporters(row)
        val accounts = users.findAll()
        val members = (accounts.filter { it.active || it.id in exporters }.map { KnowledgeMember(it.id, it.name?.takeIf(String::isNotBlank) ?: it.username, it.email, it.active) } +
            exporters.filter { id -> accounts.none { it.id == id } }.map { KnowledgeMember(it, "Unavailable account", it.toString(), false) }).sortedBy { it.name.lowercase() }
        return KnowledgeSettingsView(access.exporters(row), row?.coverHtml?.ifBlank { DEFAULT_HTML } ?: DEFAULT_HTML, assets(row), row?.revision ?: -1, DEFAULT_HTML, members)
    }
    @Transactional
    fun save(email: String, input: KnowledgeSettingsInput): KnowledgeSettingsView {
        access.admin(email)
        val images = ReportCoverImages.normalize(input.coverAssets, assets(rows.findById(1).orElse(null)))
        validate(input.coverHtml)
        renderer.reportCover(cover(input.coverHtml, images, "Knowledge base", 3))
        access.admin(email)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('knowledge-export-settings'))", RowCallbackHandler {})
        val old = rows.findById(1).orElse(null)
        if ((old?.revision ?: -1) != input.revision) throw ConflictException("Export settings changed. Reload before saving.")
        val row = old ?: KnowledgeSettings()
        row.coverHtml = input.coverHtml; row.coverAssetsJson = mapper.writeValueAsString(images)
        rows.saveAndFlush(row)
        return view(email)
    }
    @Transactional
    fun permissions(email: String, input: KnowledgePermissionsInput): KnowledgeSettingsView {
        access.admin(email)
        if (input.exporterIds.size > 500) throw BadRequestException("Choose up to 500 documentation exporters")
        input.exporterIds.forEach { if (users.findById(it).orElse(null)?.active != true) throw BadRequestException("Select active accounts for documentation export") }
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('knowledge-export-settings'))", RowCallbackHandler {})
        val old = rows.findById(1).orElse(null)
        if ((old?.revision ?: -1) != input.revision) throw ConflictException("Export settings changed. Reload before saving.")
        val row = old ?: KnowledgeSettings()
        row.exporterIdsJson = mapper.writeValueAsString(input.exporterIds)
        rows.saveAndFlush(row)
        return view(email)
    }
    fun preview(email: String, input: KnowledgeCoverPreview): ByteArray {
        access.admin(email); validate(input.coverHtml)
        val result = renderer.reportCover(cover(input.coverHtml, ReportCoverImages.normalize(input.coverAssets), "Knowledge base", 3))
        access.admin(email)
        return KnowledgePdf.finish(result, "Knowledge base cover")
    }
    fun cover(row: KnowledgeSettings, title: String, count: Int) = cover(row.coverHtml.ifBlank { DEFAULT_HTML }, assets(row), title, count)
    private fun cover(html: String, assets: Map<String, String>, title: String, count: Int): Map<String, Any> {
        val branding = reportCovers.context()
        return mapOf("html" to html, "assets" to assets, "title" to title, "documentCount" to count.toString(), "organization" to branding.siteName,
            "siteName" to branding.siteName, "companyName" to branding.companyName, "siteLogo" to branding.siteLogo)
    }
    private fun assets(row: KnowledgeSettings?): Map<String, String> = row?.let { mapper.readValue(it.coverAssetsJson, object : TypeReference<Map<String, String>>() {}) } ?: emptyMap()
    private fun validate(html: String) { if (html.isBlank() || html.length > 30000) throw BadRequestException("Use a cover HTML design of up to 30,000 characters") }
    companion object {
        val DEFAULT_HTML = """<!doctype html><html><head><meta charset="utf-8"><style>
            @page {size:A4;margin:0} body {margin:0;font-family:Liberation Sans,sans-serif;color:#183047}
            .cover {height:297mm;box-sizing:border-box;padding:30mm 22mm;background:#f3f7fa;border-top:10mm solid #157f82}
            .brand {font-size:12pt;color:#157f82;letter-spacing:2px;margin-bottom:38mm}.logo {max-width:48mm;max-height:18mm}
            h1 {font-size:34pt;line-height:1.18;margin:0 0 12mm;overflow-wrap:anywhere}.subtitle {font-size:16pt}
            .rule {height:2mm;width:24mm;background:#157f82;margin:22mm 0 10mm}.foot {font-size:10pt;color:#546c7d}
            </style></head><body><main class="cover"><div class="brand"><img class="logo" src="{{siteLogo}}" alt="Workspace logo"><p>{{siteName}}</p><p>{{companyName}}</p></div>
            <h1>{{title}}</h1><p class="subtitle">Knowledge base</p><div class="rule"></div><p class="foot">{{documentCount}} selected documents<br>Generated {{generatedAt}}</p></main></body></html>""".trimIndent()
    }
}
