package com.starter.service.branding

import com.starter.dto.*
import com.starter.exception.*
import com.starter.model.postgres.BrandingPreferencesRecord
import com.starter.repository.postgres.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandingPreferencesService(private val repository: BrandingPreferencesRepository, private val images: BrandingLoginImageRepository) {
    @Transactional(readOnly = true)
    fun current() = view(repository.findById(1).orElseGet { BrandingPreferencesRecord() })

    @Transactional
    fun save(request: BrandingPreferences): BrandingPreferences {
        val name = request.applicationName.trim()
        if (name.isEmpty() || name.length > 80 || name.any { it.isISOControl() }) throw BadRequestException("Use an application name between 1 and 80 characters")
        val background = color(request.sidebarBackgroundColor); val text = color(request.sidebarTextColor); val link = color(request.linkColor)
        if (request.slideshowSeconds !in 5..60 || request.loginBackgroundSlot?.let { it !in 0..4 } == true) throw BadRequestException("Use image slots 1–5 and a slide interval between 5 and 60 seconds")
        if (request.loginBackgroundMode != LoginBackgroundMode.NONE && (images.count() == 0L ||
                (request.loginBackgroundMode == LoginBackgroundMode.SINGLE && request.loginBackgroundSlot != null && !images.existsById(request.loginBackgroundSlot))))
            throw BadRequestException("Upload and select an available sign-in image first")
        val row = repository.findById(1).orElseGet { BrandingPreferencesRecord() }
        if (row.revision != request.revision) throw ConflictException("Branding changed. Reload the saved settings before editing.")
        row.applicationName = name; row.sidebarBackgroundColor = background; row.sidebarTextColor = text; row.linkColor = link
        row.loginBackgroundMode = request.loginBackgroundMode; row.loginBackgroundSlot = request.loginBackgroundSlot; row.slideshowSeconds = request.slideshowSeconds
        return view(repository.saveAndFlush(row))
    }
    private fun color(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!normalized.matches(Regex("#[0-9a-fA-F]{6}"))) throw BadRequestException("Colours must use six-digit hex values, such as #245A78")
        return normalized.lowercase()
    }
    private fun view(row: BrandingPreferencesRecord) = BrandingPreferences(row.applicationName, row.sidebarBackgroundColor, row.sidebarTextColor,
        row.linkColor, row.loginBackgroundMode, row.loginBackgroundSlot, row.slideshowSeconds, row.revision)
}
