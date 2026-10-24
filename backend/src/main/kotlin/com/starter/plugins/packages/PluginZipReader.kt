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

/** Bounded package ingestion. Backend artifacts execute only in the separate worker runtime. */
@Component
class PluginZipReader(mapper: ObjectMapper) {
    private val json = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    fun read(bytes: ByteArray): ParsedPluginPackage {
        if (bytes.isEmpty() || bytes.size > MAX_ARCHIVE_BYTES) invalid("ZIP files must be at most 16 MiB")
        try {
            val files = linkedMapOf<String, String>()
            val seen = mutableSetOf<String>()
            var total = 0
            var entries = 0
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > 200) invalid("Too many ZIP entries")
                    val path = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
                    if (!path.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]{0,119}")) || path.split('/').any { it in setOf("", ".", "..") }) invalid("Use safe, relative ZIP paths")
                    if (!seen.add(path.lowercase(java.util.Locale.ROOT))) invalid("Duplicate ZIP entry")
                    val extension = path.substringAfterLast('.').lowercase()
                    val binary = extension in setOf("png", "jpg", "jpeg", "webp", "jar", "woff", "woff2")
                    if (!entry.isDirectory && path !in setOf("plugin.json", "author.json", "LICENSE", "NOTICE") && extension !in setOf("html", "js", "mjs", "css", "json", "py", "jar", "kt", "java", "ts", "tsx", "md", "txt", "xml", "toml", "yaml", "yml", "sh", "png", "jpg", "jpeg", "webp", "woff", "woff2")) invalid("Unsupported plugin artifact type")
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
                    files[path] = if (binary) java.util.Base64.getEncoder().encodeToString(output.toByteArray()) else Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
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
            if (manifest.apiVersion != 1) invalid("This host supports plugin API version 1")
            val images = mutableMapOf<String, String>()
            listOf("icon" to manifest.icon, "banner" to manifest.banner).forEach { (kind, path) -> if (path != null) {
                if (path == "builtin" || path.substringAfterLast('.').lowercase() !in setOf("png", "jpg", "jpeg", "webp")) invalid("Plugin artwork must reference a packaged PNG, JPEG, or WebP")
                val raw = files[path] ?: invalid("Missing $kind image")
                val normalized = com.starter.service.branding.BrandingImages.normalize(java.util.Base64.getDecoder().decode(raw), if (kind == "icon") com.starter.service.branding.BrandingImageProfile.FAVICON else com.starter.service.branding.BrandingImageProfile.LOGIN)
                images["__$kind"] = java.util.Base64.getEncoder().encodeToString(normalized)
            } }
            val supported = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE, PluginExtension.SEARCH, PluginExtension.SETTINGS)
            if (manifest.extensions.intersect(setOf(PluginExtension.PAGE, PluginExtension.API)).isEmpty() || !supported.containsAll(manifest.extensions)) invalid("Declare PAGE, API, or both; optional extensions are DATABASE, SEARCH and SETTINGS")
            if (manifest.capabilities.isNotEmpty() || manifest.requiresCapabilities.isNotEmpty() || manifest.features.isNotEmpty()) invalid("ZIP plugins cannot declare server capabilities")
            PluginBackendValidation.check(descriptor, files)
            if (manifest.menuItems.map { it.path }.toSet() != descriptor.pages.keys) invalid("Provide a page for every submenu, with no undeclared pages")
            if (PluginExtension.PAGE in manifest.extensions) {
                (descriptor.pages.values + descriptor.entrypoint).forEach {
                    if (!it.endsWith(".html") || files[it].isNullOrBlank()) invalid("Every frontend entrypoint and submenu must reference a packaged HTML file")
                    PluginPageAssets.render(it, files)
                }
            } else if (descriptor.pages.isNotEmpty() || manifest.menuItems.isNotEmpty()) invalid("Backend-only plugins cannot declare frontend pages or submenus")
            return ParsedPluginPackage(descriptor, author, files.filterKeys { it !in setOf("plugin.json", "author.json") } + images, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        } catch (error: BadRequestException) { throw error }
        catch (_: Exception) { invalid("Invalid plugin ZIP or metadata. Check the package format and host API version.") }
    }
    private fun invalid(message: String): Nothing = throw BadRequestException(message)
    companion object {
        const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 32 * 1024 * 1024
        const val MAX_FILE_BYTES = 12 * 1024 * 1024
    }
}
