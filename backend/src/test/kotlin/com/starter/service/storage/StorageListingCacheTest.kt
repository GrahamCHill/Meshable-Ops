package com.starter.service.storage

import com.github.benmanes.caffeine.cache.Ticker
import com.starter.exception.NotFoundException
import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageConnection
import com.starter.service.AdminSettingsService
import com.starter.service.FolderStorageService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class StorageListingCacheTest {
    @Test fun `recursive walking reads managed namespace settings a constant number of times`() {
        StorageListingFixture().use { fixture -> StorageListingCache().useCache { cache ->
            val item = Item()
            fixture.rootSegment = item.id.toString()
            val settings = mock(AdminSettingsService::class.java)
            `when`(settings.currentEntity()).thenReturn(fixture.settings)
            val storage = FolderStorageService(settings, listings = cache)
            assertThat(storage.listDocuments(item)).hasSize(2501)
            verify(settings, atMost(4)).currentEntity()
            assertThat(fixture.requests).hasSize(3)
        } }
    }
    @Test fun `one paginated namespace walk supplies counts across different root pages without writes`() {
        StorageListingFixture().use { fixture -> StorageListingCache().useCache { cache ->
            assertThat(cache.inspect(fixture.settings, listOf("folders/a/")).documentCounts).containsEntry("folders/a/", 2501)
            assertThat(cache.inspect(fixture.settings, listOf("folders/b/", "folders/missing/")).documentCounts)
                .containsEntry("folders/b/", 1).containsEntry("folders/missing/", 0)
            assertThat(fixture.requests).hasSize(3)
            assertThat(fixture.writes.get()).isZero()
            val browse = cache.browse(fixture.settings, "folders/", "folders/a/", "")
            assertThat(browse.entries.count { it.type == "OBJECT" }).isEqualTo(2500)
            assertThat(browse.entries.filter { it.type == "FOLDER" }.map { it.name }).containsExactly("__generated_pdfs__", "empty", "nested")
            assertThat(fixture.requests).hasSize(6)
            assertThat(cache.browse(fixture.settings, "folders/", "folders/a/", "")).isSameAs(browse)
            assertThat(fixture.requests).hasSize(6)
        } }
    }

    @Test fun `concurrent cold requests share one cloud walk`() {
        StorageListingFixture().use { fixture -> StorageListingCache().useCache { cache ->
            val pool = Executors.newFixedThreadPool(8)
            try {
                val futures = (1..8).map { pool.submit<Int> { cache.inspect(fixture.settings, listOf("folders/a/")).documentCounts.getValue("folders/a/") } }
                futures.forEach { assertThat(it.get(20, TimeUnit.SECONDS)).isEqualTo(2501) }
                assertThat(fixture.requests).hasSize(3)
            } finally { pool.shutdownNow() }
        } }
    }

    @Test fun `expired freshness refreshes in background and hard expiry refuses failed storage`() {
        val now = AtomicLong()
        val tasks = java.util.ArrayDeque<Runnable>()
        val cache = StorageListingCache(Ticker { now.get() }, java.util.concurrent.Executor { tasks.add(it) })
        StorageListingFixture(1).use { fixture -> cache.useCache {
            val before = cache.browse(fixture.settings, "folders/", "folders/a/", "")
            fixture.version = 2
            now.set(TimeUnit.SECONDS.toNanos(6))
            assertThat(cache.browse(fixture.settings, "folders/", "folders/a/", "")).isSameAs(before)
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
            assertThat(cache.browse(fixture.settings, "folders/", "folders/a/", "").entries.map { it.name }).contains("v2-file-00000.txt")
            fixture.status = 403
            now.set(TimeUnit.SECONDS.toNanos(37))
            assertThatThrownBy { cache.browse(fixture.settings, "folders/", "folders/a/", "") }.isInstanceOf(Exception::class.java)
        } }
    }

    @Test fun `bucket credential and prefix edits get distinct cache entries`() {
        StorageListingFixture(1).use { fixture -> StorageListingCache().useCache { cache ->
            val settings = fixture.settings
            cache.browse(settings, "folders/", "folders/a/", "")
            settings.storageBucket = "other-bucket"
            cache.browse(settings, "folders/", "folders/a/", "")
            settings.storageSecretKey = "rotated"
            cache.browse(settings, "folders/", "folders/a/", "")
            cache.browse(settings, "folders/", "folders/b/", "")
            assertThat(fixture.requests).hasSize(4)
            cache.invalidate()
            cache.browse(settings, "folders/", "folders/b/", "")
            assertThat(fixture.requests).hasSize(5)
        } }
    }

    @Test fun `cached folder listing cannot bypass revocation or expose generated exports`() {
        StorageListingFixture(1).use { fixture -> StorageListingCache().useCache { cache ->
            val settings = fixture.settings
            val item = Item(storagePrefix = "folders/a/", storageConnection = StorageConnection(provider = "S3", bucket = "docs",
                endpoint = settings.storageEndpoint, accessKey = "fixture", secretKey = "fixture", basePath = "folders/"))
            val storage = FolderStorageService(mock(AdminSettingsService::class.java), listings = cache)
            assertThat(storage.browse(item, "").entries.map { it.name }).doesNotContain("__generated_pdfs__")
            item.accessible = false
            assertThatThrownBy { storage.browse(item, "") }.isInstanceOf(NotFoundException::class.java)
            item.accessible = true
            item.storageConnection!!.enabled = false
            assertThatThrownBy { storage.browse(item, "") }.isInstanceOf(NotFoundException::class.java)
            assertThat(fixture.requests).hasSize(1)
        } }
    }
}

internal inline fun <T> StorageListingCache.useCache(block: (StorageListingCache) -> T): T = try { block(this) } finally { close() }
