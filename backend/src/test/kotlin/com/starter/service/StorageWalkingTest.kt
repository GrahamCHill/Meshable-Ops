package com.starter.service

import com.starter.access.AccessControlService
import com.starter.access.StorageAccess
import com.starter.access.StorageRule
import com.starter.ai.DocumentFingerprints
import com.starter.ai.DocumentIndexService
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageDocument
import com.starter.repository.postgres.*
import com.starter.service.storage.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional

class StorageWalkingTest {
    private val items = mock(ItemRepository::class.java)
    private val documents = mock(StorageDocumentRepository::class.java)
    private val storage = mock(FolderStorageService::class.java)
    private val index = mock(DocumentIndexService::class.java)
    private val acl = mock(AccessControlService::class.java)
    private val jobs = mock(StorageBrowseJobs::class.java)
    private val vectors = mock(VectorService::class.java)
    private val service = ItemService(items, documents, mock(UserRepository::class.java), mock(EventRepository::class.java),
        vectors, storage, mock(AuditLogService::class.java), index, acl, jobs)
    init { `when`(acl.policy()).thenReturn(com.starter.access.AccessPolicy()) }

    @Test fun `2500 new files return without synchronous indexing or a recursive walk`() {
        val folder = Item(storagePrefix = "folders/a/")
        val entries = List(2500) { StorageEntry("$it.txt", "$it.txt", "OBJECT", 12) }
        `when`(items.findById(folder.id)).thenReturn(Optional.of(folder))
        `when`(storage.prefixFor(folder)).thenReturn("folders/a/")
        `when`(storage.browse(folder, "")).thenReturn(StorageBrowseResult("S3", "", entries))
        `when`(acl.storage()).thenReturn(StorageAccess())
        val result = service.browse(folder.id, "")
        assertThat(result.entries).hasSize(2500).allMatch { it.documentId == null && it.vectorStatus == null }
        verify(jobs).request(folder.id, "")
        verify(documents).findListingByItemIdAndStoragePathIn(folder.id, entries.map { it.path })
        verifyNoMoreInteractions(documents)
        verifyNoInteractions(index, vectors)
        verify(storage, never()).listDocuments(folder)
    }

    @Test fun `unchanged files keep index statuses without queueing or rewriting metadata`() {
        val folder = Item(storagePrefix = "folders/a/")
        val entry = StorageEntry("file.txt", "file.txt", "OBJECT", 12)
        val document = StorageDocument(item = folder, storagePath = entry.path, sourceChecksum = DocumentFingerprints.metadata(entry), vectorizedAt = Instant.now())
        `when`(items.findById(folder.id)).thenReturn(Optional.of(folder))
        `when`(storage.prefixFor(folder)).thenReturn("folders/a/")
        `when`(storage.browse(folder, "")).thenReturn(StorageBrowseResult("S3", "", listOf(entry)))
        `when`(documents.findListingByItemIdAndStoragePathIn(folder.id, listOf(entry.path))).thenReturn(listOf(
            StorageDocumentListing(document.id, document.storagePath, document.sourceChecksum, document.processingStatus, document.vectorizedAt)))
        `when`(acl.storage()).thenReturn(StorageAccess())
        assertThat(service.browse(folder.id, "").entries.single().documentId).isEqualTo(document.id.toString())
        verifyNoInteractions(jobs, index, vectors)
    }

    @Test fun `restricted root counts batch paths and resolve permissions once rather than per file`() {
        val roots = List(20) { Item(storagePrefix = "folders/$it/") }
        val policy = StorageAccess(false, roots.map { StorageRule(it.id) }, listOf(StorageRule(roots.first().id, "private/")))
        `when`(acl.storage()).thenReturn(policy)
        `when`(items.findVisibleRootsByConnection(null)).thenReturn(roots)
        roots.forEach { `when`(storage.prefixFor(it)).thenReturn(it.storagePrefix) }
        `when`(storage.prefixesFor(roots)).thenReturn(roots.associate { it.id to it.storagePrefix!! })
        `when`(storage.provider()).thenReturn("S3")
        `when`(documents.findPathsByItemIds(roots.map { it.id })).thenReturn(roots.flatMap { root ->
            listOf("public.txt", "private/file.txt", "__generated_pdfs__/export.pdf").map { path ->
                object : StorageDocumentPath { override val itemId = root.id; override val storagePath = path }
            }
        })
        val result = service.listShared(0, 20)
        assertThat(result.content.first().documentCount).isEqualTo(1)
        assertThat(result.content.drop(1)).allMatch { it.documentCount == 2 }
        verify(acl).storage(null, com.starter.access.AccessPolicy())
        verify(documents).findPathsByItemIds(roots.map { it.id })
        verifyNoMoreInteractions(documents)
        verify(storage, never()).folderCounts(anyList())
    }

    @Test fun `a fresh policy is applied to each directory response including cached provider entries`() {
        val folder = Item(storagePrefix = "folders/a/")
        val entries = listOf(StorageEntry("allowed.txt", "allowed.txt", "OBJECT"), StorageEntry("private", "private/", "FOLDER"))
        `when`(items.findById(folder.id)).thenReturn(Optional.of(folder))
        `when`(storage.prefixFor(folder)).thenReturn("folders/a/")
        `when`(storage.browse(folder, "")).thenReturn(StorageBrowseResult("S3", "", entries))
        `when`(acl.storage()).thenReturn(StorageAccess(), StorageAccess(blocks = listOf(StorageRule(folder.id, "private/"))))
        assertThat(service.browse(folder.id, "").entries).hasSize(2)
        assertThat(service.browse(folder.id, "").entries.map { it.path }).containsExactly("allowed.txt")
        verify(acl, times(2)).storage(null, com.starter.access.AccessPolicy())
    }
}
