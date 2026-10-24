package com.starter.dto

import com.starter.model.postgres.BrandingKind

data class BrandingVersion(val kind: BrandingKind, val version: String)
enum class LoginBackgroundMode { NONE, SINGLE, SLIDESHOW }
enum class WorkspaceThemeMode { LIGHT, DARK }
data class WorkspacePalette(val background: String? = null, val surface: String? = null, val raised: String? = null,
    val text: String? = null, val textSecondary: String? = null, val link: String? = null,
    val sidebarBackground: String? = null, val sidebarText: String? = null,
    val tableHeaderBackground: String? = null, val tableHeaderText: String? = null,
    val headerBackground: String? = null, val headerText: String? = null)
data class WorkspaceThemes(val switchingEnabled: Boolean = true, val fixedMode: WorkspaceThemeMode = WorkspaceThemeMode.LIGHT,
    val light: WorkspacePalette = WorkspacePalette(), val dark: WorkspacePalette = WorkspacePalette(), val custom: WorkspacePalette = WorkspacePalette())
data class BrandingPreferences(
    val applicationName: String = "Meshable Ops",
    val sidebarBackgroundColor: String? = null,
    val sidebarTextColor: String? = null,
    val linkColor: String? = null,
    val loginBackgroundMode: LoginBackgroundMode = LoginBackgroundMode.NONE,
    val loginBackgroundSlot: Int? = null,
    val slideshowSeconds: Int = 8,
    val revision: Long = 0,
    val themes: WorkspaceThemes = WorkspaceThemes(),
)
data class LoginImageView(val slot: Int, val version: String)
data class BrandingView(val logoVersion: String? = null, val faviconVersion: String? = null,
    val settings: BrandingPreferences = BrandingPreferences(), val loginImages: List<LoginImageView> = emptyList())
