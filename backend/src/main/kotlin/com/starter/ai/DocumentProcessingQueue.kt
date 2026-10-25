package com.starter.ai

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

data class DocumentJob(val documentId: UUID, val checksum: String, val model: String, val token: UUID, val attempts: Int)

/** Durable PostgreSQL queue. Re-enqueueing fences any in-flight result for an older source. */
@Service
class DocumentProcessingQueue(private val jdbc: JdbcTemplate) {
    fun enqueue(documentId: UUID, checksum: String, model: String) {
        jdbc.update("""INSERT INTO document_processing_jobs(document_id, source_checksum, embedding_model)
            VALUES (?, ?, ?) ON CONFLICT(document_id) DO UPDATE SET source_checksum=EXCLUDED.source_checksum,
            embedding_model=EXCLUDED.embedding_model, state='QUEUED', attempts=0, claim_token=NULL,
            next_attempt_at=NOW(), lease_until=NULL, updated_at=NOW()""", documentId, checksum, model)
    }

    fun cancel(documentId: UUID) {
        jdbc.update("DELETE FROM document_processing_jobs WHERE document_id=?", documentId)
    }

    fun claim(): DocumentJob? {
        jdbc.update("""UPDATE document_processing_jobs SET state='FAILED', updated_at=NOW()
            WHERE state='RUNNING' AND lease_until < NOW() AND attempts >= 3""")
        val token = UUID.randomUUID()
        return jdbc.query("""WITH candidate AS (
            SELECT document_id FROM document_processing_jobs WHERE attempts < 3
              AND ((state='QUEUED' AND next_attempt_at <= NOW()) OR (state='RUNNING' AND lease_until < NOW()))
            ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1
            ) UPDATE document_processing_jobs j SET state='RUNNING', attempts=attempts+1,
                claim_token=?, lease_until=NOW()+INTERVAL '30 minutes', updated_at=NOW()
            FROM candidate c WHERE j.document_id=c.document_id
            RETURNING j.document_id, j.source_checksum, j.embedding_model, j.attempts""",
            { rs, _ -> DocumentJob(rs.getObject("document_id", UUID::class.java), rs.getString("source_checksum"),
                rs.getString("embedding_model"), token, rs.getInt("attempts")) }, token).firstOrNull()
    }

    fun owns(job: DocumentJob): Boolean = jdbc.query(
        "SELECT claim_token FROM document_processing_jobs WHERE document_id=? AND state='RUNNING' FOR UPDATE",
        { rs, _ -> rs.getObject("claim_token", UUID::class.java) }, job.documentId).singleOrNull() == job.token

    fun complete(job: DocumentJob) {
        jdbc.update("""UPDATE document_processing_jobs SET state='DONE', lease_until=NULL, updated_at=NOW()
            WHERE document_id=? AND claim_token=?""", job.documentId, job.token)
    }

    @Transactional
    fun failed(job: DocumentJob) {
        // Match browsing's document -> job lock order, and never overwrite a newer job's status.
        jdbc.query("SELECT id FROM storage_documents WHERE id=? FOR UPDATE", { rs, _ -> rs.getObject(1) }, job.documentId)
        val changed = jdbc.update("""UPDATE document_processing_jobs SET state=?, lease_until=NULL,
            next_attempt_at=NOW()+INTERVAL '1 minute' * ?, updated_at=NOW()
            WHERE document_id=? AND claim_token=? AND state='RUNNING'""",
            if (job.attempts >= 3) "FAILED" else "QUEUED", job.attempts * job.attempts, job.documentId, job.token)
        if (changed == 1) jdbc.update("UPDATE storage_documents SET processing_status=?, updated_at=NOW() WHERE id=? AND source_checksum=?",
            if (job.attempts >= 3) "CONTENT_FAILED" else "CONTENT_RETRY", job.documentId, job.checksum)
    }
}
