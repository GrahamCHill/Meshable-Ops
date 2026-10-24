package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.packages.*
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream
import java.util.Optional
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginVersionTest {
    private fun manifest(vararg versions: String) = PluginManifest("version-test","Version test","9.0.0","Version checks",compatibleVersions=versions.toList())
    private fun policy(version: String): PluginVersionPolicy {
        val app=mock(ApplicationVersion::class.java);`when`(app.current()).thenReturn(version);return PluginVersionPolicy(app)
    }
    @Test fun `exact releases lists and single entry arrays use application rather than plugin versions`() {
        assertThat(policy("1.0.0").assess(manifest("1.0.0")).status).isEqualTo(PluginVersionStatus.COMPATIBLE)
        assertThat(policy("1.2.3").assess(manifest("1.0.0","1.2.3","2.0.0")).status).isEqualTo(PluginVersionStatus.COMPATIBLE)
        assertThat(policy("9.0.0").assess(manifest("1.0.0")).status).isEqualTo(PluginVersionStatus.INCOMPATIBLE)
        assertThat(policy("1.0.0+build.9").assess(manifest("1.0.0")).status).isEqualTo(PluginVersionStatus.COMPATIBLE)
    }
    @Test fun `inclusive ranges and comparator bounds support multiple alternatives`() {
        listOf("1.0.0","1.4.2").forEach { assertThat(policy(it).assess(manifest("1.0.0 - 1.4.2")).status).isEqualTo(PluginVersionStatus.COMPATIBLE) }
        assertThat(policy("1.4.3").assess(manifest("1.0.0 - 1.4.2")).status).isEqualTo(PluginVersionStatus.INCOMPATIBLE)
        assertThat(policy("1.9.9").assess(manifest(">=1.0.0 <2.0.0")).status).isEqualTo(PluginVersionStatus.COMPATIBLE)
        assertThat(policy("2.0.0").assess(manifest(">=1.0.0 <2.0.0")).status).isEqualTo(PluginVersionStatus.INCOMPATIBLE)
        assertThat(policy("3.0.1").assess(manifest("1.0.0","> 3.0.0 <= 3.0.1")).status).isEqualTo(PluginVersionStatus.COMPATIBLE)
    }
    @Test fun `SemVer compares numeric components and pre release identifiers without integer overflow`() {
        val order=listOf("1.0.0-alpha","1.0.0-alpha.1","1.0.0-alpha.beta","1.0.0-beta","1.0.0-beta.2","1.0.0-beta.11","1.0.0-rc.1","1.0.0","1.9.0","1.10.0","999999999999999999.0.0")
        assertThat(order.map { PluginVersionRules.version(it)!! }.sorted()).isEqualTo(order.map { PluginVersionRules.version(it)!! })
        assertThat(policy("1.0.0-rc.1").assess(manifest("1.0.0")).status).isEqualTo(PluginVersionStatus.INCOMPATIBLE)
    }
    @Test fun `malformed unbounded and ambiguous declarations are rejected on installation`() {
        listOf("*","1","01.0.0","1.0.0-01","1.0.0 - 0.9.0",">=1.0.0 || <2.0.0",">=1.0.0 garbage <2.0.0",">=1.0.0<2.0.0",">=1.0.0\n<2.0.0","x".repeat(201)).forEach {
            assertThatThrownBy { PluginManifests.validate(manifest(it)) }.describedAs(it).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { PluginManifests.validate(manifest(*Array(51){"1.0.0"})) }.isInstanceOf(IllegalArgumentException::class.java)
        val mapper=jacksonObjectMapper();val reader=PluginZipReader(mapper)
        fun zip(versions: Any): ByteArray {
            val node=mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(manifest().copy(extensions=setOf(PluginExtension.PAGE)));node.set<com.fasterxml.jackson.databind.JsonNode>("compatibleVersions",mapper.valueToTree(versions))
            val files=mapOf("plugin.json" to mapper.writeValueAsString(mapOf("manifest" to node)),"author.json" to """{"name":"","organization":"","email":"","website":""}""","index.html" to "<h1>Test</h1>")
            return ByteArrayOutputStream().also { bytes->ZipOutputStream(bytes).use { output->files.forEach { (path,body)->output.putNextEntry(ZipEntry(path));output.write(body.toByteArray());output.closeEntry() } } }.toByteArray()
        }
        assertThat(reader.read(zip(listOf("1.0.0",">=2.0.0 <3.0.0"))).descriptor.manifest.compatibleVersions).hasSize(2)
        assertThatThrownBy { reader.read(zip("1.0.0")) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `missing declarations and unknown builds warn with backup advice without imposing an approval`() {
        val missing=policy("1.0.0").assess(manifest())
        assertThat(missing.status).isEqualTo(PluginVersionStatus.UNKNOWN)
        assertThat(missing.warnings.single()).contains("may not be compatible","discretion","backup")
        assertThat(policy("unknown").assess(manifest("1.0.0")).warnings.single()).contains("caution","backup")
    }
    @Test fun `host update retains an enabled uploaded plugin and access policy while warning about mismatched versions`() {
        val settings=mock(PluginSettingsRepository::class.java);val packages=mock(InstalledPluginCatalogue::class.java)
        val saved=PluginSettings("version-test",true,allowedRolesCsv="ADMIN",searchEnabled=false)
        val value=manifest("1.0.0");`when`(packages.manifests()).thenReturn(listOf(value));`when`(packages.allowed()).thenReturn(true)
        `when`(settings.findAll()).thenReturn(listOf(saved));`when`(settings.findById(saved.pluginId)).thenReturn(Optional.of(saved))
        fun host(version:String)=PluginRegistry(emptyList(),settings,packages=packages,versions=policy(version))
        assertThat(host("1.0.0").installed().single().versionCompatibility).isEqualTo(PluginVersionStatus.COMPATIBLE)
        val upgraded=host("2.0.0");val view=upgraded.installed().single()
        assertThat(view.active).isTrue();assertThat(view.enabled).isTrue();assertThat(view.allowedRoles).containsExactly("ADMIN")
        assertThat(view.versionCompatibility).isEqualTo(PluginVersionStatus.INCOMPATIBLE);assertThat(view.compatibilityWarnings.single()).contains("remains usable","caution","backup")
        upgraded.requireAvailable(saved.pluginId,"ADMIN")
        assertThat(upgraded.available("USER")).isEmpty()
        verify(settings,never()).saveAndFlush(any(PluginSettings::class.java))
        verify(packages,never()).page(anyString(),anyString(),anyString())
    }
    @Test fun `legacy persisted descriptors deserialize on a new host without losing package contents`() {
        val mapper=jacksonObjectMapper();val json=mapper.writeValueAsString(PluginPackageDescriptor(manifest())).replace(",\"compatibleVersions\":[]","")
        val records=mock(PluginPackageRepository::class.java);val security=mock(ExtensionSecurityRepository::class.java)
        val record=PluginPackageRecord("version-test",json,"""{"name":"","organization":"","email":"","website":""}""","""{"index.html":"<h1>Retained page</h1>"}""","a".repeat(64))
        `when`(records.findById(record.id)).thenReturn(Optional.of(record));`when`(security.findById(1)).thenReturn(Optional.of(ExtensionSecurityRecord(allowThirdPartyExtensions=true)))
        val restarted=InstalledPluginCatalogue(records,security,mapper)
        assertThat(restarted.descriptor(record).manifest.compatibleVersions).isEmpty()
        assertThat(restarted.page(record.id,"","ADMIN")).contains("Retained page")
        assertThat(restarted.find(record.id)?.sha256).isEqualTo(record.sha256)
        verify(records,never()).deleteById(anyString())
    }
    @Test fun `a newly bundled ID cannot silently replace an uploaded package after an update`() {
        val settings=mock(PluginSettingsRepository::class.java);val packages=mock(InstalledPluginCatalogue::class.java);val original=manifest()
        `when`(packages.manifests()).thenReturn(listOf(original));`when`(packages.allowed()).thenReturn(true);`when`(packages.contains(original.id)).thenReturn(true)
        val saved=PluginSettings(original.id,true);`when`(settings.findAll()).thenReturn(listOf(saved));`when`(settings.findById(original.id)).thenReturn(Optional.of(saved))
        val builtin=object:WorkspacePlugin {override val manifest=original.copy(name="New bundled module")}
        val registry=PluginRegistry(listOf(builtin),settings,packages=packages)
        assertThat(registry.definition(original.id).name).isEqualTo(original.name)
        assertThat(registry.installed().single().source).isEqualTo("ZIP")
        assertThat(registry.installed().single().compatibilityIssues.single()).contains("same ID","retained")
        assertThatThrownBy {registry.requireAvailable(original.id,"ADMIN")}.isInstanceOf(NotFoundException::class.java)
    }
}
