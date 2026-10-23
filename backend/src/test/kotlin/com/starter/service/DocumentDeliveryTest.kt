package com.starter.service

import com.starter.dto.DocumentAccessPolicy
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.DownloadMode
import com.starter.model.postgres.Item
import com.starter.repository.postgres.ItemRepository
import com.starter.service.storage.StorageObjectDownload
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class DocumentDeliveryTest {
    private val settings = mock(DocumentAccessService::class.java)
    private val items = mock(ItemRepository::class.java)
    private val storage = mock(FolderStorageService::class.java)
    private val pdf = mock(PdfService::class.java)
    private val folder = Item(title = "Legal", storagePrefix = "legal/")
    private val source = StorageObjectDownload("contract.pdf", "application/pdf", "%PDF-original".toByteArray())
    private val service = DocumentDeliveryService(settings, items, storage, pdf)
    private fun allow(policy: DocumentAccessPolicy = DocumentAccessPolicy()) {
        `when`(settings.current()).thenReturn(policy)
        `when`(items.findById(folder.id)).thenReturn(Optional.of(folder))
        `when`(items.countVisibleById(folder.id)).thenReturn(1)
        `when`(storage.downloadForIndex(folder, "contract.pdf", PdfService.MAX_BYTES)).thenReturn(source)
    }

    @Test fun `disabled downloads reject direct files and archives before storage access`() {
        allow(DocumentAccessPolicy(downloadMode = DownloadMode.DISABLED))
        listOf(false, true).forEach { archive ->
            assertThatThrownBy { service.download(folder.id, "contract.pdf", archive, "user@test.local") }.isInstanceOf(ForbiddenException::class.java)
        }
        verifyNoInteractions(storage, pdf)
    }

    @Test fun `disabled PDF viewing rejects metadata and page routes before storage access`() {
        allow(DocumentAccessPolicy(pdfPreviewEnabled = false))
        assertThatThrownBy { service.preview(folder.id, "contract.pdf") }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.page(folder.id, "contract.pdf", 1, "user@test.local") }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(storage, pdf)
    }

    @Test fun `normal downloads preserve the original bytes`() {
        allow()
        `when`(storage.download(folder, "contract.pdf", false)).thenReturn(source)
        assertThat(service.download(folder.id, "contract.pdf", false, "user@test.local")).isSameAs(source)
        verifyNoInteractions(pdf)
    }

    @Test fun `watermark mode transforms PDFs even when renamed and leaves other formats unchanged`() {
        allow(DocumentAccessPolicy(downloadMode = DownloadMode.WATERMARKED))
        val renamed = source.copy(filename = "renamed.bin", contentType = "application/octet-stream")
        val text = StorageObjectDownload("notes.txt", "text/plain", "notes".toByteArray())
        val stamped = "%PDF-watermarked".toByteArray()
        `when`(pdf.watermark(renamed.bytes, "CONFIDENTIAL", "user@test.local")).thenReturn(stamped)
        doAnswer { invocation ->
            val transform = invocation.getArgument<(StorageObjectDownload) -> StorageObjectDownload>(4)
            assertThat(transform(text)).isSameAs(text)
            transform(renamed)
        }.`when`(storage).downloadTransformed(eq(folder) ?: folder, eq("renamed.bin") ?: "", eq(false), eq(PdfService.MAX_BYTES), any<(StorageObjectDownload) -> StorageObjectDownload>() ?: { it })
        assertThat(service.download(folder.id, "renamed.bin", false, "user@test.local").bytes).isEqualTo(stamped)
    }

    @Test fun `failed watermarking never returns the original document`() {
        allow(DocumentAccessPolicy(downloadMode = DownloadMode.WATERMARKED))
        doThrow(BadRequestException("Damaged PDF")).`when`(pdf).watermark(source.bytes, "CONFIDENTIAL", "user@test.local")
        doAnswer { invocation -> invocation.getArgument<(StorageObjectDownload) -> StorageObjectDownload>(4)(source) }
            .`when`(storage).downloadTransformed(eq(folder) ?: folder, eq("contract.pdf") ?: "", eq(false), eq(PdfService.MAX_BYTES), any<(StorageObjectDownload) -> StorageObjectDownload>() ?: { it })
        assertThatThrownBy { service.download(folder.id, "contract.pdf", false, "user@test.local") }.isInstanceOf(BadRequestException::class.java)
        verify(storage, never()).download(folder, "contract.pdf", false)
    }

    @Test fun `revoked access prevents PDF delivery after rendering`() {
        allow()
        `when`(items.countVisibleById(folder.id)).thenReturn(1, 0)
        `when`(pdf.page(source.bytes, 1, null, "user@test.local")).thenReturn(byteArrayOf(1))
        assertThatThrownBy { service.page(folder.id, "contract.pdf", 1, "user@test.local") }.isInstanceOf(NotFoundException::class.java)
    }

    @Test fun `policy changes during download prevent delivery`() {
        allow()
        `when`(settings.current()).thenReturn(DocumentAccessPolicy(), DocumentAccessPolicy(downloadMode = DownloadMode.DISABLED))
        `when`(storage.download(folder, "contract.pdf", false)).thenReturn(source)
        assertThatThrownBy { service.download(folder.id, "contract.pdf", false, "user@test.local") }.isInstanceOf(ForbiddenException::class.java)
    }
}
