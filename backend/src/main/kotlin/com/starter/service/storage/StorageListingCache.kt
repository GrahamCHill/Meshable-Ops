package com.starter.service.storage

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import com.starter.model.postgres.AppSettings
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Cache provider data only. Callers must apply current visibility and path permissions on every read. */
@Component
class StorageListingCache internal constructor(ticker: Ticker, private val executor: Executor) {
    constructor() : this(Ticker.systemTicker(), ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue(32), { task -> Thread(task, "storage-listing-refresh").apply { isDaemon = true } }))

    // Copy connection values: never retain a managed entity or a request's security context in a refresh task.
    private data class Location(val provider: String, val bucket: String?, val region: String?, val endpoint: String?,
        val accessKey: String?, val secretKey: String?, val viewPrefix: String) {
        fun settings() = AppSettings(storageProvider = provider, storageBucket = bucket, storageRegion = region,
            storageEndpoint = endpoint, storageAccessKey = accessKey, storageSecretKey = secretKey)
        override fun toString() = "StorageLocation($provider)"
    }
    private data class BrowseKey(val location: Location, val root: String, val path: String)

    private val browses = Caffeine.newBuilder().ticker(ticker).executor(executor)
        .maximumWeight(100_000).weigher<BrowseKey, StorageBrowseResult> { _, value -> 256 + value.entries.size }
        .refreshAfterWrite(Duration.ofSeconds(5)).expireAfterWrite(Duration.ofSeconds(30))
        .build<BrowseKey, StorageBrowseResult> { key ->
            objectStorageAdapter(key.location.settings(), key.location.viewPrefix, boundedTransfers = true).browse(key.root, key.path)
        }
    private val counts = Caffeine.newBuilder().ticker(ticker).executor(executor)
        .maximumWeight(100_000).weigher<Location, FolderStorageSnapshot> { _, value -> 256 + value.documentCounts.size }
        .refreshAfterWrite(Duration.ofSeconds(5)).expireAfterWrite(Duration.ofSeconds(30))
        .build<Location, FolderStorageSnapshot> { location ->
            objectStorageAdapter(location.settings(), location.viewPrefix, boundedTransfers = true).countRoots()
        }

    fun browse(settings: AppSettings, viewPrefix: String, root: String, path: String): StorageBrowseResult =
        browses.get(BrowseKey(settings.location(viewPrefix), root, path))

    fun inspect(settings: AppSettings, prefixes: Collection<String>): FolderStorageSnapshot {
        if (prefixes.isEmpty()) return FolderStorageSnapshot(settings.storageProvider, emptyMap())
        val snapshot = counts.get(settings.location(settings.storageViewPrefix()))
        return snapshot.copy(documentCounts = prefixes.associateWith { snapshot.documentCounts[it] ?: 0 })
    }

    fun invalidate() { browses.invalidateAll(); counts.invalidateAll() }

    private fun AppSettings.location(prefix: String) = Location(storageProvider, storageBucket, storageRegion,
        storageEndpoint, storageAccessKey, storageSecretKey, prefix)

    @PreDestroy
    fun close() { (executor as? ThreadPoolExecutor)?.shutdownNow() }
}
