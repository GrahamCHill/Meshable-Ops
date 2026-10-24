package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.packages.PluginZipReader
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.io.ByteArrayOutputStream
import java.util.zip.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class PluginPresentationTest {
    private val settings = mock(PluginSettingsRepository::class.java)
    private val rows = mutableListOf<PluginSettings>()
    init { `when`(settings.findAll()).thenAnswer { rows }; `when`(settings.findById(anyString())).thenAnswer { call -> Optional.ofNullable(rows.find { it.pluginId == call.arguments[0] }) } }
    private fun plugin(value: PluginManifest) = object : WorkspacePlugin { override val manifest = value }
    private fun manifest(id: String) = PluginManifest(id,id,"1.0.0","Example")
    @Test fun `required plugin IDs block activation but optional extensions do not`() {
        val registry = PluginRegistry(listOf(plugin(manifest("base")),plugin(manifest("required").copy(requires=setOf("base"))),plugin(manifest("optional").copy(extends=setOf("base")))),settings)
        rows += listOf(PluginSettings("required",true),PluginSettings("optional",true))
        assertThat(registry.available("USER").map { it.manifest.id }).containsExactly("optional")
        assertThatThrownBy { registry.requireAvailable("required","USER") }.isInstanceOf(NotFoundException::class.java)
        rows += PluginSettings("base",true)
        registry.requireAvailable("required","USER")
    }
    @Test fun `author incompatibilities block both enabled plugins and explain the conflict`() {
        val registry = PluginRegistry(listOf(plugin(manifest("first").copy(incompatibleWith=mapOf("second" to "Conflicting task schema"))),plugin(manifest("second"))),settings)
        rows += listOf(PluginSettings("first",true),PluginSettings("second",true))
        assertThat(registry.available("ADMIN")).isEmpty()
        assertThat(registry.installed().all { it.compatibilityIssues.single().contains("Conflicting task schema") }).isTrue()
        assertThatThrownBy { registry.requireAvailable("second","ADMIN") }.isInstanceOf(NotFoundException::class.java)
        rows.last().enabled=false;registry.requireAvailable("first","ADMIN")
    }
    @Test fun `removed modules and unsupported host APIs remain unavailable`() {
        val registry = PluginRegistry(listOf(plugin(manifest("removed")),plugin(manifest("future").copy(apiVersion=2))),settings)
        rows += listOf(PluginSettings("removed",true,removed=true),PluginSettings("future",true))
        assertThat(registry.available("ADMIN")).isEmpty()
        assertThat(registry.installed().first { it.manifest.id=="future" }.compatibilityIssues).isNotEmpty()
        assertThatThrownBy { registry.requireAvailable("removed","ADMIN") }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { registry.requireAvailable("future","ADMIN") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `remote artwork self requirements and compact panels are rejected`() {
        listOf(manifest("invalid").copy(icon="https://example.com/track.png"),manifest("invalid").copy(requires=setOf("invalid")),manifest("invalid").copy(placements=listOf(PluginPlacement("header.actions","Bad panel",mode=PluginPlacementMode.PANEL)))).forEach {
            assertThatThrownBy { PluginRegistry(listOf(plugin(it)),settings) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
    @Test fun `zip artwork is normalized with categories placements and optional settings`() {
        val mapper=jacksonObjectMapper();val image=ByteArrayOutputStream().also {ImageIO.write(BufferedImage(16,8,BufferedImage.TYPE_INT_RGB),"png",it)}.toByteArray()
        val manifest=manifest("image-test").copy(extensions=setOf(PluginExtension.PAGE),icon="art/icon.png",banner="art/banner.png",categories=setOf("Operations"),hasAdminSettings=false,placements=listOf(PluginPlacement("page.home.after","Notes",mode=PluginPlacementMode.PANEL)))
        val files=mapOf("plugin.json" to mapper.writeValueAsBytes(mapOf("manifest" to manifest)),"author.json" to """{"name":"","organization":"","email":"","website":""}""".toByteArray(),"index.html" to "<h1>Panel</h1>".toByteArray(),"art/icon.png" to image,"art/banner.png" to image)
        val archive=ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { zip -> files.forEach { (path,data) -> zip.putNextEntry(ZipEntry(path));zip.write(data);zip.closeEntry() } } }.toByteArray()
        val parsed=PluginZipReader(mapper).read(archive)
        assertThat(parsed.descriptor.manifest.categories).containsExactly("Operations")
        assertThat(parsed.descriptor.manifest.hasAdminSettings).isFalse()
        val normalized=ImageIO.read(java.util.Base64.getDecoder().decode(parsed.files.getValue("__icon")).inputStream())
        assertThat(normalized.width).isEqualTo(64);assertThat(normalized.height).isEqualTo(64)
        assertThat(parsed.files).containsKeys("__icon","__banner","index.html")
    }
}
