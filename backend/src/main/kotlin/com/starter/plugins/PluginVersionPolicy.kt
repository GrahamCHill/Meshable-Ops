package com.starter.plugins

import org.springframework.stereotype.Component
import java.util.Properties

@Component
class ApplicationVersion {
    /** The packaged Maven release is authoritative; runtime settings cannot spoof compatibility. */
    fun current(): String = release
    companion object {
        private val release = ApplicationVersion::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use { stream ->
            Properties().apply { load(stream) }.getProperty("build.version")
        } ?: "unknown"
    }
}

enum class PluginVersionStatus { COMPATIBLE, UNKNOWN, INCOMPATIBLE }
data class PluginVersionAssessment(val status: PluginVersionStatus, val warnings: Set<String> = emptySet())

@Component
class PluginVersionPolicy(private val release: ApplicationVersion = ApplicationVersion()) {
    val applicationVersion: String get() = release.current()
    fun assess(manifest: PluginManifest): PluginVersionAssessment {
        if (manifest.compatibleVersions.isEmpty()) return PluginVersionAssessment(PluginVersionStatus.UNKNOWN, warnings=setOf(
            "No compatible application versions are declared. This plugin may not be compatible with Meshable Ops $applicationVersion. Use discretion and make a backup before enabling it or updating the application."))
        val host=PluginVersionRules.version(applicationVersion) ?: return PluginVersionAssessment(PluginVersionStatus.UNKNOWN,
            warnings=setOf("The application release version is unavailable, so this plugin's compatibility cannot be verified. Proceed with caution and make a backup before use. Rebuild with Maven build information to verify versions."))
        val rules=manifest.compatibleVersions.map { PluginVersionRules.rule(it) }
        if (rules.any { it==null }) return PluginVersionAssessment(PluginVersionStatus.UNKNOWN,warnings=setOf("Invalid compatibleVersions declaration. Compatibility is unverified; proceed with caution, make a backup, and ask the plugin author for an updated package."))
        return if (rules.any { it!!(host) }) PluginVersionAssessment(PluginVersionStatus.COMPATIBLE)
        else PluginVersionAssessment(PluginVersionStatus.INCOMPATIBLE,warnings=setOf("Meshable Ops $applicationVersion is outside this plugin author's declared compatible versions (${manifest.compatibleVersions.joinToString(", ")}). The plugin remains usable: proceed with caution and make a backup before use or application updates."))
    }
}
