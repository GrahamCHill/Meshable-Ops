package com.starter.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.*
import com.starter.repository.postgres.StorageDocumentRepository
import com.starter.service.*
import com.starter.service.storage.StorageObjectDownload
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Executors

@EnabledIfEnvironmentVariable(named = "DOCUMENT_TEST_JDBC_URL", matches = ".+")
class DocumentProcessingIntegrationTest {
    private val root = DriverManagerDataSource(System.getenv("DOCUMENT_TEST_JDBC_URL"), "postgres", "test-only-history-ocr")
    private val schema = "document_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var queue: DocumentProcessingQueue
    private lateinit var manager: DataSourceTransactionManager
    private val id = UUID.randomUUID()
    private val folder = Item()
    private val endpoint = AiEndpoint(AiProvider.LOCAL, "", "local")

    @BeforeEach fun setup() {
        JdbcTemplate(root).execute("CREATE EXTENSION IF NOT EXISTS vector")
        JdbcTemplate(root).execute("CREATE SCHEMA $schema")
        val separator = if (System.getenv("DOCUMENT_TEST_JDBC_URL").contains("?")) "&" else "?"
        val source = DriverManagerDataSource(System.getenv("DOCUMENT_TEST_JDBC_URL") + separator + "currentSchema=$schema,public", "postgres", "test-only-history-ocr")
        jdbc = JdbcTemplate(source)
        manager = DataSourceTransactionManager(source)
        jdbc.execute("CREATE TABLE storage_connections(id UUID PRIMARY KEY, enabled BOOLEAN)")
        jdbc.execute("CREATE TABLE items(id UUID PRIMARY KEY, accessible BOOLEAN, storage_connection_id UUID)")
        jdbc.execute("CREATE TABLE storage_documents(id UUID PRIMARY KEY, source_checksum TEXT, processing_status TEXT, embedding_model TEXT, updated_at TIMESTAMPTZ)")
        jdbc.execute("""CREATE TABLE document_processing_jobs(document_id UUID PRIMARY KEY REFERENCES storage_documents(id) ON DELETE CASCADE,
            source_checksum TEXT, embedding_model TEXT, state TEXT DEFAULT 'QUEUED', attempts INTEGER DEFAULT 0,
            claim_token UUID, lease_until TIMESTAMPTZ, next_attempt_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW())""")
        jdbc.execute("""CREATE TABLE document_chunks(id UUID PRIMARY KEY, document_id UUID REFERENCES storage_documents(id) ON DELETE CASCADE,
            chunk_index INTEGER, content TEXT, embedding vector(1536), source_checksum TEXT, embedding_model TEXT, page_number INTEGER)""")
        jdbc.execute("""CREATE TABLE document_pages(document_id UUID REFERENCES storage_documents(id) ON DELETE CASCADE,
            page_index INTEGER, page_number INTEGER, content TEXT, metadata JSONB, PRIMARY KEY(document_id,page_index))""")
        jdbc.execute("CREATE TABLE document_vectors(document_id UUID PRIMARY KEY, payload JSONB DEFAULT '{}')")
        jdbc.update("INSERT INTO items VALUES (?, TRUE, NULL)", folder.id)
        jdbc.update("INSERT INTO storage_documents(id,source_checksum,processing_status) VALUES (?, 'source', 'CONTENT_QUEUED')", id)
        queue = DocumentProcessingQueue(jdbc)
        queue.enqueue(id, "source", "model")
    }

    @AfterEach fun cleanup() { JdbcTemplate(root).execute("DROP SCHEMA $schema CASCADE") }

