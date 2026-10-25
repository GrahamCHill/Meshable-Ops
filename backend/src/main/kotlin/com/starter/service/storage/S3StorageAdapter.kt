package com.starter.service.storage

import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.model.postgres.AppSettings
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
import java.time.Duration

private data class S3StorageConfig(
    val bucket: String,
    val region: String,
    val endpoint: String?,
    val accessKey: String?,
    val secretKey: String?,
    val rootPrefix: String,
)

internal class S3StorageAdapter(settings: AppSettings, viewPrefix: String, private val boundedTransfers: Boolean = false) : ObjectStorageAdapter {
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

    override fun download(rootPrefix: String, relativePath: String, maxBytes: Int?): StorageObjectDownload = client().use { client ->
        val response = client.getObject(
            GetObjectRequest.builder().bucket(config.bucket).key(rootPrefix + relativePath).build()
        )
        response.use {
            val bytes = if (maxBytes == null) it.readBytes() else it.readNBytes(maxBytes + 1)
            if (maxBytes != null && bytes.size > maxBytes) throw BadRequestException("Document exceeds the indexing size limit")
            StorageObjectDownload(relativePath.substringAfterLast('/'), it.response().contentType() ?: MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)
        }
    }

    override fun delete(rootPrefix: String, relativePath: String) {
        client().use { it.deleteObject(DeleteObjectRequest.builder().bucket(config.bucket).key(rootPrefix + relativePath).build()) }
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
        if (probe || boundedTransfers) {
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
