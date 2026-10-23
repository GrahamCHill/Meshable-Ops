package com.starter.service

import com.azure.core.credential.AzureNamedKeyCredential
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.ListBlobsOptions
import com.azure.identity.DefaultAzureCredentialBuilder
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.dto.AdminSettingsRequest
import com.starter.dto.MessageResponse
import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.Item
import org.springframework.stereotype.Service
import org.springframework.http.MediaType
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID
import java.time.Duration

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

internal interface ObjectStorageAdapter {
    val provider: String
    fun testConnection()
    fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot
    fun createFolder(prefix: String)
    fun deleteFolderIfEmpty(prefix: String)
    fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult
    fun listDocuments(rootPrefix: String): List<StorageEntry>
    fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray)
    fun download(rootPrefix: String, relativePath: String): StorageObjectDownload
}

@Service
class FolderStorageService(
    private val adminSettingsService: AdminSettingsService,
) {
    fun prefixFor(item: Item): String {
        val connection = item.storageConnection ?: return prefixFor(item.id)
        val prefix = normalizeStorageFolder(item.storagePrefix ?: throw BadRequestException("Missing shared folder path"))
        if (!prefix.startsWith(normalizeStorageFolder(connection.basePath))) throw BadRequestException("Folder is outside the connection boundary")
        return prefix
    }

    fun browse(item: Item, path: String) = scopedAdapter(item).browse(prefixFor(item), normalizeStorageFolder(path))
    fun listDocuments(item: Item) = scopedAdapter(item).listDocuments(prefixFor(item))
    fun upload(item: Item, path: String, objects: List<StorageObjectUpload>) {
        val adapter = scopedAdapter(item)
        val destination = normalizeStorageFolder(path)
        // Validate the whole batch before writing its first object.
        val paths = objects.map { normalizeStorageObject(it.relativePath) }
        objects.zip(paths).forEach { (file, objectPath) -> adapter.upload(prefixFor(item), destination + objectPath, file.contentType, file.bytes) }
    }
    fun download(item: Item, path: String, archive: Boolean): StorageObjectDownload {
        val adapter = scopedAdapter(item)
        val prefix = prefixFor(item)
        if (!archive) return adapter.download(prefix, normalizeStorageObject(path))
        val folder = normalizeStorageFolder(path)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            adapter.listDocuments(prefix + folder).forEach { document ->
                val safePath = normalizeStorageObject(document.path)
                zip.putNextEntry(ZipEntry(safePath))
                zip.write(adapter.download(prefix + folder, safePath).bytes)
                zip.closeEntry()
            }
        }
        return StorageObjectDownload("${folder.trimEnd('/').substringAfterLast('/').ifBlank { "storage-folder" }}.zip", "application/zip", output.toByteArray())
    }
    private fun scopedAdapter(item: Item): ObjectStorageAdapter {
        if (!item.accessible || item.storageConnection?.enabled == false) throw com.starter.exception.NotFoundException("Storage root not found")
        val connection = item.storageConnection
        if (connection == null) {
            requireManagedRoot(prefixFor(item))
            return adapter()
        }
        return objectStorageAdapter(connection.settings(), normalizeStorageFolder(connection.basePath))
    }

    fun testConnection(req: AdminSettingsRequest): MessageResponse {
        val settings = adminSettingsService.validatedStorageSettings(req)
        if (settings.storageProvider == "NONE") throw BadRequestException("Select a storage provider before testing the connection")
        try {
            adapter(settings).testConnection()
        } catch (ex: Exception) {
            val hint = when (ex) {
                is software.amazon.awssdk.services.s3.model.S3Exception -> when (ex.statusCode()) {
                    401, 403 -> "Access denied. Check the credentials and ListBucket permission for this prefix."
                    404 -> "Bucket not found. Check the bucket name, region, and endpoint."
                    else -> "S3 rejected the request. Check the bucket, region, endpoint, and credentials."
                }
                is com.azure.storage.blob.models.BlobStorageException -> when (ex.statusCode) {
                    401, 403 -> "Access denied. Check the account credentials or the server identity's Blob Data Reader access."
                    404 -> "Container not found. Check the container name and Blob service endpoint."
                    else -> "Azure rejected the request. Check the container, endpoint, and credentials."
                }
                else -> "Could not reach storage or resolve credentials. Check the service endpoint, server network access, and authentication settings."
            }
            throw BadRequestException(hint)
        }
        return MessageResponse("Connection verified: this prefix can be listed. Upload and delete permissions were not tested. Save to apply these settings.")
    }

    fun prefixFor(itemId: UUID) = "${adminSettingsService.currentEntity().storageViewPrefix()}$itemId/"
    fun inspectAndEnsureFolders(prefixes: Collection<String>) = adapter().inspectAndEnsureRoots(prefixes)
    fun createFolder(prefix: String) = adapter().createFolder(prefix)
    fun deleteFolderIfEmpty(prefix: String) = adapter().deleteFolderIfEmpty(prefix)

    fun browse(rootPrefix: String, path: String): StorageBrowseResult {
        requireManagedRoot(rootPrefix)
        return adapter().browse(rootPrefix, normalizeRelativePath(path))
    }

    fun listDocuments(rootPrefix: String): List<StorageEntry> {
        requireManagedRoot(rootPrefix)
        return adapter().listDocuments(rootPrefix)
    }

    fun upload(rootPrefix: String, currentPath: String, objects: List<StorageObjectUpload>) {
        requireManagedRoot(rootPrefix)
        val destination = normalizeRelativePath(currentPath)
        objects.forEach { objectUpload ->
            val objectPath = normalizeObjectPath(objectUpload.relativePath)
            adapter().upload(rootPrefix, destination + objectPath, objectUpload.contentType, objectUpload.bytes)
        }
    }

    fun download(rootPrefix: String, path: String): StorageObjectDownload {
        requireManagedRoot(rootPrefix)
        return adapter().download(rootPrefix, normalizeObjectPath(path))
    }

    fun downloadFolder(rootPrefix: String, path: String): StorageObjectDownload {
        requireManagedRoot(rootPrefix)
        val folderPath = normalizeRelativePath(path)
        val documents = adapter().listDocuments(rootPrefix).filter { it.path.startsWith(folderPath) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            documents.forEach { document ->
                val entryName = document.path.removePrefix(folderPath).ifBlank { document.name }
                archive.putNextEntry(ZipEntry(entryName))
                archive.write(adapter().download(rootPrefix, document.path).bytes)
                archive.closeEntry()
            }
        }
        val folderName = folderPath.trimEnd('/').substringAfterLast('/').ifBlank { "storage-root" }
        return StorageObjectDownload("$folderName.zip", "application/zip", output.toByteArray())
    }

    private fun adapter(settings: AppSettings = adminSettingsService.currentEntity()): ObjectStorageAdapter {
        return objectStorageAdapter(settings)
    }

    private fun requireManagedRoot(prefix: String) {
        val viewPrefix = adminSettingsService.currentEntity().storageViewPrefix()
        val id = prefix.removePrefix(viewPrefix).removeSuffix("/")
        if (!prefix.startsWith(viewPrefix) || '/' in id || runCatching { UUID.fromString(id) }.isFailure) {
            throw BadRequestException("Invalid managed storage root")
        }
    }

    private fun normalizeRelativePath(path: String): String {
        val segments = path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
        if (segments.any { it == "." || it == ".." }) throw BadRequestException("Invalid storage path")
        return segments.joinToString("/").let { if (it.isBlank()) "" else "$it/" }
    }


    private fun normalizeObjectPath(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
            throw BadRequestException("Invalid object path")
        }
        return segments.joinToString("/")
    }
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
    override fun download(rootPrefix: String, relativePath: String): StorageObjectDownload =
        throw BadRequestException("Object storage is not configured")
}

