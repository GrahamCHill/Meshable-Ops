package com.starter.plugins.packages

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.NotFoundException
import org.springframework.stereotype.Service

data class InstalledPackage(val manifest: com.starter.plugins.PluginManifest, val author: PluginAuthor)

/** Database-backed so installation and the global kill switch also apply across host replicas. */
@Service
class InstalledPluginCatalogue(private val records: PluginPackageRepository, private val security: ExtensionSecurityRepository, private val mapper: ObjectMapper) {
    fun allowed() = security.findById(1).orElse(null)?.allowThirdPartyExtensions == true
    fun manifests() = records.findAllProjectedBy().map { mapper.readValue(it.descriptorJson, PluginPackageDescriptor::class.java).manifest }
    fun installed() = records.findAllProjectedBy().map { InstalledPackage(mapper.readValue(it.descriptorJson, PluginPackageDescriptor::class.java).manifest,
        mapper.readValue(it.authorJson, PluginAuthor::class.java)) }
    fun find(id: String) = records.findById(id).orElse(null)
    fun contains(id: String) = records.existsById(id)
    fun descriptor(record: PluginPackageRecord) = mapper.readValue(record.descriptorJson, PluginPackageDescriptor::class.java)
    fun author(id: String) = records.findMetadataById(id)?.let { mapper.readValue(it.authorJson, PluginAuthor::class.java) }
    fun page(id: String, section: String, role: String): String {
        if (!allowed()) throw NotFoundException("Third-party extensions are disabled")
        val record = find(id) ?: throw NotFoundException("Plugin package not installed")
        val descriptor = descriptor(record)
        val file = if (section.isEmpty()) descriptor.entrypoint else {
            if (descriptor.manifest.menuItems.none { it.path == section && role in it.roles }) throw NotFoundException("Plugin section unavailable")
            descriptor.pages[section] ?: throw NotFoundException("Plugin page unavailable")
        }
        if (com.starter.plugins.PluginExtension.PAGE !in descriptor.manifest.extensions) throw NotFoundException("This plugin has no frontend")
        return PluginPageAssets.render(file, mapper.readValue(record.filesJson, object : TypeReference<Map<String, String>>() {}))
    }
}
