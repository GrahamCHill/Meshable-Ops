package com.starter.plugins.figma

import com.starter.plugins.*
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class FigmaPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("figma","Figma","1.0.0","Attach Figma designs to projects, with optional approved file metadata refresh.",
        compatibleVersions=listOf("1.0.0","1.1.0"),extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.DATABASE,PluginExtension.SETTINGS),
        categories=setOf("Development"),contextTargets=setOf("PROJECT"),tables=listOf(PluginTable("plugin_figma_files","Project design references")),
        settingsFields=listOf(PluginSettingField("accessToken","Figma personal access token",PluginFieldType.SECRET,help="Optional. Metadata refresh requires file_metadata:read; links work without a token.",maxLength=4096),
            PluginSettingField("fileKeys","Approved file keys",help="Comma-separated Figma file keys this connection may read. Linking a URL does not grant metadata access.",maxLength=4096)))
    override fun validateConfiguration(values: Map<String,String>) { values["fileKeys"]?.let(::fileKeys) }
    companion object {
        fun fileKeys(raw: String): Set<String> {
            val keys = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (keys.size > 100 || keys.any { !it.matches(Regex("[A-Za-z0-9_-]{5,200}")) }) throw BadRequestException("Choose up to 100 valid Figma file keys")
            return keys
        }
    }
}