private data class S3StorageConfig(
    val bucket: String,
    val region: String,
    val endpoint: String?,
    val accessKey: String?,
    val secretKey: String?,
    val rootPrefix: String,
)

private class S3StorageAdapter(settings: AppSettings, viewPrefix: String) : ObjectStorageAdapter {
    override val provider = "S3"
    private val config = S3StorageConfig(
        bucket = settings.storageBucket ?: throw BadRequestException("S3 bucket is not configured"),
        region = settings.storageRegion ?: "us-east-1",
        endpoint = settings.storageEndpoint,
        accessKey = settings.storageAccessKey,
        secretKey = settings.storageSecretKey,
        rootPrefix = viewPrefix,
    )

    override fun testConnection() {
        client(probe = true).use { client ->
            client.listObjectsV2(ListObjectsV2Request.builder().bucket(config.bucket).prefix(config.rootPrefix).maxKeys(1).build())
        }
    }

    override fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot = client().use { client ->
        val counts = prefixes.associateWith { 0 }.toMutableMap()
        val present = mutableSetOf<String>()
        var token: String? = null
        do {
            val response = client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(config.bucket).prefix(config.rootPrefix)
                    .continuationToken(token).build()
            )
            response.contents().forEach { item ->
                val root = item.key().managedRoot(config.rootPrefix) ?: return@forEach
                if (root in counts) {
                    present += root
                    if (!item.key().endsWith('/')) counts[root] = counts.getValue(root) + 1
                }
            }
            token = response.nextContinuationToken()
        } while (response.isTruncated == true)
        (prefixes - present).forEach { putMarker(client, it) }
        FolderStorageSnapshot(provider, counts)
    }

    override fun createFolder(prefix: String) {
        client().use { putMarker(it, prefix) }
    }

    override fun deleteFolderIfEmpty(prefix: String) = client().use { client ->
        val objects = client.listObjectsV2(
            ListObjectsV2Request.builder().bucket(config.bucket).prefix(prefix).maxKeys(2).build()
        ).contents()
        if (objects.any { it.key() != prefix }) throw ConflictException("Folder contains content and cannot be deleted")
        client.deleteObject(DeleteObjectRequest.builder().bucket(config.bucket).key(prefix).build())
        Unit
    }

    override fun browse(rootPrefix: String, relativePath: String): StorageBrowseResult = client().use { client ->
        val fullPrefix = rootPrefix + relativePath
        val entries = mutableListOf<StorageEntry>()
        client.listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(config.bucket).prefix(fullPrefix).delimiter("/").build()
        ).forEach { response ->
            entries += response.commonPrefixes().map { child ->
                val relative = child.prefix().removePrefix(rootPrefix)
                StorageEntry(relative.removeSuffix("/").substringAfterLast('/'), relative, "FOLDER")
            }
            entries += response.contents().filter { it.key() != fullPrefix }.map { item ->
                val relative = item.key().removePrefix(rootPrefix)
                StorageEntry(
                    name = item.key().removePrefix(fullPrefix), path = relative, type = "OBJECT",
                    size = item.size(), lastModified = item.lastModified()?.toString(),
                )
            }
        }
        StorageBrowseResult(provider, relativePath, entries.sortedEntries())
    }

    override fun listDocuments(rootPrefix: String): List<StorageEntry> = client().use { client ->
        val documents = mutableListOf<StorageEntry>()
        var token: String? = null
        do {
            val response = client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(config.bucket).prefix(rootPrefix)
                    .continuationToken(token).build()
            )
            response.contents().filter { !it.key().endsWith('/') }.forEach { item ->
                val relative = item.key().removePrefix(rootPrefix)
                documents += StorageEntry(
                    name = relative.substringAfterLast('/'), path = relative, type = "OBJECT",
                    size = item.size(), lastModified = item.lastModified()?.toString(),
                )
            }
            token = response.nextContinuationToken()
        } while (response.isTruncated == true)
        documents.sortedEntries()
    }

    override fun upload(rootPrefix: String, relativePath: String, contentType: String?, bytes: ByteArray) = client().use { client ->
        client.putObject(
            PutObjectRequest.builder().bucket(config.bucket).key(rootPrefix + relativePath)
                .contentType(contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE).build(),
            RequestBody.fromBytes(bytes),
        )
        Unit
    }

    override fun download(rootPrefix: String, relativePath: String): StorageObjectDownload = client().use { client ->
        val response = client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(config.bucket).key(rootPrefix + relativePath).build()
        )
        StorageObjectDownload(
            filename = relativePath.substringAfterLast('/'),
            contentType = response.response().contentType() ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            bytes = response.asByteArray(),
        )
    }

    private fun client(probe: Boolean = false): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(config.region))
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(
                if (config.accessKey != null && config.secretKey != null) {
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey, config.secretKey))
                } else {
                    DefaultCredentialsProvider.create()
                }
            )
        if (probe) {
            builder.httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(20)) }
        }
        if (config.endpoint != null) {
            builder.endpointOverride(URI.create(config.endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        }
        return builder.build()
    }

    private fun putMarker(client: S3Client, prefix: String) {
        client.putObject(
            PutObjectRequest.builder().bucket(config.bucket).key(prefix).contentType("application/x-directory").build(),
            RequestBody.empty(),
        )
    }
}

