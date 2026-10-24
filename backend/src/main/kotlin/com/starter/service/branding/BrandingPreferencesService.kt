package com.starter.service.branding

import com.starter.dto.*
import com.starter.exception.*
import com.starter.model.postgres.BrandingPreferencesRecord
import com.starter.repository.postgres.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandingPreferencesService(private val repository: BrandingPreferencesRepository, private val images: BrandingLoginImageRepository) {
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    @Transactional(readOnly = true)
    fun current() = view(repository.findById(1).orElseGet { BrandingPreferencesRecord() })

    @Transactional
    fun save(request: BrandingPreferences): BrandingPreferences {
        val name = request.applicationName.trim()
        if (name.isEmpty() || name.length > 80 || name.any { it.isISOControl() }) throw BadRequestException("Use an application name between 1 and 80 characters")
        val company=request.companyName.trim()
        if(company.length>120 || company.any {it.isISOControl()})throw BadRequestException("Use a company name of up to 120 characters")
        val background = color(request.sidebarBackgroundColor); val text = color(request.sidebarTextColor); val link = color(request.linkColor)
        if (request.slideshowSeconds !in 5..60 || request.loginBackgroundSlot?.let { it !in 0..4 } == true) throw BadRequestException("Use image slots 1–5 and a slide interval between 5 and 60 seconds")
        if (request.loginBackgroundMode != LoginBackgroundMode.NONE && (images.count() == 0L ||
                (request.loginBackgroundMode == LoginBackgroundMode.SINGLE && request.loginBackgroundSlot != null && !images.existsById(request.loginBackgroundSlot))))
            throw BadRequestException("Upload and select an available sign-in image first")
        val row = repository.findById(1).orElseGet { BrandingPreferencesRecord() }
        if (row.revision != request.revision) throw ConflictException("Branding changed. Reload the saved settings before editing.")
        row.applicationName = name; row.sidebarBackgroundColor = background; row.sidebarTextColor = text; row.linkColor = link
        row.companyName=company
        row.loginBackgroundMode = request.loginBackgroundMode; row.loginBackgroundSlot = request.loginBackgroundSlot; row.slideshowSeconds = request.slideshowSeconds
        row.themesJson = mapper.writeValueAsString(request.themes.copy(light = palette(request.themes.light), dark = palette(request.themes.dark), custom = palette(request.themes.custom)))
        return view(repository.saveAndFlush(row))
    }
    private fun color(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!normalized.matches(Regex("#[0-9a-fA-F]{6}"))) throw BadRequestException("Colours must use six-digit hex values, such as #245A78")
        return normalized.lowercase()
    }
    private fun palette(p: WorkspacePalette) = WorkspacePalette(color(p.background), color(p.surface), color(p.raised), color(p.text),
        color(p.textSecondary), color(p.link), color(p.sidebarBackground), color(p.sidebarText), color(p.tableHeaderBackground),
        color(p.tableHeaderText), color(p.headerBackground), color(p.headerText))
    private fun view(row: BrandingPreferencesRecord) = BrandingPreferences(if (row.applicationName == "WeaveOps") "Meshable Ops" else row.applicationName, row.sidebarBackgroundColor, row.sidebarTextColor,
        row.linkColor, row.loginBackgroundMode, row.loginBackgroundSlot, row.slideshowSeconds, row.revision,
        mapper.readValue(row.themesJson, WorkspaceThemes::class.java), row.companyName)
}
