package com.starter.plugins.packages

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.plugins.PluginExtension
import com.starter.plugins.PluginManifests
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Archives are bounded while streaming and never extracted or executed on the server. */
@Component
class PluginZipReader(mapper: ObjectMapper) {
    private val json = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    fun read(bytes: ByteArray): ParsedPluginPackage {
        if (bytes.isEmpty() || bytes.size > MAX_ARCHIVE_BYTES) invalid("ZIP files must be at most 5 MB")
        try {
            val files = linkedMapOf<String, String>()
            val seen = mutableSetOf<String>()
            var total = 0
            var entries = 0
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > 50) invalid("Too many ZIP entries")
                    val path = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
                    if (!path.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]{0,119}")) || path.split('/').any { it in setOf("", ".", "..") }) invalid("Use safe, relative ZIP paths")
                    if (!seen.add(path.lowercase(java.util.Locale.ROOT))) invalid("Duplicate ZIP entry")
                    if (!entry.isDirectory && path !in setOf("plugin.json", "author.json") && !path.endsWith(".html")) invalid("Packages may contain JSON metadata and self-contained HTML pages only")
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (entry.isDirectory && count > 0) invalid("Directory entries must be empty")
                        total += count
                        if (total > MAX_EXPANDED_BYTES || output.size() + count > MAX_FILE_BYTES) invalid("Expanded ZIP exceeds the package limits")
                        output.write(buffer, 0, count)
                    }
                    if (entry.isDirectory) continue
                    files[path] = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
                }
            }
            val descriptor = json.readValue(files["plugin.json"] ?: invalid("plugin.json is required at the ZIP root"), PluginPackageDescriptor::class.java)
            val authorTree = json.readTree(files["author.json"] ?: invalid("author.json is required at the ZIP root"))
            val authorKeys = setOf("name", "organization", "email", "website")
            if (!authorTree.isObject || authorTree.fieldNames().asSequence().toSet() != authorKeys || authorKeys.any { !authorTree[it].isTextual || authorTree[it].asText().length > 300 || authorTree[it].asText().any(Char::isISOControl) }) invalid("author.json needs name, organization, email, and website strings; each may be empty")
            val author = json.treeToValue(authorTree, PluginAuthor::class.java)
            PluginManifests.validateIdentity(descriptor.manifest)
            PluginManifests.validate(descriptor.manifest)
            val manifest = descriptor.manifest
            val supported = setOf(PluginExtension.PAGE, PluginExtension.DATABASE, PluginExtension.SEARCH, PluginExtension.SETTINGS)
            if (PluginExtension.PAGE !in manifest.extensions || !supported.containsAll(manifest.extensions)) invalid("ZIP plugins support pages, managed data, settings, and search; server integrations require reviewed source modules")
            if (manifest.capabilities.isNotEmpty() || manifest.requiresCapabilities.isNotEmpty() || manifest.features.isNotEmpty()) invalid("ZIP plugins cannot declare server capabilities")
            if (manifest.menuItems.map { it.path }.toSet() != descriptor.pages.keys) invalid("Provide a page for every submenu, with no undeclared pages")
            if ((descriptor.pages.values + descriptor.entrypoint).any { !it.endsWith(".html") || files[it].isNullOrBlank() }) invalid("Every entrypoint and submenu page must be an HTML file inside the ZIP")
            return ParsedPluginPackage(descriptor, author, files.filterKeys { it.endsWith(".html") }, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        } catch (error: BadRequestException) { throw error }
        catch (_: Exception) { invalid("Invalid plugin ZIP or metadata. Check the package format and host API version.") }
    }
    private fun invalid(message: String): Nothing = throw BadRequestException(message)
    companion object {
        const val MAX_ARCHIVE_BYTES = 5 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 10 * 1024 * 1024
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
    }
}
