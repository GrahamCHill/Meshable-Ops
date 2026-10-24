package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.plugins.packages.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginBackendPackageTest {
    private val json = jacksonObjectMapper()
    private val reader = PluginZipReader(json)
    private val base = PluginPackageDescriptor(PluginManifest("zip-code", "Code", "1.0.0", "Uploaded code",
        runtimes = setOf(PluginRuntime.PYTHON), extensions = setOf(PluginExtension.API)), backend = PluginBackend(
        mapOf("python" to PluginBackendModule(BackendRuntime.PYTHON, "backend/app.py")),
        listOf(PluginBackendRoute("/calculate", "python"))))
    private fun read(descriptor: PluginPackageDescriptor = base, extra: Map<String, ByteArray> = emptyMap()): ParsedPluginPackage {
        val files = mapOf("plugin.json" to json.writeValueAsBytes(descriptor), "author.json" to """{"name":"","organization":"","email":"","website":""}""".toByteArray(),
            "backend/app.py" to "from meshable import serve; serve(lambda req, host: {'ok': True})".toByteArray()) + extra
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry() } }
        return reader.read(bytes.toByteArray())
    }
    @Test fun `backend-only archives need no HTML or React and retain Python code`() {
        val parsed = read()
        assertThat(parsed.files.keys).containsExactly("backend/app.py")
        assertThat(parsed.descriptor.manifest.extensions).doesNotContain(PluginExtension.PAGE)
    }
    @Test fun `the same package can contain React frontend Python and executable Kotlin artifacts`() {
        val backend = base.backend!!
        val descriptor = base.copy(manifest = base.manifest.copy(extensions = setOf(PluginExtension.PAGE, PluginExtension.API), runtimes = PluginRuntime.entries.toSet()),
            entrypoint = "frontend/index.html", backend = backend.copy(modules = backend.modules + ("kotlin" to PluginBackendModule(BackendRuntime.KOTLIN, "backend/code.jar")),
                routes = backend.routes + PluginBackendRoute("/summary", "kotlin", methods = setOf("GET"))))
        val parsed = read(descriptor, mapOf("frontend/index.html" to "<div id='root'></div><script src='app.js'></script><link rel='stylesheet' href='app.css'>".toByteArray(),
            "frontend/app.js" to "document.body.dataset.ready = 'yes'".toByteArray(), "frontend/app.css" to "body {color:blue}".toByteArray(), "backend/code.jar" to byteArrayOf(80,75,0,-1)))
        assertThat(java.util.Base64.getDecoder().decode(parsed.files["backend/code.jar"])).containsExactly(80,75,0,-1)
        val html = PluginPageAssets.render(descriptor.entrypoint, parsed.files)
        assertThat(html).contains("dataset.ready", "color:blue").doesNotContain("src=\"app.js\"", "href=\"app.css\"")
    }
    @Test fun `missing backend code invalid modules paths permissions and undeclared origins fail at install`() {
        val backend = base.backend!!
        listOf(
            base.copy(backend = null),
            base.copy(backend = backend.copy(modules = mapOf("python" to PluginBackendModule(BackendRuntime.PYTHON, "backend/missing.py")))),
            base.copy(backend = backend.copy(routes = listOf(PluginBackendRoute("/calculate", "missing")))),
            base.copy(backend = backend.copy(routes = listOf(PluginBackendRoute("/../admin", "python")))),
            base.copy(backend = backend.copy(routes = listOf(PluginBackendRoute("/calculate", "python", permissions = setOf("sql"))))),
            base.copy(backend = backend.copy(routes = listOf(PluginBackendRoute("/calculate", "python", methods = setOf("GET"), permissions = setOf("data.write"))))),
            base.copy(backend = backend.copy(routes = backend.routes + backend.routes)),
            base.copy(backend = backend.copy(allowedOrigins = setOf("http://example.com"))),
            base.copy(backend = backend.copy(allowedOrigins = setOf("https://example.com/api"))),
            base.copy(manifest = base.manifest.copy(runtimes = setOf(PluginRuntime.REACT))),
            base.copy(pages = mapOf("guide" to "index.html"))
        ).forEach { descriptor -> assertThatThrownBy { read(descriptor) }.isInstanceOf(BadRequestException::class.java) }
    }
    @Test fun `frontend bundles must use packaged relative assets`() {
        listOf("https://example.com/app.js", "../outside.js", "missing.js", "/admin/app.js").forEach { src ->
            assertThatThrownBy { PluginPageAssets.render("index.html", mapOf("index.html" to "<script src='$src'></script>")) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { PluginPageAssets.render("index.html", mapOf("index.html" to "<style>@import 'external.css';</style>")) }.isInstanceOf(BadRequestException::class.java)
    }
}
