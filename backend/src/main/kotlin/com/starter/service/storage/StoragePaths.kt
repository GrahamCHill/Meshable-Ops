package com.starter.service.storage

import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings

internal fun String.managedRoot(viewPrefix: String): String? {
    if (!startsWith(viewPrefix)) return null
    val id = removePrefix(viewPrefix).substringBefore('/')
    return id.takeIf { it.isNotBlank() }?.let { "$viewPrefix$it/" }
}

internal fun AppSettings.storageViewPrefix(): String {
    val segments = storageRootPath.ifBlank { "folders" }.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
        throw BadRequestException("Invalid configured storage view path")
    }
    return "${segments.joinToString("/")}/"
}

internal fun List<StorageEntry>.sortedEntries() =
    sortedWith(compareBy<StorageEntry> { it.type != "FOLDER" }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

internal fun normalizeStorageFolder(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    val parts = normalized.split('/').filter { it.isNotEmpty() }
    if (parts.any { it == "." || it == ".." || it.any { character -> character.code < 32 } }) throw BadRequestException("Invalid storage path")
    return parts.joinToString("/").let { if (it.isEmpty()) "" else "$it/" }
}

internal fun normalizeStorageObject(path: String): String = normalizeStorageFolder(path).trimEnd('/').ifEmpty {
    throw BadRequestException("Select a file to download")
}
