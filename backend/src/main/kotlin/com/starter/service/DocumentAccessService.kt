package com.starter.service

import com.starter.dto.DocumentAccessPolicy
import com.starter.dto.DocumentAccessRequest
import com.starter.exception.BadRequestException
import com.starter.model.postgres.DocumentAccessSettings
import com.starter.repository.postgres.DocumentAccessSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class DocumentAccessService(private val repository: DocumentAccessSettingsRepository, private val audit: AuditLogService) {
    // A fresh transaction also makes the final delivery check observe policy changes during PDF processing.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun current(): DocumentAccessPolicy = repository.findById(1).orElse(null)?.let {
        DocumentAccessPolicy(it.pdfPreviewEnabled, it.downloadMode, it.watermarkText)
    } ?: DocumentAccessPolicy()

    @Transactional
    fun save(request: DocumentAccessRequest, actor: String): DocumentAccessPolicy {
        val text = request.watermarkText.trim()
        if (text.isEmpty()) throw BadRequestException("Enter watermark text")
        val settings = repository.findById(1).orElseGet { DocumentAccessSettings() }
        settings.pdfPreviewEnabled = request.pdfPreviewEnabled
        settings.downloadMode = request.downloadMode
        settings.watermarkText = text
        repository.saveAndFlush(settings)
        audit.record(actingEmail = actor, action = "DOCUMENT_ACCESS_UPDATED", resourceType = "SETTINGS", resourceId = "documents")
        return DocumentAccessPolicy(settings.pdfPreviewEnabled, settings.downloadMode, settings.watermarkText)
    }
}
