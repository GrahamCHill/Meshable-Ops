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
    private val endpoint = AiEndpoint(AiProvider.LOCAL, "", "local")
    private val service = DocumentIndexService(documents, storage, vectors, embeddings, settings, text, jobs)
    init { `when`(documents.saveAndFlush(any(StorageDocument::class.java))).thenAnswer { it.arguments[0] } }

    @Test fun `vectors use the persisted document identity returned by JPA merge`() {
        val folder = Item(storagePrefix = "shared/")
        val persisted = StorageDocument(item = folder, storagePath = "file.txt", fileName = "file.txt")
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, endpoint, endpoint))
        `when`(embeddings.fingerprint(endpoint)).thenReturn("local-model")
        `when`(documents.saveAndFlush(any(StorageDocument::class.java))).thenReturn(persisted)
        `when`(embeddings.embed(anyList(), eq(endpoint) ?: endpoint)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { 0.1f }))
        val result = service.sync(folder, listOf(StorageEntry("file.txt", "file.txt", "OBJECT", size = 4))).getValue("file.txt")
        assertThat(result.id).isEqualTo(persisted.id)
        verify(vectors).upsertDocument(eq(persisted.id) ?: persisted.id, anyList(), anyMap(), anyString(), eq("local-model") ?: "local-model")
    }

    @Test fun `missing extraction service still indexes metadata without failing storage browsing`() {
        val folder = Item(storagePrefix = "shared/")
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, true, endpoint, endpoint))
        `when`(embeddings.fingerprint(endpoint)).thenReturn("local-model")
        `when`(text.supports("contract.txt")).thenReturn(true)
        `when`(storage.downloadForIndex(folder, "contract.txt", 5 * 1024 * 1024)).thenReturn(StorageObjectDownload("contract.txt", "text/plain", "body".toByteArray()))
        `when`(text.extract(eq("contract.txt") ?: "contract.txt", any(ByteArray::class.java) ?: byteArrayOf())).thenThrow(AiUnavailableException("Extraction offline"))
        `when`(embeddings.embed(anyList(), eq(endpoint) ?: endpoint)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { 0.1f }))
        val document = service.sync(folder, listOf(StorageEntry("contract.txt", "contract.txt", "OBJECT", size = 4))).getValue("contract.txt")
        assertThat(document.processingStatus).isEqualTo("CONTENT_UNAVAILABLE")
        assertThat(document.vectorizedAt).isNotNull()
        verify(vectors).upsertDocument(eq(document.id) ?: document.id, anyList(), anyMap(), anyString(), eq("local-model") ?: "local-model")
    }

    @Test fun `content indexing reads a bounded object and stores chunks tied to its document and embedding model`() {
        val folder = Item(storagePrefix = "shared/", storageConnection = StorageConnection())
        val body = "Renewal notice must be delivered 60 days in advance. ".repeat(60)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, true, endpoint, endpoint))
        `when`(embeddings.fingerprint(endpoint)).thenReturn("model-id")
        `when`(documents.saveAndFlush(any(StorageDocument::class.java))).thenAnswer { it.arguments[0] }
        `when`(text.supports("contract.txt")).thenReturn(true)
        `when`(storage.downloadForIndex(folder, "contract.txt", 5 * 1024 * 1024)).thenReturn(StorageObjectDownload("contract.txt", "text/plain", body.toByteArray()))
        `when`(text.extract(eq("contract.txt") ?: "contract.txt", any(ByteArray::class.java) ?: byteArrayOf())).thenReturn(ExtractedDocumentText(body, false))
        `when`(embeddings.embed(anyList(), eq(endpoint) ?: endpoint)).thenAnswer { call -> List((call.arguments[0] as List<*>).size) { List(EmbeddingDimensions.SIZE) { 0.1f } } }
        val result = service.sync(folder, listOf(StorageEntry("contract.txt", "contract.txt", "OBJECT", size = body.length.toLong())))
        val document = result.getValue("contract.txt")
        assertThat(document.processingStatus).isEqualTo("CONTENT_INDEXED")
        assertThat(document.vectorizedAt).isNotNull()
        verify(vectors).replaceChunks(eq(document.id) ?: document.id, argThat<List<String>> { it.size > 1 && it.first().contains("Renewal notice") } ?: emptyList(), anyList(), anyString(), eq("model-id") ?: "model-id")
        verify(storage).downloadForIndex(folder, "contract.txt", 5 * 1024 * 1024)
        verify(jobs).queueFileChanges()
    }
    @Test fun `indexing hidden folders never reads storage or calls a provider`() {
        assertThatThrownBy { service.sync(Item(accessible = false), emptyList()) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(documents, storage, vectors, embeddings, settings, text)
    }
    @Test fun `chunking is bounded and keeps overlap without an infinite tail`() {
        assertThat(documentChunks("")).isEmpty()
        val chunks = documentChunks("a".repeat(100000))
        assertThat(chunks).hasSize(32)
        assertThat(chunks.all { it.length <= 1600 }).isTrue()
        assertThat(documentChunks("short")).containsExactly("short")
    }
}
