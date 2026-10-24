package com.starter.service

import com.starter.exception.BadRequestException
import com.starter.dto.AdminSettingsRequest
import com.starter.dto.MessageResponse
import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.Item
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID
import com.starter.service.storage.*

@Service
class FolderStorageService(
    private val adminSettingsService: AdminSettingsService,
    private val access: com.starter.access.AccessControlService? = null,
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
    fun download(item: Item, path: String, archive: Boolean): StorageObjectDownload =
        downloadTransformed(item, path, archive, null) { it }

    fun downloadTransformed(item: Item, path: String, archive: Boolean, maxPdfBytes: Int?, transform: (StorageObjectDownload) -> StorageObjectDownload): StorageObjectDownload {
        access?.requireStorage(item.id, path, archive)
        val acl = access?.storage()
        val adapter = scopedAdapter(item)
        val prefix = prefixFor(item)
        fun read(root: String, objectPath: String): StorageObjectDownload {
            val source = if (maxPdfBytes != null && objectPath.endsWith(".pdf", ignoreCase = true)) adapter.download(root, objectPath, maxPdfBytes)
                else adapter.download(root, objectPath)
            return transform(source)
        }
        if (!archive) return read(prefix, normalizeStorageObject(path))
        val folder = normalizeStorageFolder(path)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            adapter.listDocuments(prefix + folder).forEach { document ->
                val safePath = normalizeStorageObject(document.path)
                if (acl?.allows(item.id, folder + safePath) == false) return@forEach
                zip.putNextEntry(ZipEntry(safePath))
                zip.write(read(prefix + folder, safePath).bytes)
                zip.closeEntry()
            }
        }
        return StorageObjectDownload("${folder.trimEnd('/').substringAfterLast('/').ifBlank { "storage-folder" }}.zip", "application/zip", output.toByteArray())
    }
    fun downloadForIndex(item: Item, path: String, maxBytes: Int) = scopedAdapter(item).download(prefixFor(item), normalizeStorageObject(path), maxBytes)
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
        val settings = adminSettingsService.storageSettingsForTest(req)
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
