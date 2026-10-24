package com.starter.plugins.packages

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import com.starter.plugins.WorkspacePlugin
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class PluginPackageService(private val records: PluginPackageRepository, private val security: ExtensionSecurityRepository,
    private val catalogue: InstalledPluginCatalogue, private val reader: PluginZipReader, private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate, private val bundled: List<WorkspacePlugin>, private val registry: PluginRegistry,
    private val configuration: PluginConfigurationService) {
    fun security() = security.findById(1).orElse(null)?.let { ExtensionSecurityView(it.allowThirdPartyExtensions, it.revision) } ?: ExtensionSecurityView(false, -1)

    @Transactional
    fun saveSecurity(request: ExtensionSecurityView): ExtensionSecurityView {
        lock()
        val existing = security.findById(1).orElse(null)
        if ((existing?.revision ?: -1) != request.revision) throw ConflictException("Security settings changed. Reload before saving.")
        val row = existing ?: ExtensionSecurityRecord()
        row.allowThirdPartyExtensions = request.allowThirdPartyExtensions
        security.saveAndFlush(row)
        return security()
    }

    @Transactional
    fun install(file: MultipartFile): com.starter.plugins.PluginView {
        if (file.size > PluginZipReader.MAX_ARCHIVE_BYTES || file.originalFilename?.endsWith(".zip", ignoreCase = true) != true) throw BadRequestException("Choose a plugin ZIP file of at most 5 MB")
        lock()
        if (!catalogue.allowed()) throw ForbiddenException("Enable third-party extensions in Administration → Security first")
        val parsed = reader.read(file.inputStream.use { it.readNBytes(PluginZipReader.MAX_ARCHIVE_BYTES + 1) })
        val manifest = parsed.descriptor.manifest
        if (bundled.any { it.manifest.id == manifest.id } || records.existsById(manifest.id)) throw ConflictException("A plugin with this ID is already installed. Packages cannot replace an installed plugin.")
        if (records.count() >= 30 || manifest.tables.size > 8) throw BadRequestException("At most 30 ZIP plugins and 8 managed tables per package are supported")
        // Only validated identifiers and fixed host-owned DDL. Uploaded SQL/JARs are never executed.
        manifest.tables.forEach { table ->
            jdbc.execute("""CREATE TABLE IF NOT EXISTS "${table.name}" (
                id uuid PRIMARY KEY, owner_id uuid NOT NULL, data jsonb NOT NULL,
                revision bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(),
                updated_at timestamptz NOT NULL DEFAULT now(), search_text text NOT NULL DEFAULT '', search_embedding vector(384))""")
            jdbc.execute("CREATE INDEX IF NOT EXISTS \"${table.name.take(45)}_owner_updated\" ON \"${table.name}\" (owner_id, updated_at)")
        }
        records.saveAndFlush(PluginPackageRecord(manifest.id, mapper.writeValueAsString(parsed.descriptor),
            mapper.writeValueAsString(parsed.author), mapper.writeValueAsString(parsed.files), parsed.sha256))
        jdbc.update("UPDATE plugin_settings SET removed = false, enabled = false WHERE plugin_id = ?", manifest.id)
        return registry.installed().first { it.manifest.id == manifest.id }
    }

    fun runtime(id: String, section: String, role: String): PluginRuntime {
        registry.requireAvailable(id, role)
        val html = org.jsoup.Jsoup.parse(catalogue.page(id, section, com.starter.access.Roles.plugin(role))).also {
            it.select("meta[http-equiv], base, iframe, object, embed").remove()
        }.outerHtml()
        // SECRET fields are always redacted by the existing encrypted configuration service.
        return PluginRuntime(html, configuration.view(id).values)
    }
    private fun lock() { jdbc.query("SELECT pg_advisory_xact_lock(hashtext('workspace-extension-installation'))", org.springframework.jdbc.core.RowCallbackHandler { }) }
}
