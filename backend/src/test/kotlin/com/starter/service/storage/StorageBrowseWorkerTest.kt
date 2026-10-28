package com.starter.service.storage

import com.starter.ai.DocumentIndexService
import com.starter.model.postgres.Item
import com.starter.repository.postgres.ItemRepository
import com.starter.service.FolderStorageService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.transaction.*
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.Optional
import java.util.UUID

class StorageBrowseWorkerTest {
    private val jobs = mock(StorageBrowseJobs::class.java)
    private val items = mock(ItemRepository::class.java)
    private val storage = mock(FolderStorageService::class.java)
    private val index = mock(DocumentIndexService::class.java)
    private val manager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?) = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
    private val worker = StorageBrowseWorker(jobs, items, storage, index, manager)
    private val item = Item(storagePrefix = "folders/a/")
    private val job = StorageBrowseJob(item.id, "nested/", 1, UUID.randomUUID())

    @Test fun `worker waits for startup and drops work if the root has been revoked`() {
        worker.poll()
        verifyNoInteractions(jobs)
        `when`(jobs.claim()).thenReturn(job)
        `when`(items.findById(item.id)).thenReturn(Optional.of(item.apply { accessible = false }))
        worker.applicationReady()
        worker.poll()
        verify(jobs).complete(job)
        verifyNoInteractions(storage, index)
    }

    @Test fun `worker indexes the current directory and retains retries after a provider failure`() {
        val entry = StorageEntry("new.txt", "nested/new.txt", "OBJECT")
        `when`(jobs.claim()).thenReturn(job)
        `when`(items.findById(item.id)).thenReturn(Optional.of(item))
        `when`(storage.prefixFor(item)).thenReturn("folders/a/")
        `when`(storage.browse(item, job.path)).thenReturn(StorageBrowseResult("S3", job.path,
            listOf(entry, StorageEntry("empty", "nested/empty/", "FOLDER"))))
        worker.applicationReady()
        worker.poll()
        verify(index).sync(item, listOf(entry), false, false)
        verify(jobs).complete(job)
        reset(jobs)
        `when`(jobs.claim()).thenReturn(job)
        `when`(storage.browse(item, job.path)).thenThrow(IllegalStateException("provider unavailable"))
        worker.poll()
        verify(jobs).failed(job)
        verify(jobs, never()).complete(job)
    }
}
