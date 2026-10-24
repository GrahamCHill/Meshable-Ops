package com.starter.service

import com.starter.dto.BrandingView
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.BrandingAsset
import com.starter.model.postgres.BrandingKind
import com.starter.repository.postgres.BrandingAssetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import com.starter.service.branding.BrandingImages
import com.starter.service.branding.BrandingImageProfile

@Service
class BrandingService(private val repository: BrandingAssetRepository) {
    @Transactional(readOnly = true)
    fun current(): BrandingView {
        val versions = repository.versions().associate { it.kind to it.version }
        return BrandingView(versions[BrandingKind.LOGO], versions[BrandingKind.FAVICON])
    }

    @Transactional(readOnly = true)
    fun asset(kind: BrandingKind, version: String): ByteArray {
        val asset = repository.findById(kind).orElse(null)
        if (asset == null || asset.version != version) throw NotFoundException("Brand image not found")
        return asset.png
    }

    @Transactional
    fun upload(kind: BrandingKind, file: MultipartFile): BrandingView {
        if (file.isEmpty || file.size > 1024 * 1024) throw BadRequestException("Choose a PNG, JPEG, or WebP image up to 1 MiB")
        val png = BrandingImages.normalize(file.bytes, BrandingImageProfile.valueOf(kind.name))
        val asset = repository.findById(kind).orElseGet { BrandingAsset(kind) }
        asset.png = png
        asset.version = BrandingImages.version(png)
        asset.updatedAt = Instant.now()
        repository.saveAndFlush(asset)
        return current()
    }

    @Transactional
    fun reset(kind: BrandingKind): BrandingView {
        repository.deleteById(kind)
        repository.flush()
        return current()
    }

}
