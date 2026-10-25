package com.starter.service.pdfstorage

import com.starter.service.PdfService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

class GeneratedPdfStorageUnavailable : RuntimeException("Saved PDF storage is unavailable. Check the configured storage connection.")

@Service
class GeneratedPdfStorage(private val destinations: GeneratedPdfDestinations, private val files: GeneratedPdfFiles,
    private val backends: List<GeneratedPdfBackend>) {
    private val log = LoggerFactory.getLogger(javaClass)
    private fun backend(provider: String) = backends.single { it.supports(provider) }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** The source record keeps legacy inline bytes when no external location is usable. */
    fun persist(id: UUID, kind: GeneratedPdfKind, bytes: ByteArray): ByteArray {
        var tracked = false
        try {
            val location = destinations.resolve() ?: return bytes
            if (bytes.size > PdfService.MAX_BYTES) return bytes
            val backend = backend(location.provider)
            val file = GeneratedPdfFile(id, kind, location, backend.prepare(location, id), bytes.size, hash(bytes))
            files.begin(file); tracked = true
            backend.upload(file, bytes)
            files.activate(id)
            runCatching { files.status("Saved generated PDF to ${location.provider} in the reserved export folder.") }
            return byteArrayOf()
        } catch (_: Exception) {
            if (tracked) runCatching { files.abandon(id) }
            log.warn("External generated PDF storage failed for {}; retained database bytes", id)
            runCatching { files.status("External PDF storage unavailable; the generated PDF was safely stored in the database.") }
            return bytes
        }
    }

    fun read(id: UUID, legacy: ByteArray): ByteArray? {
        if (legacy.isNotEmpty()) return legacy
        try {
            val file = files.active(id) ?: return null
            val bytes = backend(file.location.provider).download(destinations.credentials(file))
            if (bytes.size != file.size || hash(bytes) != file.checksum) throw GeneratedPdfStorageUnavailable()
            return bytes
        } catch (_: Exception) { throw GeneratedPdfStorageUnavailable() }
    }
}

@Service
class GeneratedPdfDeletion(private val files: GeneratedPdfFiles, private val destinations: GeneratedPdfDestinations,
    private val backends: List<GeneratedPdfBackend>) {
    @Transactional
    fun next(): Boolean {
        val file = files.pending() ?: return false
        try {
            backends.single { it.supports(file.location.provider) }.delete(destinations.credentials(file))
            files.deleted(file.id)
        } catch (_: Exception) {
            files.retry(file.id)
            LoggerFactory.getLogger(javaClass).warn("Generated PDF deletion deferred for {}; retry retained", file.id)
        }
        return true
    }
}

@Component
class GeneratedPdfDeletionWorker(private val deletion: GeneratedPdfDeletion, private val files: GeneratedPdfFiles) {
    @Volatile private var ready = false
    @EventListener(ApplicationReadyEvent::class) fun ready() { ready = true }
    @Scheduled(fixedDelay = 60_000, scheduler = "generatedPdfTaskScheduler")
    fun poll() {
        if (!ready) return
        files.queueOrphans()
        repeat(50) { if (!deletion.next()) return }
    }
}
