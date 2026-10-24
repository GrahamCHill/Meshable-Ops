package com.starter.service.branding

import com.starter.dto.BrandingPreferences
import com.starter.model.postgres.BrandingKind
import com.starter.service.BrandingService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class WorkspaceBrandingService(private val assets: BrandingService, private val preferences: BrandingPreferencesService, private val backgrounds: BrandingBackgroundService) {
    @Transactional(readOnly = true)
    fun current() = assets.current().copy(settings = preferences.current(), loginImages = backgrounds.list())
    fun asset(kind: BrandingKind, version: String) = assets.asset(kind, version)
    fun background(slot: Int, version: String) = backgrounds.image(slot, version)
    fun upload(kind: BrandingKind, file: MultipartFile) = assets.upload(kind, file).let { current() }
    fun reset(kind: BrandingKind) = assets.reset(kind).let { current() }
    fun save(request: BrandingPreferences) = preferences.save(request).let { current() }
    fun uploadBackground(slot: Int, file: MultipartFile) = backgrounds.upload(slot, file).let { current() }
    fun removeBackground(slot: Int) = backgrounds.remove(slot).let { current() }
}
