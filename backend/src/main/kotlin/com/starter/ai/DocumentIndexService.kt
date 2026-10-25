package com.starter.ai

import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageDocument
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.service.FolderStorageService
import com.starter.service.VectorService
import com.starter.service.AdminSettingsService
import com.starter.service.storage.StorageEntry
import com.starter.exception.NotFoundException
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DocumentIndexService(
    private val documents: StorageDocumentRepository,
    private val storage: FolderStorageService,
    private val vectors: VectorService,
    private val embeddings: EmbeddingService,
    private val settings: AiSettingsService,
    private val textService: DocumentTextService,
    private val reindexJobs: AdminSettingsService,
    private val processing: DocumentProcessingQueue,
) {
    fun sync(item: Item, entries: List<StorageEntry>, removeMissing: Boolean = false, force: Boolean = false): Map<String, StorageDocument> {
        if (!item.accessible || item.storageConnection?.enabled == false) throw NotFoundException("Storage root not found")
        val existing = documents.findAllByItemId(item.id).associateBy { it.storagePath }
        val config = settings.current()
        val model = embeddings.fingerprint(config.embeddings)
        val seen = entries.map { it.path }.toSet()
        val changed = entries.any { existing[it.path]?.sourceChecksum != DocumentFingerprints.metadata(it) } ||
            (removeMissing && existing.keys.any { it !in seen })
        val synchronized = entries.associate { entry ->
            val checksum = DocumentFingerprints.metadata(entry)
            var document = existing[entry.path] ?: StorageDocument(item = item, storagePath = entry.path)
            val needsIndex = force || document.sourceChecksum != checksum || document.vectorizedAt == null ||
                document.embeddingModel != model || (config.indexContent && document.processingStatus in setOf("METADATA_ONLY", "CONTENT_UNAVAILABLE"))
            document.fileName = entry.name; document.contentType = entry.contentType
            document.sizeBytes = entry.size; document.storageLastModified = entry.lastModified
            document = documents.saveAndFlush(document)
            if (needsIndex) {
                vectors.deleteDocument(document.id)
                document.vectorizedAt = null
                document.sourceChecksum = checksum
                document.embeddingModel = model
                try {
                    index(document, config, model, checksum)
                    document.vectorizedAt = Instant.now()
                } catch (_: AiUnavailableException) {
                    // Storage stays usable when extraction or an AI provider is down. Reindex retries.
                    document.processingStatus = "INDEX_ERROR"
                } catch (_: BadRequestException) {
                    document.processingStatus = "INDEX_ERROR"
                }
                documents.save(document)
            } else if (!config.indexContent && document.processingStatus.startsWith("CONTENT_")) {
                vectors.replaceChunks(document.id, emptyList(), emptyList(), checksum, model)
                document.processingStatus = "METADATA_ONLY"
                documents.save(document)
            }
            entry.path to document
        }
        if (removeMissing) documents.deleteAll(existing.values.filter { it.storagePath !in seen })
        if (changed) reindexJobs.queueFileChanges()
        return synchronized
    }

    private fun index(document: StorageDocument, config: ActiveAiConfiguration, model: String, metadataChecksum: String) {
        document.processingStatus = "METADATA_ONLY"
        processing.cancel(document.id)
        if (config.indexContent) {
            document.processingStatus = when {
                !textService.supports(document.fileName) -> "UNSUPPORTED_FORMAT"
                document.sizeBytes != null && document.sizeBytes!! > textService.maxBytes -> "CONTENT_TOO_LARGE"
                else -> {
                    processing.enqueue(document.id, metadataChecksum, model)
                    "CONTENT_QUEUED"
                }
            }
        }
        val metadata = listOf(document.item.title, document.fileName, document.storagePath.replace('/', ' '), document.contentType.orEmpty()).joinToString(" ")
        val batch = embeddings.embed(listOf(metadata), config.embeddings)
        vectors.upsertDocument(document.id, batch.first(), mapOf(
            "documentId" to document.id.toString(), "folderId" to document.item.id.toString(), "itemId" to document.item.id.toString(),
            "entityType" to "document", "title" to document.fileName, "storagePath" to document.storagePath,
            "processingStatus" to document.processingStatus,
        ), metadataChecksum, model)
    }
}
