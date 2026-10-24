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
        var chunks = emptyList<String>()
        var contentChecksum = metadataChecksum
        document.processingStatus = "METADATA_ONLY"
        if (config.indexContent) {
            try {
                when {
                    !textService.supports(document.fileName) -> document.processingStatus = "UNSUPPORTED_FORMAT"
                    document.sizeBytes == null || document.sizeBytes!! > 5 * 1024 * 1024 -> document.processingStatus = "CONTENT_TOO_LARGE"
                    else -> {
                        val content = storage.downloadForIndex(document.item, document.storagePath, 5 * 1024 * 1024)
                        contentChecksum = DocumentFingerprints.hash(content.bytes)
                        val extracted = textService.extract(document.fileName, content.bytes)
                        chunks = documentChunks(extracted.text)
                        document.processingStatus = when {
                            chunks.isEmpty() -> "NO_EXTRACTABLE_TEXT"
                            extracted.truncated || extracted.text.length > 32 * 1400 + 200 -> "CONTENT_TRUNCATED"
                            else -> "CONTENT_INDEXED"
                        }
                    }
                }
            } catch (_: AiUnavailableException) { document.processingStatus = "CONTENT_UNAVAILABLE" }
              catch (_: BadRequestException) { document.processingStatus = "CONTENT_UNAVAILABLE" }
        }
        val metadata = listOf(document.item.title, document.fileName, document.storagePath.replace('/', ' '), document.contentType.orEmpty()).joinToString(" ")
        val batch = embeddings.embed(listOf(metadata) + chunks.map { "${document.fileName}\n$it" }, config.embeddings)
        vectors.upsertDocument(document.id, batch.first(), mapOf(
            "documentId" to document.id.toString(), "folderId" to document.item.id.toString(), "itemId" to document.item.id.toString(),
            "entityType" to "document", "title" to document.fileName, "storagePath" to document.storagePath,
            "processingStatus" to document.processingStatus,
        ), metadataChecksum, model)
        vectors.replaceChunks(document.id, chunks, batch.drop(1), contentChecksum, model)
    }
}
