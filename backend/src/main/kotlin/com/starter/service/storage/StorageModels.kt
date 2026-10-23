package com.starter.service.storage

data class FolderStorageSnapshot(
    val provider: String,
    val documentCounts: Map<String, Int>,
)

data class StorageEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long? = null,
    val contentType: String? = null,
    val lastModified: String? = null,
)

data class StorageBrowseResult(
    val provider: String,
    val path: String,
    val entries: List<StorageEntry>,
)

data class StorageObjectUpload(val relativePath: String, val contentType: String?, val bytes: ByteArray)
data class StorageObjectDownload(val filename: String, val contentType: String, val bytes: ByteArray)
