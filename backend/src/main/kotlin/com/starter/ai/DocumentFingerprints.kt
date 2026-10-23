package com.starter.ai

import com.starter.service.storage.StorageEntry
import java.security.MessageDigest

object DocumentFingerprints {
    fun metadata(entry: StorageEntry) = hash(listOf(entry.path, entry.name, entry.contentType, entry.size, entry.lastModified).joinToString("|").toByteArray(Charsets.UTF_8))
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
