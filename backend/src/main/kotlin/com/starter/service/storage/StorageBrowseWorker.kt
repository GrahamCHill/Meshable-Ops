package com.starter.service.storage

import com.starter.ai.DocumentIndexService
import com.starter.repository.postgres.ItemRepository
import com.starter.service.FolderStorageService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class StorageBrowseWorker(private val jobs: StorageBrowseJobs, private val items: ItemRepository,
    private val storage: FolderStorageService, private val index: DocumentIndexService, manager: PlatformTransactionManager) {
    private val tx = TransactionTemplate(manager)
    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile private var ready = false

    @EventListener(ApplicationReadyEvent::class)
    fun applicationReady() { ready = true }

    @Scheduled(fixedDelay = 1000, scheduler = "storageTaskScheduler")
    fun poll() {
        if (!ready) return
        val job = jobs.claim() ?: return
        try {
            tx.executeWithoutResult {
                val item = items.findById(job.itemId).orElse(null)
                if (item != null && item.accessible && item.storageConnection?.enabled != false &&
                    !GeneratedPdfNamespace.excluded(storage.prefixFor(item), job.path)) {
                    // Read current storage/settings rather than indexing a request's potentially stale snapshot.
                    index.sync(item, storage.browse(item, job.path).entries.filter { it.type == "OBJECT" })
                }
                jobs.complete(job)
            }
        } catch (_: Exception) {
            jobs.failed(job)
            log.warn("Storage directory indexing failed for {}; retry retained", job.itemId)
        }
    }
}
