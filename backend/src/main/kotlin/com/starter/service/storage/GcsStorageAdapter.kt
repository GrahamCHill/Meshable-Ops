package com.starter.service.storage

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.cloud.http.HttpTransportOptions
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.AppSettings
import org.springframework.http.MediaType
import java.nio.channels.Channels
import java.time.Duration

internal class GcsStorageAdapter(
    settings: AppSettings,
    private val viewPrefix: String,
    private val storageFactory: () -> Storage = { gcsStorage(settings) },
) : ObjectStorageAdapter {
    override val provider = "GCS"
    private val bucket = settings.storageBucket ?: throw BadRequestException("Google Cloud Storage bucket is not configured")
    private val storage by lazy(storageFactory)

    override fun testConnection() {
        storage.list(bucket, Storage.BlobListOption.prefix(viewPrefix), Storage.BlobListOption.pageSize(1)).values.iterator().hasNext()
    }

    override fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot {
        val counts = prefixes.associateWith { 0 }.toMutableMap()
        val present = mutableSetOf<String>()
        storage.list(bucket, Storage.BlobListOption.prefix(viewPrefix)).iterateAll().forEach { blob ->
            val root = blob.name.managedRoot(viewPrefix) ?: return@forEach
            if (root in counts) {
                present += root
                if (!blob.name.endsWith('/')) counts[root] = counts.getValue(root) + 1
            }
        }
        (prefixes - present).forEach { createFolder(it) }
        return FolderStorageSnapshot(provider, counts)
    }

    override fun createFolder(prefix: String) {
        storage.create(BlobInfo.newBuilder(bucket, prefix).setContentType("application/x-directory").build(), ByteArray(0))
    }

    override fun deleteFolderIfEmpty(prefix: String) {
        val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.pageSize(2))
        if (blobs.iterateAll().any { it.name != prefix }) throw ConflictException("Folder contains content and cannot be deleted")
        storage.delete(BlobId.of(bucket, prefix))
    }

    override fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult {
        val fullPrefix = rootPrefix + relativePath
        val entries = storage.list(bucket, Storage.BlobListOption.prefix(fullPrefix), Storage.BlobListOption.currentDirectory())
            .iterateAll().filter { it.name != fullPrefix }.map { blob ->
                val relative = blob.name.removePrefix(rootPrefix)
                if (blob.isDirectory || blob.name.endsWith('/')) {
                    StorageEntry(relative.removeSuffix("/").substringAfterLast('/'), relative, "FOLDER")
                } else blob.entry(relative, blob.name.removePrefix(fullPrefix))
            }
        return StorageBrowseResult(provider, relativePath, entries.sortedEntries())
    }

    override fun listDocuments(rootPrefix: String): List<StorageEntry> =
        storage.list(bucket, Storage.BlobListOption.prefix(rootPrefix)).iterateAll()
            .filter { !it.name.endsWith('/') }.map { blob ->
                val relative = blob.name.removePrefix(rootPrefix)
                blob.entry(relative, relative.substringAfterLast('/'))
            }.sortedEntries()

    override fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray) {
        storage.create(BlobInfo.newBuilder(bucket, rootPrefix + relativePath)
            .setContentType(contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE).build(), bytes)
    }

    override fun download(rootPrefix: String, relativePath: String, maxBytes: Int?): StorageObjectDownload {
        val blob = storage.get(BlobId.of(bucket, rootPrefix + relativePath)) ?: throw NotFoundException("Storage object not found")
        if (maxBytes != null && blob.size > maxBytes) throw BadRequestException("Document exceeds the indexing size limit")
        val bytes = Channels.newInputStream(blob.reader()).use { if (maxBytes == null) it.readBytes() else it.readNBytes(maxBytes + 1) }
        if (maxBytes != null && bytes.size > maxBytes) throw BadRequestException("Document exceeds the indexing size limit")
        return StorageObjectDownload(relativePath.substringAfterLast('/'), blob.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)
    }

    override fun delete(rootPrefix: String, relativePath: String) { storage.delete(BlobId.of(bucket, rootPrefix + relativePath)) }

    private fun Blob.entry(path: String, name: String) = StorageEntry(
        name = name, path = path, type = "OBJECT", size = size,
        contentType = contentType, lastModified = updateTimeOffsetDateTime?.toString(),
    )
}

private fun gcsStorage(settings: AppSettings): Storage {
    val builder = StorageOptions.newBuilder()
        .setTransportOptions(HttpTransportOptions.newBuilder().setConnectTimeout(5000).setReadTimeout(10000).build())
        .setRetrySettings(StorageOptions.getDefaultRetrySettings().toBuilder().setTotalTimeoutDuration(Duration.ofSeconds(20)).build())
    settings.storageSecretKey?.let { builder.setCredentials(gcsServiceAccountCredentials(it)) }
    return builder.build().service
}
