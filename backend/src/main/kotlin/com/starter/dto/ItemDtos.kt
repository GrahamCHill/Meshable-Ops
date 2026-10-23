package com.starter.dto

import jakarta.validation.constraints.*

data class ItemRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

data class ItemResponse(
    val id: String,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val storagePrefix: String,
    val storageProvider: String,
    val documentCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val connectionId: String? = null,
    val connectionNickname: String? = null,
    val accessible: Boolean = true,
)

data class StorageEntryResponse(
    val name: String,
    val path: String,
    val type: String,
    val size: Long?,
    val contentType: String?,
    val lastModified: String?,
    val documentId: String? = null,
    val vectorStatus: String? = null,
)

data class StorageContentsResponse(
    val folderId: String,
    val rootPrefix: String,
    val path: String,
    val storageProvider: String,
    val entries: List<StorageEntryResponse>
)
