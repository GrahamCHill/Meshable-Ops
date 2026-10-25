package com.starter.service.storage

import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings

internal interface ObjectStorageAdapter {
    val provider: String
    fun testConnection()
    fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot
    fun createFolder(prefix: String)
    fun deleteFolderIfEmpty(prefix: String)
    fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult
    fun listDocuments(rootPrefix: String): List<StorageEntry>
    fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray)
    fun download(rootPrefix: String, relativePath: String, maxBytes: Int? = null): StorageObjectDownload
}

internal fun objectStorageAdapter(settings: AppSettings, viewPrefix: String = settings.storageViewPrefix()): ObjectStorageAdapter = when (settings.storageProvider) {
    "NONE" -> NoStorageAdapter
    "S3" -> S3StorageAdapter(settings, viewPrefix)
    "AZURE_BLOB" -> AzureBlobStorageAdapter(settings, viewPrefix)
    "GCS" -> GcsStorageAdapter(settings, viewPrefix)
    else -> throw BadRequestException("Unsupported storage provider")
}

private object NoStorageAdapter : ObjectStorageAdapter {
    override val provider = "NONE"
    override fun testConnection() = Unit
    override fun inspectAndEnsureRoots(prefixes: Collection<String>) =
        FolderStorageSnapshot(provider, prefixes.associateWith { 0 })
    override fun createFolder(prefix: String) = Unit
    override fun deleteFolderIfEmpty(prefix: String) = Unit
    override fun browse(rootPrefix: String, relativePath: String) =
        StorageBrowseResult(provider, relativePath, emptyList())
    override fun listDocuments(rootPrefix: String) = emptyList<StorageEntry>()
    override fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray) =
        throw BadRequestException("Object storage is not configured")
    override fun download(rootPrefix: String, relativePath: String, maxBytes: Int?): StorageObjectDownload =
        throw BadRequestException("Object storage is not configured")
}
