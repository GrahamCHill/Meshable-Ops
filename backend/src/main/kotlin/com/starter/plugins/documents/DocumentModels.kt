package com.starter.plugins.documents

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "document_templates")
class DocumentTemplateRecord(@Id val id: UUID = UUID.randomUUID(), var name: String = "",
    var category: String = "", var published: Boolean = false,
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var templateJson: String = "{}",
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var fieldRulesJson: String = "[]",
    val createdBy: UUID = UUID.randomUUID(), val createdAt: Instant = Instant.now(), var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0) { override fun toString() = "DocumentTemplateRecord(id=$id)" }

@Entity @Table(name = "generated_documents", indexes = [Index(columnList = "owner_id,created_at")])
class GeneratedDocument(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(),
    val templateId: UUID = UUID.randomUUID(), val templateRevision: Long = 0, val templateName: String = "",
    val name: String = "", val filename: String = "document.pdf", val pageCount: Int = 0, val byteSize: Int = 0,
    val createdAt: Instant = Instant.now(),
    @get:JsonIgnore @Column(columnDefinition = "TEXT") val valuesJson: String = "{}",
    @get:JsonIgnore @Column(columnDefinition = "TEXT") val templateSnapshot: String = "{}",
    @get:JsonIgnore @Column(columnDefinition = "bytea") val pdfBytes: ByteArray = byteArrayOf()) {
    override fun toString() = "GeneratedDocument(id=$id)"
}
interface DocumentTemplateRepository : JpaRepository<DocumentTemplateRecord, UUID> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("SELECT t FROM DocumentTemplateRecord t WHERE t.id = :id AND (t.published = true OR :admin = true)")
    fun visibleById(id: UUID, admin: Boolean): java.util.Optional<DocumentTemplateRecord>
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): List<DocumentTemplateRecord>
    fun findByPublishedTrueOrderByUpdatedAtDesc(pageable: Pageable): List<DocumentTemplateRecord>
}
interface GeneratedDocumentRepository : JpaRepository<GeneratedDocument, UUID> {
    @Query("SELECT new com.starter.plugins.documents.GeneratedDocumentView(d.id, d.ownerId, d.templateId, d.templateRevision, d.templateName, d.name, d.filename, d.pageCount, d.byteSize, d.createdAt) FROM GeneratedDocument d WHERE d.id IN :ids")
    fun workflowMetadata(ids: List<UUID>): List<GeneratedDocumentView>
    @Query("SELECT new com.starter.plugins.documents.GeneratedDocumentView(d.id, d.ownerId, d.templateId, d.templateRevision, d.templateName, d.name, d.filename, d.pageCount, d.byteSize, d.createdAt) FROM GeneratedDocument d WHERE (:admin = true OR d.ownerId = :owner) ORDER BY d.createdAt DESC")
    fun visible(owner: UUID, admin: Boolean, pageable: Pageable): List<GeneratedDocumentView>
    @Query("SELECT count(d) FROM GeneratedDocument d WHERE d.id IN :ids AND (:admin = true OR d.ownerId = :owner)")
    fun visibleCount(ids: List<UUID>, owner: UUID, admin: Boolean): Long
}
