package com.starter.service.storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.access.StorageAccess
import com.starter.ai.DocumentFingerprints
import com.starter.ai.DocumentIndexService
import com.starter.model.postgres.*
import com.starter.repository.postgres.*
import com.starter.service.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.*
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/** Opt-in service + real JPA/PostgreSQL + SDK/loopback HTTP + JSON benchmark. Excludes browser/auth/WAN. */
@EnabledIfEnvironmentVariable(named = "STORAGE_WALK_BENCHMARK", matches = "true")
@EnabledIfEnvironmentVariable(named = "DOCUMENT_TEST_JDBC_URL", matches = ".+")
class StorageWalkingBenchmarkTest {
    @Test fun `measure cold warm and concurrent listings above 2000 files`() {
        val schema = "storage_bench_${UUID.randomUUID().toString().replace("-", "")}"
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = System.getenv("DOCUMENT_TEST_JDBC_URL")
            maximumPoolSize = 10
        }).use { source ->
            val jdbc = JdbcTemplate(source)
            jdbc.execute("CREATE SCHEMA $schema")
            val beans = AnnotationConfigApplicationContext()
            beans.beanFactory.registerSingleton("secretEncryption", com.starter.security.testEncryption())
            beans.register(com.starter.security.EncryptedSecretConverter::class.java)
            beans.refresh()
            try { beans.use { context ->
                Configuration().apply {
                    properties["hibernate.resource.beans.container"] = org.springframework.orm.hibernate5.SpringBeanContainer(context.beanFactory)
                    properties["hibernate.connection.datasource"] = source
                }.addAnnotatedClass(User::class.java).addAnnotatedClass(Item::class.java)
                    .addAnnotatedClass(StorageConnection::class.java).addAnnotatedClass(StorageDocument::class.java)
                    .setProperty("hibernate.default_schema", schema)
                    .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                    .buildSessionFactory().use { factory ->
                        val em = SharedEntityManagerCreator.createSharedEntityManager(factory)
                        val tx = TransactionTemplate(JpaTransactionManager(factory))
                        val repositories = JpaRepositoryFactory(em)
                        val items = repositories.getRepository(ItemRepository::class.java)
                        val documents = repositories.getRepository(StorageDocumentRepository::class.java)
                        val folder = tx.execute {
                            val user = em.merge(User(email = "benchmark@test.local", username = "benchmark"))
                            em.merge(Item(user = user, title = "Benchmark"))
                        }!!
                        val root = "folders/${folder.id}/"
                        val entries = List(2500) { StorageEntry("v1-file-${it.toString().padStart(5, '0')}.txt",
                            "v1-file-${it.toString().padStart(5, '0')}.txt", "OBJECT", 12, lastModified = "2026-10-28T00:00:00Z") }
                        jdbc.batchUpdate("""INSERT INTO $schema.storage_documents
                            (id, item_id, storage_path, file_name, size_bytes, processing_status, source_checksum, vectorized_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, 12, 'METADATA_ONLY', ?, NOW(), NOW(), NOW())""", entries.map { entry ->
                            arrayOf(UUID.randomUUID(), folder.id, entry.path, entry.name, DocumentFingerprints.metadata(entry))
                        })
                        StorageListingFixture().use { fixture -> StorageListingCache().useCache { cache ->
                            fixture.rootSegment = folder.id.toString()
                            val settings = mock(AdminSettingsService::class.java)
                            `when`(settings.currentEntity()).thenReturn(fixture.settings)
                            val acl = mock(AccessControlService::class.java)
                            `when`(acl.storage()).thenReturn(StorageAccess())
                            val storage = FolderStorageService(settings, acl, cache)
                            val index = mock(DocumentIndexService::class.java)
                            val service = ItemService(items, documents, mock(UserRepository::class.java), mock(EventRepository::class.java),
                                mock(VectorService::class.java), storage, mock(AuditLogService::class.java), index, acl, mock(StorageBrowseJobs::class.java))
                            val json = jacksonObjectMapper()
                            fun roots() { tx.executeWithoutResult { json.writeValueAsBytes(service.listShared(0, 20)) } }
                            fun browse() { tx.executeWithoutResult { json.writeValueAsBytes(service.browse(folder.id, "")) } }
                            println("STORAGE_BENCH runtime=${System.getProperty("java.runtime.version")} processors=${Runtime.getRuntime().availableProcessors()} maxHeapMiB=${Runtime.getRuntime().maxMemory() / 1048576}")
                            println("STORAGE_BENCH cold roots_ms=${measureNanoTime { roots() } / 1e6} browse_ms=${measureNanoTime { browse() } / 1e6} cloud_requests=${fixture.requests.size}")
                            assertThat(storage.folderCounts(listOf(root)).documentCounts[root]).isEqualTo(2501)
                            repeat(30) { roots(); browse() }
                            fun measure(label: String, n: Int = 200, enforce: Boolean = true, action: () -> Unit) {
                                val samples = List(n) { measureNanoTime { action() } / 1e6 }.sorted()
                                report(label, samples, enforce)
                            }
                            measure("warm_roots", action = ::roots)
                            measure("warm_browse", action = ::browse)
                            val pool = Executors.newFixedThreadPool(4)
                            try {
                                val samples = pool.invokeAll(List(200) { Callable { measureNanoTime { browse() } / 1e6 } })
                                    .map { it.get() }.sorted()
                                report("concurrent_4_browse", samples)
                            } finally { pool.shutdownNow() }
                            measure("uncached_browse", n = 100, enforce = false) { cache.invalidate(); browse() }
                            verifyNoInteractions(index)
                        } }
                    }
            } } finally { jdbc.execute("DROP SCHEMA $schema CASCADE") }
        }
    }

    private fun report(label: String, samples: List<Double>, enforce: Boolean = true) {
        fun p(fraction: Double) = samples[ceil(samples.size * fraction).toInt() - 1]
        println("STORAGE_BENCH $label n=${samples.size} p50_ms=${p(.50)} p95_ms=${p(.95)} p99_ms=${p(.99)} max_ms=${samples.last()}")
        if (enforce) assertThat(p(.99)).describedAs("$label warm p99 target (ms)").isLessThan(200.0)
    }
}
