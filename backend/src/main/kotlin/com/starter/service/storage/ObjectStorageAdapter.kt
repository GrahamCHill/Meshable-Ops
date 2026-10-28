package com.starter.service.storage

import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings

internal interface ObjectStorageAdapter {
    val provider: String
    fun testConnection()
    fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot
    fun countRoots(): FolderStorageSnapshot
    fun createFolder(prefix: String)
    fun deleteFolderIfEmpty(prefix: String)
    fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult
    fun listDocuments(rootPrefix: String): List<StorageEntry>
    fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray)
    fun download(rootPrefix: String, relativePath: String, maxBytes: Int? = null): StorageObjectDownload
    fun delete(rootPrefix: String, relativePath: String)
}

internal fun objectStorageAdapter(settings: AppSettings, viewPrefix: String = settings.storageViewPrefix(), boundedTransfers: Boolean = false): ObjectStorageAdapter = when (settings.storageProvider) {
    "NONE" -> NoStorageAdapter
    "S3" -> S3StorageAdapter(settings, viewPrefix, boundedTransfers)
    "AZURE_BLOB" -> AzureBlobStorageAdapter(settings, viewPrefix, boundedTransfers)
    "GCS" -> GcsStorageAdapter(settings, viewPrefix)
    else -> throw BadRequestException("Unsupported storage provider")
}

private object NoStorageAdapter : ObjectStorageAdapter {
    override val provider = "NONE"
    override fun testConnection() = Unit
    override fun inspectAndEnsureRoots(prefixes: Collection<String>) =
        FolderStorageSnapshot(provider, prefixes.associateWith { 0 })
    override fun countRoots() = FolderStorageSnapshot(provider, emptyMap())
    override fun createFolder(prefix: String) = Unit
    override fun deleteFolderIfEmpty(prefix: String) = Unit
    override fun browse(rootPrefix: String, relativePath: String) =
        StorageBrowseResult(provider, relativePath, emptyList())
    override fun listDocuments(rootPrefix: String) = emptyList<StorageEntry>()
    override fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray) =
        throw BadRequestException("Object storage is not configured")
    override fun download(rootPrefix: String, relativePath: String, maxBytes: Int?): StorageObjectDownload =
        throw BadRequestException("Object storage is not configured")
    override fun delete(rootPrefix: String, relativePath: String) = throw BadRequestException("Object storage is not configured")
}
