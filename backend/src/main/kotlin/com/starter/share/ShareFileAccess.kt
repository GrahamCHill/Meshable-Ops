package com.starter.share

import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.repository.postgres.ItemRepository
import com.starter.service.FolderStorageService
import com.starter.service.PdfService
import com.starter.service.storage.normalizeStorageObject
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ShareFileAccess(private val items: ItemRepository, private val storage: FolderStorageService) {
    fun check(id: UUID) { if (items.countVisibleById(id) != 1L) throw NotFoundException("Shared file unavailable") }
    fun read(id: UUID, path: String): com.starter.service.storage.StorageObjectDownload {
        check(id)
        val item = items.findById(id).orElseThrow { NotFoundException("Shared file unavailable") }
        val source = storage.downloadForIndex(item, normalizeStorageObject(path), PdfService.MAX_BYTES)
        if (!source.filename.endsWith(".pdf", true)) throw BadRequestException("Only PDF files can be shared")
        check(id); return source
    }
}
