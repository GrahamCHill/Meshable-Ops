package com.starter.service.branding

import com.starter.exception.*
import com.starter.model.postgres.BrandingLoginImage
import com.starter.repository.postgres.BrandingLoginImageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class BrandingBackgroundService(private val repository: BrandingLoginImageRepository) {
    @Transactional(readOnly = true) fun list() = repository.versions()
    @Transactional(readOnly = true)
    fun image(slot: Int, version: String): ByteArray {
        val row = repository.findById(slot).orElse(null)
        if (row == null || row.version != version) throw NotFoundException("Sign-in image not found")
        return row.jpeg
    }
    @Transactional
    fun upload(slot: Int, file: MultipartFile) {
        slot(slot)
        if (file.size > BrandingImageProfile.LOGIN.maxBytes) throw BadRequestException("Choose an image up to 5 MiB")
        val bytes = BrandingImages.normalize(file.bytes, BrandingImageProfile.LOGIN)
        val row = repository.findById(slot).orElseGet { BrandingLoginImage(slot) }
        row.jpeg = bytes; row.version = BrandingImages.version(bytes); row.updatedAt = Instant.now()
        repository.saveAndFlush(row)
    }
    @Transactional fun remove(slot: Int) { slot(slot); repository.deleteById(slot); repository.flush() }
    private fun slot(slot: Int) { if (slot !in 0..4) throw BadRequestException("Choose image slot 1–5") }
}
