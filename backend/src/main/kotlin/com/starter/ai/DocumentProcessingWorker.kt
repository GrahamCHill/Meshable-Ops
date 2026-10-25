package com.starter.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.service.FolderStorageService
import com.starter.service.VectorService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class DocumentProcessingWorker(
    private val queue: DocumentProcessingQueue,
    private val documents: StorageDocumentRepository,
    private val storage: FolderStorageService,
    private val text: DocumentTextService,
    private val embeddings: EmbeddingService,
    private val settings: AiSettingsService,
    private val vectors: VectorService,
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
    manager: PlatformTransactionManager,
    @Value("\${app.documents.max-bytes:52428800}") private val maxBytes: Int,
) {
    private val tx = TransactionTemplate(manager)
    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile private var ready = false
    init { require(maxBytes in 1..104857600) }

    @EventListener(ApplicationReadyEvent::class)
    fun applicationReady() { ready = true }

    @Scheduled(fixedDelay = 2000, scheduler = "documentTaskScheduler")
    fun poll() {
        if (!ready) return
        val job = queue.claim() ?: return
        try { process(job) }
        catch (_: Exception) {
            queue.failed(job)
            log.warn("Document processing attempt {} failed for {}; retry state retained", job.attempts, job.documentId)
        }
    }

    internal fun process(job: DocumentJob) {
        val document = documents.findForProcessing(job.documentId) ?: return
        if (com.starter.service.storage.GeneratedPdfNamespace.excluded(document.item.storagePrefix, document.storagePath)) {
            tx.executeWithoutResult {
                if (queue.lockDocument(job.documentId) && queue.owns(job)) { vectors.deleteDocument(document.id); queue.complete(job) }
            }
            return
        }
        val config = settings.current()
        if (!config.indexContent || !document.item.accessible || document.item.storageConnection?.enabled == false) {
            tx.executeWithoutResult { if (queue.lockDocument(job.documentId) && queue.owns(job)) queue.complete(job) }
            return
        }
        if (document.sourceChecksum != job.checksum) {
            tx.executeWithoutResult { if (queue.lockDocument(job.documentId) && queue.owns(job)) queue.complete(job) }
            return
        }
        val model = embeddings.fingerprint(config.embeddings)
        if (model != job.model) {
            tx.executeWithoutResult { if (queue.lockDocument(job.documentId) && queue.owns(job)) queue.enqueue(document.id, job.checksum, model) }
            return
        }
        val content = storage.downloadForIndex(document.item, document.storagePath, maxBytes)
        val extracted = text.extract(document.fileName, content.bytes)
        val (chunks, truncated) = pageChunks(extracted)
        // Provider batches are bounded independently of document length.
        val batch = chunks.chunked(32).flatMap { part ->
            if (!queue.heartbeat(job)) return
            embeddings.embed(part.map { "${document.fileName}\n${it.text}" }, config.embeddings)
        }
        val checksum = DocumentFingerprints.hash(content.bytes)
        tx.executeWithoutResult {
            // Same lock order as browsing: document first, queue second.
            val current = jdbc.query("SELECT source_checksum FROM storage_documents WHERE id=? FOR UPDATE",
                { rs, _ -> rs.getString(1) }, document.id).singleOrNull()
            if (current != job.checksum || !queue.owns(job)) return@executeWithoutResult
            val live = settings.current()
            if (!live.indexContent || embeddings.fingerprint(live.embeddings) != model) {
                if (live.indexContent) queue.enqueue(document.id, job.checksum, embeddings.fingerprint(live.embeddings))
                else queue.complete(job)
                return@executeWithoutResult
            }
            val available = jdbc.queryForObject("""SELECT count(*) FROM items i LEFT JOIN storage_connections sc ON sc.id=i.storage_connection_id
                WHERE i.id=? AND i.accessible=TRUE AND (sc.id IS NULL OR sc.enabled=TRUE)""", Long::class.java, document.item.id) == 1L
            if (!available) { queue.complete(job); return@executeWithoutResult }
            vectors.replacePageChunks(document.id, chunks, batch, checksum, model)
            jdbc.update("DELETE FROM document_pages WHERE document_id=?", document.id)
            extracted.pages.ifEmpty { listOf(ExtractedPage(null, extracted.text)) }.forEachIndexed { index, page ->
                jdbc.update("""INSERT INTO document_pages(document_id, page_index, page_number, content, metadata)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb))""", document.id, index, page.pageNumber, page.text,
                    json.writeValueAsString(mapOf("method" to page.method, "confidence" to page.confidence,
                        "engine" to extracted.engine, "languages" to extracted.languages)))
            }
            val status = when { extracted.ocrRequired -> "OCR_REQUIRED"; chunks.isEmpty() -> "NO_EXTRACTABLE_TEXT"; truncated -> "CONTENT_TRUNCATED"; else -> "CONTENT_INDEXED" }
            jdbc.update("UPDATE storage_documents SET processing_status=?, embedding_model=?, updated_at=NOW() WHERE id=?", status, model, document.id)
            jdbc.update("UPDATE document_vectors SET payload=jsonb_set(payload, '{processingStatus}', to_jsonb(CAST(? AS text))) WHERE document_id=?", status, document.id)
            queue.complete(job)
        }
    }
}
