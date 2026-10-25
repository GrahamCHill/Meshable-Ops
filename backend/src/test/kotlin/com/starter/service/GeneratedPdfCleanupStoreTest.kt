package com.starter.service

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.aop.framework.ProxyFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Run against a disposable PostgreSQL database, never an application database. Each test owns a separate schema. */
@EnabledIfEnvironmentVariable(named = "PDF_CLEANUP_TEST_JDBC_URL", matches = ".+")
class GeneratedPdfCleanupStoreTest : GeneratedPdfDatabaseTest() {

    @Test fun `cleanup affects only expired exports and tagged caches while retaining source snapshots and references`() {
        fun report(at: Instant, bytes: String) = jdbc.update("INSERT INTO workspace_project_reports VALUES (?, ?, ?)", UUID.randomUUID(), Timestamp.from(at), bytes.toByteArray())
        report(cutoff.minusSeconds(1), "expired report")
        report(cutoff, "boundary report")
        report(cutoff.plusSeconds(1), "recent report")
        fun document(at: Instant, bytes: String, rebuildable: Boolean): UUID {
            val id = UUID.randomUUID()
            jdbc.update("INSERT INTO generated_documents VALUES (?, ?, ?, ?, ?, ?)", id, Timestamp.from(at), bytes.toByteArray(), rebuildable, "saved template", "saved values")
            return id
        }
        val expired = document(cutoff.minusSeconds(1), "expired template pdf", true)
        val billing = document(cutoff.minusSeconds(1), "protected billing pdf", false)
        val legacy = document(cutoff.minusSeconds(1), "unclassified pdf", false)
        document(cutoff, "boundary pdf", true)
        document(cutoff.plusSeconds(1), "recent pdf", true)
        document(cutoff.minusSeconds(1), "", true)
        jdbc.update("INSERT INTO source_records VALUES (?, ?)", UUID.randomUUID(), "# Original Markdown")
        jdbc.update("INSERT INTO workflow_references VALUES (?)", expired)

        val expected = PdfCleanupCounts(1, 1, ("expired report".length + "expired template pdf".length).toLong())
        assertThat(store.settings().schedule).isEqualTo(PdfCleanupSchedule.NEVER)
        assertThat(store.eligible(cutoff)).isEqualTo(expected)
        assertThat(store.clear(cutoff)).isEqualTo(expected)
        assertThat(store.clear(cutoff)).isEqualTo(PdfCleanupCounts())
        assertThat(store.eligible(cutoff)).isEqualTo(PdfCleanupCounts())
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspace_project_reports", Long::class.java)).isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT octet_length(pdf_bytes) FROM generated_documents WHERE id = ?", Int::class.java, expired)).isZero()
        assertThat(jdbc.queryForObject("SELECT template_snapshot || ':' || values_json FROM generated_documents WHERE id = ?", String::class.java, expired)).isEqualTo("saved template:saved values")
        for (id in listOf(billing, legacy)) assertThat(jdbc.queryForObject("SELECT octet_length(pdf_bytes) FROM generated_documents WHERE id = ?", Int::class.java, id)).isPositive()
        assertThat(jdbc.queryForObject("SELECT markdown FROM source_records", String::class.java)).isEqualTo("# Original Markdown")
        assertThat(jdbc.queryForObject("SELECT document_id FROM workflow_references", UUID::class.java)).isEqualTo(expired)
    }

    @Test fun `settings survive reload and a failed run cannot restart a disabled schedule`() {
        val next = cutoff.plusSeconds(86400)
        store.save(PdfCleanupInput(PdfCleanupSchedule.DAILY, 7), next)
        store.record(cutoff, next, PdfCleanupCounts(1, 2, 300))
        assertThat(store.settings()).isEqualTo(PdfCleanupSettings(PdfCleanupSchedule.DAILY, 7, next, cutoff, PdfCleanupCounts(1, 2, 300), "OK"))
        store.failure(cutoff)
        assertThat(store.settings().nextRunAt).isEqualTo(next)
        assertThat(store.settings().lastStatus).isEqualTo("OK")
        store.save(PdfCleanupInput(PdfCleanupSchedule.DAILY, 7), cutoff.minusSeconds(1))
        store.failure(cutoff)
        assertThat(store.settings().nextRunAt).isEqualTo(cutoff.plusSeconds(300))
        store.save(PdfCleanupInput(), null)
        store.failure(cutoff)
        assertThat(store.settings().nextRunAt).isNull()
    }

    @Test fun `a failure rolls back both deletions and settings through the service transaction`() {
        val reportId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        jdbc.update("INSERT INTO workspace_project_reports VALUES (?, ?, ?)", reportId, Timestamp.from(cutoff.minusSeconds(1)), byteArrayOf(1))
        jdbc.update("INSERT INTO generated_documents (id, created_at, pdf_bytes, pdf_regenerable) VALUES (?, ?, ?, true)", documentId, Timestamp.from(cutoff.minusSeconds(1)), byteArrayOf(2))
        jdbc.update("""INSERT INTO generated_pdf_files(id,kind,provider,source,location_json,locator_json,size_bytes,checksum,state)
            VALUES (?,'REPORT','S3','DEFAULT','{}','{}',1,'test-checksum','ACTIVE')""",reportId)
        val audit = mock(AuditLogService::class.java)
        doThrow(IllegalStateException("test audit failure")).`when`(audit).record(null, "GENERATED_PDF_CLEANUP_MANUAL", "GENERATED_PDF_CLEANUP", "1 reports:1 template PDFs:3 bytes")
        val service = ProxyFactory(GeneratedPdfCleanupService(store, audit)).apply {
            addAdvice(TransactionInterceptor(DataSourceTransactionManager(source), AnnotationTransactionAttributeSource()))
        }.proxy as GeneratedPdfCleanupService
        assertThatThrownBy { service.clearExpired(now = cutoff.plusSeconds(30 * 86400L)) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspace_project_reports WHERE id = ?", Long::class.java, reportId)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT octet_length(pdf_bytes) FROM generated_documents WHERE id = ?", Int::class.java, documentId)).isEqualTo(1)
        assertThat(store.settings().lastRunAt).isNull()
        assertThat(jdbc.queryForObject("SELECT state FROM generated_pdf_files WHERE id=?",String::class.java,reportId)).isEqualTo("ACTIVE")
    }
}
