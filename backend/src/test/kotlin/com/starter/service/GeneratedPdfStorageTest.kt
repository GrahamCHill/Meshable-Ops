package com.starter.service

import com.starter.service.pdfstorage.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import java.util.UUID

class GeneratedPdfStorageTest {
    private val destinations = mock(GeneratedPdfDestinations::class.java)
    private val files = mock(GeneratedPdfFiles::class.java)
    private val backend = mock(GeneratedPdfBackend::class.java)
    private val storage = GeneratedPdfStorage(destinations, files, listOf(backend))
    private val id = UUID.randomUUID()
    private val bytes = "test-pdf-content".toByteArray()
    private val location = PdfStorageLocation("S3", "DEFAULT", mapOf("bucket" to "test", "prefix" to "folders/__generated_pdfs__/"))
    init {
        `when`(backend.supports("S3")).thenReturn(true)
        `when`(backend.prepare(location, id)).thenReturn(mapOf("path" to "$id.pdf"))
    }

    @Test fun `unconfigured storage retains database bytes without contacting a provider`() {
        assertThat(storage.persist(id, GeneratedPdfKind.DOCUMENT, bytes)).isEqualTo(bytes)
        verifyNoInteractions(files, backend)
    }

    @Test fun `successful external persistence leaves only a durable reference and reads the original bytes`() {
        `when`(destinations.resolve()).thenReturn(location)
        assertThat(storage.persist(id, GeneratedPdfKind.DOCUMENT, bytes)).isEmpty()
        val captured = ArgumentCaptor.forClass(GeneratedPdfFile::class.java)
        verify(files).begin(captured.capture() ?: GeneratedPdfFile(id,GeneratedPdfKind.DOCUMENT,location,emptyMap(),0,""))
        val file = captured.value
        verify(files).activate(id)
        verify(backend).upload(file, bytes)
        `when`(files.active(id)).thenReturn(file)
        `when`(destinations.credentials(file)).thenReturn(file)
        `when`(backend.download(file)).thenReturn(bytes)
        assertThat(storage.read(id, byteArrayOf())).isEqualTo(bytes)
        `when`(backend.download(file)).thenReturn("changed-content".toByteArray())
        assertThatThrownBy { storage.read(id, byteArrayOf()) }.isInstanceOf(GeneratedPdfStorageUnavailable::class.java)
    }

    @Test fun `failed uploads retain database bytes and queue any partially uploaded object for deletion`() {
        `when`(destinations.resolve()).thenReturn(location)
        doThrow(IllegalStateException("test upload failure")).`when`(backend).upload(any(GeneratedPdfFile::class.java) ?: GeneratedPdfFile(id,GeneratedPdfKind.DOCUMENT,location,emptyMap(),0,""), eq(bytes) ?: bytes)
        assertThat(storage.persist(id, GeneratedPdfKind.DOCUMENT, bytes)).isEqualTo(bytes)
        verify(files).abandon(id)
        verify(files, never()).activate(id)
    }

    @Test fun `legacy database PDFs do not need any external storage configuration`() {
        assertThat(storage.read(id, bytes)).isEqualTo(bytes)
        verifyNoInteractions(destinations, files, backend)
    }

    @Test fun `failed cloud deletions keep a retryable record instead of losing the locator`() {
        val file = GeneratedPdfFile(id, GeneratedPdfKind.REPORT, location, mapOf("path" to "$id.pdf"), bytes.size, "checksum")
        `when`(files.pending()).thenReturn(file)
        `when`(destinations.credentials(file)).thenReturn(file)
        doThrow(IllegalStateException("test outage")).`when`(backend).delete(file)
        assertThat(GeneratedPdfDeletion(files, destinations, listOf(backend)).next()).isTrue()
        verify(files).retry(id)
        verify(files, never()).deleted(id)
    }
}