private data class AzureBlobConfig(
    val container: String,
    val endpoint: String,
    val accountName: String?,
    val accountKey: String?,
    val rootPrefix: String,
)

private class AzureBlobStorageAdapter(settings: AppSettings, viewPrefix: String) : ObjectStorageAdapter {
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

    override fun inspectAndEnsureRoots(prefixes: Collection<String>): FolderStorageSnapshot {
        val container = container(createIfMissing = true)
        val counts = prefixes.associateWith { 0 }.toMutableMap()
        val present = mutableSetOf<String>()
        container.listBlobs(ListBlobsOptions().setPrefix(config.rootPrefix), null).forEach { item ->
            val root = item.name.managedRoot(config.rootPrefix) ?: return@forEach
            if (root in counts) {
                present += root
                if (!item.name.endsWith('/')) counts[root] = counts.getValue(root) + 1
            }
        }
        (prefixes - present).forEach { putMarker(container, it) }
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

    override fun download(rootPrefix: String, relativePath: String): StorageObjectDownload {
        val blob = container().getBlobClient(rootPrefix + relativePath)
        return StorageObjectDownload(
            filename = relativePath.substringAfterLast('/'),
            contentType = blob.properties.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            bytes = blob.downloadContent().toBytes(),
        )
    }

    private fun container(createIfMissing: Boolean = false): BlobContainerClient {
        val builder = BlobServiceClientBuilder().endpoint(config.endpoint)
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

private fun String.managedRoot(viewPrefix: String): String? {
    if (!startsWith(viewPrefix)) return null
    val id = removePrefix(viewPrefix).substringBefore('/')
    return id.takeIf { it.isNotBlank() }?.let { "$viewPrefix$it/" }
}

private fun AppSettings.storageViewPrefix(): String {
    val segments = storageRootPath.ifBlank { "folders" }.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
        throw BadRequestException("Invalid configured storage view path")
    }
    return "${segments.joinToString("/")}/"
}

private fun List<StorageEntry>.sortedEntries() =
    sortedWith(compareBy<StorageEntry>({ it.type != "FOLDER" }, { it.name.lowercase() }))

internal fun objectStorageAdapter(settings: AppSettings, viewPrefix: String = settings.storageViewPrefix()): ObjectStorageAdapter = when (settings.storageProvider) {
    "NONE" -> NoStorageAdapter
    "S3" -> S3StorageAdapter(settings, viewPrefix)
    "AZURE_BLOB" -> AzureBlobStorageAdapter(settings, viewPrefix)
    else -> throw BadRequestException("Unsupported storage provider")
}

internal fun normalizeStorageFolder(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    val parts = normalized.split('/').filter { it.isNotEmpty() }
    if (parts.any { it == "." || it == ".." || it.any { character -> character.code < 32 } }) throw BadRequestException("Invalid storage path")
    return parts.joinToString("/").let { if (it.isEmpty()) "" else "$it/" }
}

internal fun normalizeStorageObject(path: String): String = normalizeStorageFolder(path).trimEnd('/').ifEmpty {
    throw BadRequestException("Select a file to download")
}
