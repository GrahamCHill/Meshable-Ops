package com.starter.service.storage

import com.azure.core.credential.AzureNamedKeyCredential
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.ListBlobsOptions
import com.azure.identity.DefaultAzureCredentialBuilder
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.model.postgres.AppSettings
import org.springframework.http.MediaType
import java.time.Duration

private data class AzureBlobConfig(
    val container: String,
    val endpoint: String,
    val accountName: String?,
    val accountKey: String?,
    val rootPrefix: String,
)

internal class AzureBlobStorageAdapter(settings: AppSettings, viewPrefix: String, private val boundedTransfers: Boolean = false) : ObjectStorageAdapter {
    override val provider = "AZURE_BLOB"
    private val config = AzureBlobConfig(
        container = settings.storageBucket ?: throw BadRequestException("Azure Blob container is not configured"),
        endpoint = settings.storageEndpoint ?: throw BadRequestException("Azure Blob endpoint is not configured"),
        accountName = settings.storageAccessKey,
        accountKey = settings.storageSecretKey,
        rootPrefix = viewPrefix,
    )

    override fun testConnection() {
        container(createIfMissing = false)
            .listBlobs(ListBlobsOptions().setPrefix(config.rootPrefix).setMaxResultsPerPage(1), Duration.ofSeconds(20))
            .iterableByPage(1).iterator().hasNext()
    }

    override fun inspectAndEnsureRoots(prefixes: Collection<String>) = inspectRoots(prefixes)
    override fun countRoots() = inspectRoots(null)

    private fun inspectRoots(prefixes: Collection<String>?): FolderStorageSnapshot {
        val container = container(createIfMissing = prefixes != null)
        val counts = prefixes.orEmpty().associateWith { 0 }.toMutableMap()
        val present = mutableSetOf<String>()
        container.listBlobs(ListBlobsOptions().setPrefix(config.rootPrefix), null).forEach { item ->
            val root = item.name.managedRoot(config.rootPrefix) ?: return@forEach
            if (prefixes == null || root in counts) {
                present += root
                counts.putIfAbsent(root, 0)
                if (!item.name.endsWith('/') && !GeneratedPdfNamespace.excluded(item.name)) counts[root] = counts.getValue(root) + 1
            }
        }
        (prefixes.orEmpty() - present).forEach { putMarker(container, it) }
        return FolderStorageSnapshot(provider, counts)
    }

    override fun createFolder(prefix: String) {
        putMarker(container(createIfMissing = true), prefix)
    }

    override fun deleteFolderIfEmpty(prefix: String) {
        val container = container()
        if (container.listBlobs(ListBlobsOptions().setPrefix(prefix), null).any { it.name != prefix }) {
            throw ConflictException("Folder contains content and cannot be deleted")
        }
        container.getBlobClient(prefix).deleteIfExists()
    }

    override fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult {
        val fullPrefix = rootPrefix + relativePath
        val entries = container().listBlobsByHierarchy(
            "/", ListBlobsOptions().setPrefix(fullPrefix), null
        ).filter { it.name != fullPrefix }.map { item ->
            val relative = item.name.removePrefix(rootPrefix)
            if (item.isPrefix) {
                StorageEntry(relative.removeSuffix("/").substringAfterLast('/'), relative, "FOLDER")
            } else {
                StorageEntry(
                    name = item.name.removePrefix(fullPrefix), path = relative, type = "OBJECT",
                    size = item.properties.contentLength, contentType = item.properties.contentType,
                    lastModified = item.properties.lastModified?.toString(),
                )
            }
        }.toList()
        return StorageBrowseResult(provider, relativePath, entries.sortedEntries())
    }

    override fun listDocuments(rootPrefix: String): List<StorageEntry> =
        container().listBlobs(ListBlobsOptions().setPrefix(rootPrefix), null)
            .filter { !it.name.endsWith('/') }
            .map { item ->
                val relative = item.name.removePrefix(rootPrefix)
                StorageEntry(
                    name = relative.substringAfterLast('/'), path = relative, type = "OBJECT",
                    size = item.properties.contentLength, contentType = item.properties.contentType,
                    lastModified = item.properties.lastModified?.toString(),
                )
            }.toList().sortedEntries()

    override fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray) {
        container().getBlobClient(rootPrefix + relativePath)
            .upload(BinaryData.fromBytes(bytes), true)
        if (contentType != null) {
            container().getBlobClient(rootPrefix + relativePath).setHttpHeaders(
                com.azure.storage.blob.models.BlobHttpHeaders().setContentType(contentType)
            )
        }
    }

    override fun download(rootPrefix: String, relativePath: String, maxBytes: Int?): StorageObjectDownload {
        val blob = container().getBlobClient(rootPrefix + relativePath)
        val bytes = blob.openInputStream().use { if (maxBytes == null) it.readBytes() else it.readNBytes(maxBytes + 1) }
        if (maxBytes != null && bytes.size > maxBytes) throw BadRequestException("Document exceeds the indexing size limit")
        return StorageObjectDownload(
            filename = relativePath.substringAfterLast('/'),
            contentType = blob.properties.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            bytes = bytes,
        )
    }

    override fun delete(rootPrefix: String, relativePath: String) { container().getBlobClient(rootPrefix + relativePath).deleteIfExists() }

    private fun container(createIfMissing: Boolean = false): BlobContainerClient {
        val builder = BlobServiceClientBuilder().endpoint(config.endpoint)
        if (boundedTransfers) builder.retryOptions(com.azure.storage.common.policy.RequestRetryOptions(
            com.azure.storage.common.policy.RetryPolicyType.FIXED, 2, 10, 1000L, 1000L, null))
        if (config.accountName != null && config.accountKey != null) {
            builder.credential(AzureNamedKeyCredential(config.accountName, config.accountKey))
        } else {
            builder.credential(DefaultAzureCredentialBuilder().build())
        }
        val client = builder.buildClient()
            .getBlobContainerClient(config.container)
        if (createIfMissing) client.createIfNotExists()
        return client
    }

    private fun putMarker(container: BlobContainerClient, prefix: String) {
        container.getBlobClient(prefix).upload(BinaryData.fromBytes(ByteArray(0)), true)
    }
}
