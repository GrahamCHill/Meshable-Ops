package com.starter.service

import com.starter.dto.DocumentAccessPolicy
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.DownloadMode
import com.starter.repository.postgres.ItemRepository
import com.starter.service.storage.StorageObjectDownload
import com.starter.service.storage.normalizeStorageObject
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DocumentDeliveryService(
    private val settings: DocumentAccessService,
    private val items: ItemRepository,
    private val storage: FolderStorageService,
    private val pdf: PdfService,
) {
    fun download(id: UUID, path: String, archive: Boolean, actor: String): StorageObjectDownload {
        val policy = settings.current()
        if (policy.downloadMode == DownloadMode.DISABLED) throw ForbiddenException("Downloads are disabled by an administrator")
        val item = accessible(id)
        val result = if (policy.downloadMode == DownloadMode.NORMAL) storage.download(item, path, archive)
            else storage.downloadTransformed(item, path, archive, PdfService.MAX_BYTES) {
                if (isPdf(it)) StorageObjectDownload(it.filename, "application/pdf", pdf.watermark(it.bytes, policy.watermarkText, actor)) else it
            }
        verifyUnchanged(id, policy)
        return result
    }

    fun preview(id: UUID, path: String) = withPreview(id, path) { bytes, filename, _ -> pdf.inspect(bytes, filename) }

    fun page(id: UUID, path: String, page: Int, actor: String): ByteArray = withPreview(id, path) { bytes, _, policy ->
        pdf.page(bytes, page, policy.watermarkText.takeIf { policy.downloadMode == DownloadMode.WATERMARKED }, actor)
    }

    private fun <T> withPreview(id: UUID, path: String, action: (ByteArray, String, DocumentAccessPolicy) -> T): T {
        val policy = settings.current()
        if (!policy.pdfPreviewEnabled) throw ForbiddenException("PDF viewing is disabled by an administrator")
        val item = accessible(id)
        val source = storage.downloadForIndex(item, normalizeStorageObject(path), PdfService.MAX_BYTES)
        if (!isPdf(source)) throw BadRequestException("Select a PDF document")
        val result = action(source.bytes, source.filename, policy)
        verifyUnchanged(id, policy)
        return result
    }

    private fun accessible(id: UUID) = items.findById(id).orElseThrow { NotFoundException("Storage root not found") }.also {
        if (!it.accessible || it.storageConnection?.enabled == false || items.countVisibleById(id) != 1L) throw NotFoundException("Storage root not found")
    }

    private fun verifyUnchanged(id: UUID, policy: DocumentAccessPolicy) {
        if (items.countVisibleById(id) != 1L) throw NotFoundException("Storage root not found")
        if (settings.current() != policy) throw ForbiddenException("Document access settings changed. Try again.")
    }

    private fun isPdf(source: StorageObjectDownload) = source.filename.endsWith(".pdf", ignoreCase = true) ||
        source.contentType.substringBefore(';').equals("application/pdf", ignoreCase = true) ||
        source.bytes.take(1024).toByteArray().toString(Charsets.ISO_8859_1).contains("%PDF-")
}
