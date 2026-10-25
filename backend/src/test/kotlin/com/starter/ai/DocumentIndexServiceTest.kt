package com.starter.ai

import com.starter.model.postgres.*
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.service.*
import com.starter.service.storage.*
import com.starter.exception.NotFoundException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class DocumentIndexServiceTest {
    private val documents = mock(StorageDocumentRepository::class.java)
    private val storage = mock(FolderStorageService::class.java)
    private val vectors = mock(VectorService::class.java)
    private val embeddings = mock(EmbeddingService::class.java)
    private val settings = mock(AiSettingsService::class.java)
    private val text = mock(DocumentTextService::class.java)
    private val jobs = mock(AdminSettingsService::class.java)
    private val processing = mock(DocumentProcessingQueue::class.java)
    private val endpoint = AiEndpoint(AiProvider.LOCAL, "", "local")
    private val service = DocumentIndexService(documents, storage, vectors, embeddings, settings, text, jobs, processing)
    init {
        `when`(documents.saveAndFlush(any(StorageDocument::class.java))).thenAnswer { it.arguments[0] }
        `when`(text.maxBytes).thenReturn(52428800)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, true, endpoint, endpoint))
        `when`(embeddings.fingerprint(endpoint)).thenReturn("model-id")
        `when`(embeddings.embed(anyList(), eq(endpoint) ?: endpoint)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { 0.1f }))
    }

    @Test fun `browsing queues content without downloading or extracting bytes`() {
        val folder = Item(storagePrefix = "shared/")
        `when`(text.supports("scan.pdf")).thenReturn(true)
        val document = service.sync(folder, listOf(StorageEntry("scan.pdf", "scan.pdf", "OBJECT", size = 20_000_000))).getValue("scan.pdf")
        assertThat(document.processingStatus).isEqualTo("CONTENT_QUEUED")
        verify(processing).enqueue(document.id, document.sourceChecksum!!, "model-id")
        verifyNoInteractions(storage)
        verify(text, never()).extract(anyString(), any(ByteArray::class.java) ?: byteArrayOf())
    }

    @Test fun `metadata vectors use the persisted identity returned by JPA merge`() {
        val folder = Item(storagePrefix = "shared/")
        val persisted = StorageDocument(item = folder, storagePath = "note.txt", fileName = "note.txt")
        `when`(documents.saveAndFlush(any(StorageDocument::class.java))).thenReturn(persisted)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, endpoint, endpoint))
        service.sync(folder, listOf(StorageEntry("note.txt", "note.txt", "OBJECT", size = 4)))
        verify(vectors).upsertDocument(eq(persisted.id) ?: persisted.id, anyList(), anyMap(), anyString(), eq("model-id") ?: "model-id")
        verify(processing).cancel(persisted.id)
    }

    @Test fun `hidden folders never queue or call a provider`() {
        assertThatThrownBy { service.sync(Item(accessible = false), emptyList()) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(processing, storage, documents)
    }

    @Test fun `long documents continue beyond the former 32 chunk cap`() {
        val chunks = documentChunks("a".repeat(100000))
        assertThat(chunks.size).isGreaterThan(32)
        assertThat(chunks.all { it.length <= 1600 }).isTrue()
        assertThat(documentChunks("short")).containsExactly("short")
    }

    @Test fun `page provenance survives chunking and exceeding the cap is explicit`() {
        val extracted = ExtractedDocumentText("", false, listOf(ExtractedPage(7, "first page"), ExtractedPage(8, "second page")))
        val (chunks, truncated) = pageChunks(extracted)
        assertThat(chunks.map { it.pageNumber }).containsExactly(7, 8)
        assertThat(truncated).isFalse()
        assertThat(pageChunks(extracted, 1).second).isTrue()
    }
}
