package com.starter.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import com.starter.service.pdfstorage.PdfStorageDestination
import com.starter.service.pdfstorage.PdfStorageStatus

enum class PdfCleanupSchedule {
    NEVER, DAILY, WEEKLY;
    fun next(now: Instant): Instant? = when (this) {
        NEVER -> null
        DAILY -> now.plus(1, ChronoUnit.DAYS)
        WEEKLY -> now.plus(7, ChronoUnit.DAYS)
    }
}

data class PdfCleanupInput(val schedule: PdfCleanupSchedule = PdfCleanupSchedule.NEVER, val retentionDays: Int = 30,
    val destination: PdfStorageDestination = PdfStorageDestination.AUTO, val connectionId: UUID? = null)
data class PdfCleanupCounts(val reports: Long = 0, val documents: Long = 0, val bytes: Long = 0)
data class PdfCleanupSettings(
    val schedule: PdfCleanupSchedule = PdfCleanupSchedule.NEVER, val retentionDays: Int = 30,
    val nextRunAt: Instant? = null, val lastRunAt: Instant? = null,
    val lastResult: PdfCleanupCounts = PdfCleanupCounts(), val lastStatus: String? = null,
    val destination: PdfStorageDestination = PdfStorageDestination.AUTO, val connectionId: UUID? = null,
    val lastStorageStatus: String? = null, val lastStorageAt: Instant? = null,
)
data class PdfCleanupView(val settings: PdfCleanupSettings, val eligible: PdfCleanupCounts, val storage: PdfStorageStatus? = null)

/** Queries name only disposable report exports and explicitly rebuildable template PDF caches. */
@Repository
class GeneratedPdfCleanupStore(private val jdbc: JdbcTemplate) {
    fun settings(lock: Boolean = false): PdfCleanupSettings = jdbc.queryForObject(
        "SELECT * FROM generated_pdf_cleanup_settings WHERE id = 1${if (lock) " FOR UPDATE" else ""}",
        { row, _ -> PdfCleanupSettings(
            PdfCleanupSchedule.valueOf(row.getString("schedule")), row.getInt("retention_days"),
            row.getTimestamp("next_run_at")?.toInstant(), row.getTimestamp("last_run_at")?.toInstant(),
            PdfCleanupCounts(row.getLong("last_reports"), row.getLong("last_documents"), row.getLong("last_bytes")),
            row.getString("last_status"),
            PdfStorageDestination.valueOf(row.getString("destination")), row.getObject("connection_id", UUID::class.java),
            row.getString("last_storage_status"), row.getTimestamp("last_storage_at")?.toInstant(),
        ) },
    )!!

    fun save(input: PdfCleanupInput, next: Instant?) {
        jdbc.update("UPDATE generated_pdf_cleanup_settings SET schedule = ?, retention_days = ?, next_run_at = ?, destination = ?, connection_id = ? WHERE id = 1",
            input.schedule.name, input.retentionDays, next?.let(Timestamp::from), input.destination.name, input.connectionId)
    }

    fun eligible(cutoff: Instant): PdfCleanupCounts = jdbc.queryForObject("""
        SELECT
            (SELECT count(*) FROM workspace_project_reports WHERE created_at < ?) AS reports,
            (SELECT count(*) FROM generated_documents d WHERE $DOCUMENT_PREDICATE) AS documents,
            (SELECT coalesce(sum(octet_length(pdf)), 0) FROM workspace_project_reports WHERE created_at < ?) +
            (SELECT coalesce(sum(octet_length(pdf_bytes)), 0) FROM generated_documents d WHERE $DOCUMENT_PREDICATE) +
            (SELECT coalesce(sum(f.size_bytes),0) FROM generated_pdf_files f WHERE f.state='ACTIVE' AND
                ((f.kind='REPORT' AND EXISTS(SELECT 1 FROM workspace_project_reports r WHERE r.id=f.id AND r.created_at < ?)) OR
                 (f.kind='DOCUMENT' AND EXISTS(SELECT 1 FROM generated_documents d WHERE d.id=f.id AND d.created_at < ? AND d.pdf_regenerable=true)))) AS bytes
        """.trimIndent(), { row, _ -> PdfCleanupCounts(row.getLong("reports"), row.getLong("documents"), row.getLong("bytes")) },
        Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff), Timestamp.from(cutoff))!!

    fun clear(cutoff: Instant): PdfCleanupCounts {
        val documents = jdbc.queryForObject("""SELECT count(*) AS count,coalesce(sum(octet_length(pdf_bytes)),0) AS bytes
            FROM generated_documents d WHERE $DOCUMENT_PREDICATE""",
            { row, _ -> row.getLong("count") to row.getLong("bytes") }, Timestamp.from(cutoff))!!
        // Remote deletion is queued in the same database transaction as removing its source reference.
        // A rollback never leaves a retained report pointing at a deleted remote file.
        val externalBytes = jdbc.queryForObject("""WITH queued AS (
            UPDATE generated_pdf_files f SET state='DELETE_PENDING',next_delete_at=NOW() WHERE state='ACTIVE' AND
              ((kind='REPORT' AND EXISTS(SELECT 1 FROM workspace_project_reports r WHERE r.id=f.id AND r.created_at < ?)) OR
               (kind='DOCUMENT' AND EXISTS(SELECT 1 FROM generated_documents d WHERE d.id=f.id AND d.created_at < ? AND d.pdf_regenerable=true)))
            RETURNING size_bytes
            ) SELECT coalesce(sum(size_bytes),0) FROM queued""", Long::class.java, Timestamp.from(cutoff), Timestamp.from(cutoff))!!
        val reports = jdbc.queryForObject("""
            WITH removed AS (
                DELETE FROM workspace_project_reports WHERE created_at < ? RETURNING octet_length(pdf) AS bytes
            ) SELECT count(*) AS count, coalesce(sum(bytes), 0) AS bytes FROM removed
            """.trimIndent(), { row, _ -> row.getLong("count") to row.getLong("bytes") }, Timestamp.from(cutoff))!!
        jdbc.update("UPDATE generated_documents SET pdf_bytes=decode('','hex') WHERE created_at < ? AND pdf_regenerable=true AND octet_length(pdf_bytes)>0", Timestamp.from(cutoff))
        return PdfCleanupCounts(reports.first, documents.first, reports.second + documents.second + externalBytes)
    }

    fun record(now: Instant, next: Instant?, result: PdfCleanupCounts) {
        jdbc.update("""UPDATE generated_pdf_cleanup_settings SET last_run_at = ?, next_run_at = ?, last_reports = ?,
            last_documents = ?, last_bytes = ?, last_status = 'OK' WHERE id = 1""",
            Timestamp.from(now), next?.let(Timestamp::from), result.reports, result.documents, result.bytes)
    }

    fun failure(now: Instant) {
        // A concurrent successful run or settings edit takes precedence over retrying an older failed job.
        jdbc.update("""UPDATE generated_pdf_cleanup_settings SET last_status = 'Cleanup failed. Check the server logs.',
            next_run_at = ? WHERE id = 1 AND schedule <> 'NEVER' AND next_run_at <= ?""",
            Timestamp.from(now.plusSeconds(300)), Timestamp.from(now))
    }

    companion object {
        // Older/unclassified documents and billing PDFs default to protected. Never infer provenance from a filename.
        private const val DOCUMENT_PREDICATE = "d.created_at < ? AND d.pdf_regenerable = true AND (octet_length(d.pdf_bytes) > 0 OR EXISTS(SELECT 1 FROM generated_pdf_files f WHERE f.id=d.id AND f.state='ACTIVE' AND f.kind='DOCUMENT'))"
    }
}
