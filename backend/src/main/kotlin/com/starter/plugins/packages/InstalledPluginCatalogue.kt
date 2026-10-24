package com.starter.plugins.packages

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.NotFoundException
import org.springframework.stereotype.Service

/** Database-backed so installation and the global kill switch also apply across host replicas. */
@Service
class InstalledPluginCatalogue(private val records: PluginPackageRepository, private val security: ExtensionSecurityRepository, private val mapper: ObjectMapper) {
    fun allowed() = security.findById(1).orElse(null)?.allowThirdPartyExtensions == true
    fun records() = records.findAll()
    fun manifests() = records().map { descriptor(it).manifest }
    fun find(id: String) = records.findById(id).orElse(null)
    fun descriptor(record: PluginPackageRecord) = mapper.readValue(record.descriptorJson, PluginPackageDescriptor::class.java)
    fun author(id: String) = find(id)?.let { mapper.readValue(it.authorJson, PluginAuthor::class.java) }
    fun page(id: String, section: String, role: String): String {
        if (!allowed()) throw NotFoundException("Third-party extensions are disabled")
        val record = find(id) ?: throw NotFoundException("Plugin package not installed")
        val descriptor = descriptor(record)
        val file = if (section.isEmpty()) descriptor.entrypoint else {
            if (descriptor.manifest.menuItems.none { it.path == section && role in it.roles }) throw NotFoundException("Plugin section unavailable")
            descriptor.pages[section] ?: throw NotFoundException("Plugin page unavailable")
        }
        return mapper.readValue(record.filesJson, object : TypeReference<Map<String, String>>() {})[file] ?: throw NotFoundException("Plugin page unavailable")
    }
}
