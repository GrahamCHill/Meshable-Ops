package com.starter.plugins.packages

import com.starter.plugins.PluginManifest
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

data class PluginAuthor(val name: String, val organization: String, val email: String, val website: String)
data class PluginPackageDescriptor(val manifest: PluginManifest, val entrypoint: String = "index.html", val pages: Map<String, String> = emptyMap())
data class ParsedPluginPackage(val descriptor: PluginPackageDescriptor, val author: PluginAuthor, val files: Map<String, String>, val sha256: String)
data class PluginRuntime(val html: String, val configuration: Map<String, String>)

@Entity @Table(name = "workspace_plugin_packages")
class PluginPackageRecord(@Id val id: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") val descriptorJson: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") val authorJson: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") val filesJson: String = "",
    @Column(nullable = false, length = 64) val sha256: String = "",
    @Column(nullable = false) val installedAt: Instant = Instant.now())
interface PluginPackageMetadata { val id: String; val descriptorJson: String; val authorJson: String }
interface PluginPackageRepository : JpaRepository<PluginPackageRecord, String> {
    fun findAllProjectedBy(): List<PluginPackageMetadata>
    fun findMetadataById(id: String): PluginPackageMetadata?
}

@Entity @Table(name = "workspace_extension_security")
class ExtensionSecurityRecord(@Id val id: Int = 1, var allowThirdPartyExtensions: Boolean = false,
    @Version var revision: Long = 0)
interface ExtensionSecurityRepository : JpaRepository<ExtensionSecurityRecord, Int>
data class ExtensionSecurityView(val allowThirdPartyExtensions: Boolean, val revision: Long)
