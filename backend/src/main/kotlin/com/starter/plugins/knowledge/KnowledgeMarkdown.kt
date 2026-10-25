package com.starter.plugins.knowledge

import com.starter.exception.BadRequestException
import com.starter.projects.ReportCoverImages
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Raw HTML is escaped. Resource URLs are resolved only from this page's uploaded raster images. */
@Component
class KnowledgeMarkdown {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build()

    fun render(id: UUID, markdown: String, assets: Map<String, String>, selected: Set<UUID>? = null, diagrams: Map<String, String> = emptyMap()): KnowledgeRendered {
        if (markdown.length > 100000 || markdown.any { it.isISOControl() && it !in "\n\r\t" }) throw BadRequestException("Use up to 100,000 characters of Markdown")
        val root = parser.parse(markdown)
        root.accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                if (link.destination.startsWith("wiki:")) {
                    val reference = link.destination.removePrefix("wiki:")
                    val target = runCatching { UUID.fromString(reference.substringBefore('#')) }.getOrNull()
                        ?: throw BadRequestException("Use wiki: followed by a page ID for documentation links")
                    val fragment = reference.substringAfter('#', "").takeIf { it.isNotBlank() }?.let { "#" + slug(it) }.orEmpty()
                    link.destination = "/plugins/knowledge?page=$target$fragment"
                }
                super.visit(link)
            }
        })
        val document = Jsoup.parseBodyFragment(renderer.render(root))
        document.outputSettings().prettyPrint(false)
        val prefix = "page-$id"
        val used = mutableSetOf<String>()
        val headings = document.select("h1,h2,h3,h4,h5,h6").map { heading ->
            val base = slug(heading.text()); var name = base; var suffix = 1
            while (!used.add(name)) { suffix++; name = "$base-$suffix" }
            heading.attr("id", "$prefix-$name")
            KnowledgeHeading("$prefix-$name", heading.text(), heading.tagName().drop(1).toInt())
        }
        document.select("img").forEach { image ->
            val reference = decode(image.attr("src"))
            if (reference.contains(':') || reference.startsWith('/') || reference.contains('\\') || reference.split('/').any { it == ".." } || reference.contains('?') || reference.contains('#')) {
                throw BadRequestException("Upload images to the page and reference their filenames in Markdown")
            }
            val name = ReportCoverImages.name(reference.substringAfterLast('/'))
            image.attr("src", assets[name] ?: throw BadRequestException("Upload the page image $name before saving or previewing"))
        }
        document.select("a").forEach { link ->
            val href = link.attr("href")
            val target = INTERNAL.matchEntire(href)
            when {
                target != null -> {
                    val page = UUID.fromString(target.groupValues[1])
                    val fragment = target.groupValues[2].takeIf { it.isNotBlank() }?.let { "-" + slug(decode(it)) }.orEmpty()
                    if (selected == null) link.attr("href", "/plugins/knowledge?page=$page#page-$page$fragment")
                    else if (page in selected) link.attr("href", "#page-$page$fragment")
                    else { link.removeAttr("href"); link.attr("title", "Page not included in this export") }
                }
                href.startsWith('#') -> link.attr("href", "#$prefix-${slug(decode(href.drop(1)))}")
                !href.matches(Regex("(?i)(https?://|mailto:).+")) -> link.removeAttr("href")
                else -> link.attr("rel", "noopener noreferrer")
            }
        }
        val blocks = document.select("pre > code.language-mermaid").mapIndexed { index, code ->
            val source = code.wholeText().trim()
            if (source.isEmpty() || source.length > 20000) throw BadRequestException("Use Mermaid blocks of up to 20,000 characters")
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            val key = "$id-$index-$digest"
            val container = code.parent()!!
            container.tagName("div").empty().attr("class", "knowledge-diagram").attr("data-diagram", key)
            if (selected != null) container.appendElement("img").attr("src", diagrams[key] ?: throw BadRequestException("Render all Mermaid diagrams before exporting")).attr("alt", "Mermaid diagram")
            KnowledgeDiagram(key, source)
        }
        if (blocks.size > 20) throw BadRequestException("Use at most 20 Mermaid diagrams per document")
        return KnowledgeRendered(document.body().html(), blocks, headings)
    }

    private fun decode(value: String) = try { URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8) } catch (_: IllegalArgumentException) { throw BadRequestException("Invalid Markdown resource URL") }
    private fun slug(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "section" }.take(160)
    companion object { private val INTERNAL = Regex("/plugins/knowledge\\?page=([a-fA-F0-9-]{36})(?:#(.*))?") }
}
