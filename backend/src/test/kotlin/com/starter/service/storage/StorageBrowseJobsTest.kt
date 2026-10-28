package com.starter.service.storage

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "DOCUMENT_TEST_JDBC_URL", matches = ".+")
class StorageBrowseJobsTest {
    private lateinit var source: SingleConnectionDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var jobs: StorageBrowseJobs
    private val schema = "storage_browse_${UUID.randomUUID().toString().replace("-", "")}"
    private val itemId = UUID.randomUUID()

    @BeforeEach fun setup() {
        source = SingleConnectionDataSource(DriverManager.getConnection(System.getenv("DOCUMENT_TEST_JDBC_URL")), true)
        jdbc = JdbcTemplate(source)
        jdbc.execute("CREATE SCHEMA $schema")
        jdbc.execute("SET search_path TO $schema")
        jdbc.execute("CREATE TABLE items (id UUID PRIMARY KEY)")
        jdbc.update("INSERT INTO items VALUES (?)", itemId)
        val sql = ClassPathResource("schema.sql").inputStream.bufferedReader().use { it.readText() }
            .substringAfter("-- Browsing never waits").substringAfter('\n').substringBefore("-- Generated PDF locations")
        jdbc.execute(sql)
        jobs = StorageBrowseJobs(jdbc)
    }

    @AfterEach fun cleanup() {
        if (::jdbc.isInitialized) jdbc.execute("DROP SCHEMA $schema CASCADE")
        if (::source.isInitialized) source.destroy()
    }

    @Test fun `requests coalesce and concurrent workers cannot claim the same lease`() {
        repeat(10) { jobs.request(itemId, "nested/") }
        val job = jobs.claim()!!
        assertThat(job.revision).isEqualTo(10)
        assertThat(jobs.claim()).isNull()
        jobs.complete(job)
        assertThat(jobs.claim()).isNull()
    }

    @Test fun `requests during processing survive completion and failure retries after its delay`() {
        jobs.request(itemId, "")
        val first = jobs.claim()!!
        jobs.request(itemId, "")
        jobs.complete(first)
        val second = jobs.claim()!!
        assertThat(second.revision).isEqualTo(2)
        jobs.failed(second)
        assertThat(jobs.claim()).isNull()
        jdbc.update("UPDATE storage_browse_jobs SET next_attempt_at=NOW()-INTERVAL '1 second'")
        jobs.complete(jobs.claim()!!)
        assertThat(jobs.claim()).isNull()
    }

    @Test fun `a crashed lease is reclaimable and stale tokens cannot complete or fail newer work`() {
        jobs.request(itemId, "")
        val first = jobs.claim()!!
        jdbc.update("UPDATE storage_browse_jobs SET lease_until=NOW()-INTERVAL '1 second'")
        val second = jobs.claim()!!
        jobs.complete(first)
        jobs.failed(first)
        assertThat(jobs.claim()).isNull()
        jobs.complete(second)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM storage_browse_jobs", Long::class.java)).isZero()
    }

    @Test fun `deleting a shared root discards its pending work`() {
        jobs.request(itemId, "")
        jdbc.update("DELETE FROM items WHERE id=?", itemId)
        assertThat(jobs.claim()).isNull()
    }
}