    @Test fun `concurrent claimers cannot both own the same job`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val claims = pool.invokeAll(List(2) { java.util.concurrent.Callable { queue.claim() } }).map { it.get() }
            assertThat(claims.filterNotNull()).hasSize(1)
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired leases recover after restart and retry budget terminates`() {
        val first = queue.claim()!!
        jdbc.update("UPDATE document_processing_jobs SET lease_until=NOW()-INTERVAL '1 second'")
        val second = queue.claim()!!
        assertThat(second.token).isNotEqualTo(first.token)
        assertThat(second.attempts).isEqualTo(2)
        TransactionTemplate(manager).executeWithoutResult { assertThat(queue.owns(first)).isFalse() }
        queue.failed(second)
        jdbc.update("UPDATE document_processing_jobs SET next_attempt_at=NOW()-INTERVAL '1 second'")
        val last = queue.claim()!!
        queue.failed(last)
        assertThat(queue.claim()).isNull()
        assertThat(jdbc.queryForObject("SELECT state FROM document_processing_jobs", String::class.java)).isEqualTo("FAILED")
        assertThat(jdbc.queryForObject("SELECT processing_status FROM storage_documents", String::class.java)).isEqualTo("CONTENT_FAILED")
    }

    private fun worker(): DocumentProcessingWorker {
        val documents = mock(StorageDocumentRepository::class.java)
        val storage = mock(FolderStorageService::class.java)
        val text = mock(DocumentTextService::class.java)
        val embeddings = mock(EmbeddingService::class.java)
        val settings = mock(AiSettingsService::class.java)
        val document = StorageDocument(id=id, item=folder, fileName="scan.png", storagePath="scan.png", sourceChecksum="source")
        `when`(documents.findForProcessing(id)).thenReturn(document)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, true, endpoint, endpoint))
        `when`(embeddings.fingerprint(endpoint)).thenReturn("model")
        `when`(storage.downloadForIndex(folder,"scan.png",52428800)).thenReturn(StorageObjectDownload("scan.png","image/png",byteArrayOf(1)))
        `when`(text.extract("scan.png",byteArrayOf(1))).thenReturn(ExtractedDocumentText("Recognized",false,listOf(ExtractedPage(9,"Recognized","OCR",97.0)),"adapter-v1"))
        `when`(embeddings.embed(anyList(),eq(endpoint) ?: endpoint)).thenReturn(listOf(List(1536){0.1f}))
        val vectors = VectorService(jdbc,jacksonObjectMapper(),embeddings)
        return DocumentProcessingWorker(queue,documents,storage,text,embeddings,settings,vectors,jdbc,jacksonObjectMapper(),manager,52428800)
    }

    @Test fun `successful worker publishes page provenance and completion atomically`() {
        worker().process(queue.claim()!!)
        assertThat(jdbc.queryForObject("SELECT page_number FROM document_chunks", Int::class.java)).isEqualTo(9)
        assertThat(jdbc.queryForObject("SELECT metadata->>'engine' FROM document_pages",String::class.java)).isEqualTo("adapter-v1")
        assertThat(jdbc.queryForObject("SELECT state FROM document_processing_jobs",String::class.java)).isEqualTo("DONE")
        assertThat(jdbc.queryForObject("SELECT processing_status FROM storage_documents",String::class.java)).isEqualTo("CONTENT_INDEXED")
    }

    @Test fun `publication failure rolls back chunks pages and completion together`() {
        jdbc.execute("ALTER TABLE document_pages ADD CONSTRAINT reject_page CHECK (content <> 'Recognized')")
        val job = queue.claim()!!
        assertThatThrownBy { worker().process(job) }.isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_pages", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("SELECT state FROM document_processing_jobs", String::class.java)).isEqualTo("RUNNING")
        queue.failed(job)
        assertThat(jdbc.queryForObject("SELECT state FROM document_processing_jobs", String::class.java)).isEqualTo("QUEUED")
    }

    @Test fun `requeued sources fence old claims and deletion removes jobs`() {
        val old = queue.claim()!!
        queue.enqueue(id,"new-source","model")
        TransactionTemplate(manager).executeWithoutResult { assertThat(queue.owns(old)).isFalse() }
        assertThat(queue.heartbeat(old)).isFalse()
        queue.failed(old)
        assertThat(jdbc.queryForObject("SELECT state FROM document_processing_jobs",String::class.java)).isEqualTo("QUEUED")
        jdbc.update("DELETE FROM storage_documents WHERE id=?",id)
        assertThat(queue.claim()).isNull()
    }
}
