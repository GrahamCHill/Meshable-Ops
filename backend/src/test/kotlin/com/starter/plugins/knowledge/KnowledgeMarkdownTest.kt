package com.starter.plugins.knowledge

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class KnowledgeMarkdownTest {
    private val markdown = KnowledgeMarkdown()
    private val id = UUID.randomUUID()
    @Test fun `Markdown tables headings and page links render with stable unique anchors`() {
        val target = UUID.randomUUID()
        val source = "# Overview\n\n## Overview\n\n| A | B |\n|---|---|\n| one | two |\n\n[Next](wiki:$target#deployment) [Top](#overview)"
        val view = markdown.render(id, source, emptyMap())
        assertThat(view.html).contains("<table>", "page-$id-overview", "page-$id-overview-2", "/plugins/knowledge?page=$target#page-$target-deployment")
        val exported = markdown.render(id, source, emptyMap(), setOf(id, target))
        assertThat(exported.html).contains("href=\"#page-$target-deployment\"", "href=\"#page-$id-overview\"")
        assertThat(markdown.render(id, source, emptyMap(), setOf(id)).html).doesNotContain("href=\"/plugins/knowledge", "href=\"#page-$target")
    }
    @Test fun `raw HTML and active links are never executable and images cannot fetch external resources`() {
        val rendered = markdown.render(id, "<script>alert(1)</script>\n\n[Bad](javascript:alert%281%29)", emptyMap())
        assertThat(rendered.html).doesNotContain("<script>", "href=\"javascript:")
        listOf("https://example.test/x.png", "file:///secret.png", "../private.png", "//example.test/x", "missing.png").forEach { image ->
            assertThatThrownBy { markdown.render(id, "![Image]($image)", emptyMap()) }.isInstanceOf(BadRequestException::class.java)
        }
        val image = "data:image/png;base64,test"
        assertThat(markdown.render(id, "![Caption](images/Chart%20one.PNG)", mapOf("chart one.png" to image)).html).contains(image, "alt=\"Caption\"")
    }
    @Test fun `Mermaid blocks have source-bound identities and cannot disappear from an export`() {
        val source = "```mermaid\nflowchart LR\n A --> B\n```"
        val view = markdown.render(id, source, emptyMap())
        val diagram = view.diagrams.single()
        assertThat(diagram.source).contains("A --> B")
        assertThat(markdown.render(id, source.replace("B", "C"), emptyMap()).diagrams.single().key).isNotEqualTo(diagram.key)
        assertThatThrownBy { markdown.render(id, source, emptyMap(), setOf(id)) }.isInstanceOf(BadRequestException::class.java)
        assertThat(markdown.render(id, source, emptyMap(), setOf(id), mapOf(diagram.key to "data:image/png;base64,test")).html).contains("alt=\"Mermaid diagram\"")
    }
    @Test fun `oversized source and malformed internal links are rejected`() {
        assertThatThrownBy { markdown.render(id, "a".repeat(100001), emptyMap()) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { markdown.render(id, "[Broken](wiki:bad-id)", emptyMap()) }.isInstanceOf(BadRequestException::class.java)
    }
}
