package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** Point this only at a disposable database; no running application services are required. */
@EnabledIfEnvironmentVariable(named = "EMBEDDING_TEST_DATABASE_URL", matches = ".+")
class EmbeddingDimensionMigrationTest {
    private val source = DriverManagerDataSource(System.getenv("EMBEDDING_TEST_DATABASE_URL"), "postgres", "embedding-test")
    private val jdbc = JdbcTemplate(source)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(source))
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val legacy = List(384) { if (it == 0) 1 else 0 }.joinToString(",", "[", "]")

    @BeforeEach fun setup() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector")
        jdbc.execute("DROP TABLE IF EXISTS item_vectors, document_vectors, document_chunks, storage_documents, plugin_kanban_boards, plugin_sample_records, unrelated_vectors")
        jdbc.execute("CREATE TABLE storage_documents (id uuid PRIMARY KEY, embedding_model text, vectorized_at timestamptz)")
        jdbc.update("INSERT INTO storage_documents VALUES (?, 'old-model', now())", id)
        for (table in listOf("item_vectors", "document_vectors", "document_chunks")) {
            jdbc.execute("CREATE TABLE $table (id uuid PRIMARY KEY, embedding vector(384) NOT NULL, embedding_model text, content text)")
            jdbc.execute("CREATE INDEX ${table}_hnsw ON $table USING hnsw (embedding vector_cosine_ops)")
            jdbc.update("INSERT INTO $table VALUES (?, CAST(? AS vector), 'old-model', 'Preserved contract renewal text')", id, legacy)
        }
        jdbc.execute("CREATE TABLE plugin_kanban_boards (id uuid PRIMARY KEY, name text, content_json text, search_text text, search_embedding vector(384))")
        jdbc.execute("CREATE INDEX kanban_hnsw ON plugin_kanban_boards USING hnsw (search_embedding vector_cosine_ops)")
        jdbc.update("INSERT INTO plugin_kanban_boards VALUES (?, 'Renewal board', '{}', '', CAST(? AS vector))", id, legacy)
        jdbc.execute("CREATE TABLE plugin_sample_records (id uuid PRIMARY KEY, search_text text, search_embedding vector(384))")
        jdbc.batchUpdate("INSERT INTO plugin_sample_records VALUES (?, 'Invoice approval', CAST(? AS vector))", (1..201).toList(), 200) { statement, number ->
            statement.setObject(1, UUID(0, number.toLong()))
            statement.setString(2, legacy)
        }
        jdbc.execute("CREATE TABLE unrelated_vectors (embedding vector(384))")
    }

    private fun migrate() = transaction.executeWithoutResult {
        EmbeddingDimensionMigration(jdbc).run(DefaultApplicationArguments())
    }

    @Test fun `upgrade preserves source text rebuilds local plugins and retains usable HNSW indexes`() {
        migrate()
        for (table in listOf("item_vectors", "document_vectors", "document_chunks")) {
            assertThat(jdbc.queryForObject("SELECT content FROM $table", String::class.java)).isEqualTo("Preserved contract renewal text")
            assertThat(jdbc.queryForObject("SELECT embedding IS NULL FROM $table", Boolean::class.java)).isTrue()
            assertThat(jdbc.queryForObject("SELECT embedding_model FROM $table", String::class.java)).isEqualTo("requires-reindex-1536")
            val vector = LocalTextVectors.embed("Contract renewal").joinToString(",", "[", "]")
            jdbc.update("UPDATE $table SET embedding = CAST(? AS vector), embedding_model = 'new-model'", vector)
            assertThat(jdbc.queryForObject("SELECT 1 - (embedding <=> CAST(? AS vector)) FROM $table", Double::class.java, vector)).isCloseTo(1.0, within(0.00001))
        }
        assertThat(jdbc.queryForObject("SELECT embedding_model IS NULL AND vectorized_at IS NULL FROM storage_documents", Boolean::class.java)).isTrue()
        val query = LocalTextVectors.embed("Renewal board {}").joinToString(",", "[", "]")
        assertThat(jdbc.queryForObject("SELECT 1 - (search_embedding <=> CAST(? AS vector)) FROM plugin_kanban_boards", Double::class.java, query)).isCloseTo(1.0, within(0.00001))
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plugin_sample_records WHERE vector_dims(search_embedding) = 1536", Int::class.java)).isEqualTo(201)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_index WHERE indisvalid AND indexrelid IN ('item_vectors_hnsw'::regclass, 'document_vectors_hnsw'::regclass, 'document_chunks_hnsw'::regclass, 'kanban_hnsw'::regclass)", Int::class.java)).isEqualTo(4)
        assertThat(jdbc.queryForObject("SELECT atttypmod FROM pg_attribute WHERE attrelid = 'unrelated_vectors'::regclass AND attname = 'embedding'", Int::class.java)).isEqualTo(384)
    }

    @Test fun `restarting after an upgrade preserves newly indexed vectors`() {
        migrate()
        val vector = LocalTextVectors.embed("New contract").joinToString(",", "[", "]")
        jdbc.update("UPDATE document_chunks SET embedding = CAST(? AS vector), embedding_model = 'new-model'", vector)
        jdbc.update("UPDATE storage_documents SET embedding_model = 'new-model', vectorized_at = now()")
        migrate()
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM document_chunks", String::class.java)).isEqualTo("new-model")
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM document_chunks", Int::class.java)).isEqualTo(1536)
        assertThat(jdbc.queryForObject("SELECT vectorized_at IS NOT NULL FROM storage_documents", Boolean::class.java)).isTrue()
    }

    @Test fun `fresh schema needs no migration and accepts the expanded vectors`() {
        jdbc.execute("DROP TABLE item_vectors, document_vectors, document_chunks, storage_documents, plugin_kanban_boards, plugin_sample_records")
        jdbc.execute("CREATE TABLE document_chunks (id uuid PRIMARY KEY, embedding vector(1536), embedding_model text)")
        jdbc.execute("CREATE INDEX document_chunks_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops)")
        val vector = LocalTextVectors.embed("New document").joinToString(",", "[", "]")
        jdbc.update("INSERT INTO document_chunks VALUES (?, CAST(? AS vector), 'new-model')", id, vector)
        migrate()
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM document_chunks", Int::class.java)).isEqualTo(1536)
    }
}
