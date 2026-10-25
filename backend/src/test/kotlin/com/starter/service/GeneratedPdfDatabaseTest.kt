package com.starter.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

/** A unique disposable schema keeps these tests isolated from both each other and application tables. */
abstract class GeneratedPdfDatabaseTest {
    protected lateinit var source: SingleConnectionDataSource
    protected lateinit var jdbc: JdbcTemplate
    protected lateinit var store: GeneratedPdfCleanupStore
    private val schema = "pdf_cleanup_test_${UUID.randomUUID().toString().replace("-", "")}"
    protected val cutoff = Instant.parse("2026-09-25T10:00:00Z")

    @BeforeEach fun setup() {
        source = SingleConnectionDataSource(DriverManager.getConnection(System.getenv("PDF_CLEANUP_TEST_JDBC_URL")), true)
        jdbc = JdbcTemplate(source)
        jdbc.execute("CREATE SCHEMA $schema")
        jdbc.execute("SET search_path TO $schema")
        jdbc.execute("CREATE TABLE workspace_project_reports (id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, pdf BYTEA NOT NULL)")
        jdbc.execute("""CREATE TABLE generated_documents (id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, pdf_bytes BYTEA NOT NULL,
            pdf_regenerable BOOLEAN NOT NULL DEFAULT FALSE, template_snapshot TEXT NOT NULL DEFAULT '{}', values_json TEXT NOT NULL DEFAULT '{}')""")
        jdbc.execute("CREATE TABLE source_records (id UUID PRIMARY KEY, markdown TEXT NOT NULL)")
        jdbc.execute("CREATE TABLE workflow_references (document_id UUID REFERENCES generated_documents(id))")
        jdbc.execute(ClassPathResource("schema.sql").inputStream.bufferedReader().use { it.readText() }
            .substringAfter("-- Generated PDF locations").substringAfter('\n').substringBefore("-- Section"))
        store = GeneratedPdfCleanupStore(jdbc)
    }
    @AfterEach fun cleanup() {
        if (::jdbc.isInitialized) jdbc.execute("DROP SCHEMA $schema CASCADE")
        if (::source.isInitialized) source.destroy()
    }
}
