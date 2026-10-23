package com.starter.dto

import jakarta.validation.constraints.*

data class StorageConnectionRequest(
    @field:NotBlank @field:Size(max = 100) val nickname: String,
    @field:Pattern(regexp = "S3|AZURE_BLOB") val provider: String,
    @field:NotBlank @field:Size(max = 255) val bucket: String,
    @field:Size(max = 255) val region: String? = null,
    @field:Size(max = 512) val endpoint: String? = null,
    @field:Size(max = 255) val accessKey: String? = null,
    @field:Size(max = 512) val secretKey: String? = null,
    val useServerIdentity: Boolean = false,
    @field:Size(max = 512) val basePath: String = "",
    val enabled: Boolean = true,
)

data class StorageConnectionResponse(
    val id: String, val nickname: String, val provider: String, val bucket: String,
    val region: String?, val endpoint: String?, val accessKey: String?, val secretConfigured: Boolean,
    val basePath: String, val enabled: Boolean, val folders: List<SharedFolderResponse>,
)

data class SharedStorageConnectionResponse(val id: String, val nickname: String, val provider: String, val folderCount: Long)
data class SharedFolderResponse(val id: String, val nickname: String, val path: String, val accessible: Boolean)
data class ShareFolderRequest(
    @field:NotBlank @field:Size(max = 255) val nickname: String,
    @field:Size(max = 1024) val path: String = "",
    val accessible: Boolean = true,
)
data class UpdateSharedFolderRequest(@field:NotBlank @field:Size(max = 255) val nickname: String, val accessible: Boolean)
