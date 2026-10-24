package com.starter.dto

import com.starter.model.postgres.BrandingKind

data class BrandingVersion(val kind: BrandingKind, val version: String)
enum class LoginBackgroundMode { NONE, SINGLE, SLIDESHOW }
data class BrandingPreferences(
    val applicationName: String = "WeaveOps",
    val sidebarBackgroundColor: String? = null,
    val sidebarTextColor: String? = null,
    val linkColor: String? = null,
    val loginBackgroundMode: LoginBackgroundMode = LoginBackgroundMode.NONE,
    val loginBackgroundSlot: Int? = null,
    val slideshowSeconds: Int = 8,
    val revision: Long = 0,
)
data class LoginImageView(val slot: Int, val version: String)
data class BrandingView(val logoVersion: String? = null, val faviconVersion: String? = null,
    val settings: BrandingPreferences = BrandingPreferences(), val loginImages: List<LoginImageView> = emptyList())
