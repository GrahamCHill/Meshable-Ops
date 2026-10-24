package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.plugins.packages.PluginZipReader
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginZipReaderTest {
    private val mapper = jacksonObjectMapper()
    private val reader = PluginZipReader(mapper)
    private val manifest = PluginManifest("zip-notes", "Notes", "1.0.0", "Private notes", extensions = setOf(PluginExtension.PAGE),
        menuItems = listOf(PluginMenuItem("guide", "Guide")))
    private fun files() = linkedMapOf("plugin.json" to mapper.writeValueAsString(mapOf("manifest" to manifest, "entrypoint" to "index.html", "pages" to mapOf("guide" to "guide.html"))),
        "author.json" to """{"name":"","organization":"","email":"","website":""}""", "index.html" to "<h1>Notes</h1>", "guide.html" to "<h1>Guide</h1>")
    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { output -> files.forEach { (path, value) -> output.putNextEntry(ZipEntry(path)); output.write(value.toByteArray()); output.closeEntry() } }
    }.toByteArray()

    @Test fun `blank author fields and submenu pages form a valid bounded package`() {
        val parsed = reader.read(zip(files()))
        assertThat(parsed.author.name).isEmpty()
        assertThat(parsed.descriptor.manifest.id).isEqualTo("zip-notes")
        assertThat(parsed.files.keys).containsExactly("index.html", "guide.html")
        assertThat(parsed.sha256).hasSize(64)
    }
    @Test fun `author file and every author field are mandatory even when anonymous`() {
        val absent = files().also { it.remove("author.json") }
        assertThatThrownBy { reader.read(zip(absent)) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("author.json")
        listOf("{}", """{"name":"","organization":"","email":"","website":null}""", """{"name":"","organization":"","email":"","website":"","verified":true}""").forEach { author ->
            assertThatThrownBy { reader.read(zip(files().also { it["author.json"] = author })) }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `traversal absolute paths duplicate names and raw SQL are rejected`() {
        listOf("../outside.html", "/outside.html", "folder/../outside.html", "folder\\outside.html", "migration.sql", "INDEX.HTML").forEach { path ->
            assertThatThrownBy { reader.read(zip(files() + (path to "payload"))) }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `zip bombs too many entries and oversized archives are rejected`() {
        assertThatThrownBy { reader.read(zip(files().also { it["index.html"] = "a".repeat(PluginZipReader.MAX_FILE_BYTES + 1) })) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("Expanded ZIP")
        assertThatThrownBy { reader.read(zip(files() + (1..200).associate { "page$it.html" to "x" })) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("Too many")
        assertThatThrownBy { reader.read(ByteArray(PluginZipReader.MAX_ARCHIVE_BYTES + 1)) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `directory entries cannot hide an unbounded compressed payload`() {
        reader.read(zip(files() + ("pages/" to "")))
        assertThatThrownBy { reader.read(zip(files() + ("pages/" to "a".repeat(100000)))) }.isInstanceOf(BadRequestException::class.java).hasMessageContaining("Directory entries")
    }
    @Test fun `unknown API versions server capabilities undeclared pages and foreign tables are rejected`() {
        listOf(manifest.copy(apiVersion = 2), manifest.copy(extensions = setOf(PluginExtension.PAGE, PluginExtension.API)),
            manifest.copy(capabilities = mapOf("notifications.sms" to "SMS")),
            manifest.copy(tables = listOf(PluginTable("users", "Users")), extensions = setOf(PluginExtension.PAGE, PluginExtension.DATABASE))).forEach { invalid ->
            assertThatThrownBy { reader.read(zip(files().also { it["plugin.json"] = mapper.writeValueAsString(mapOf("manifest" to invalid, "pages" to mapOf("guide" to "guide.html"))) })) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { reader.read(zip(files().also { it.remove("guide.html") })) }.isInstanceOf(BadRequestException::class.java)
    }
}
