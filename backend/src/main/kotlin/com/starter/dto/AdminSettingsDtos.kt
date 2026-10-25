package com.starter.dto

import jakarta.validation.constraints.*
import com.fasterxml.jackson.annotation.JsonProperty

data class AdminSettingsRequest(
    @field:Pattern(regexp = "NONE|S3|AZURE_BLOB|GCS") val storageProvider: String = "NONE",
    @field:Size(max = 255) val storageBucket: String? = null,
    @field:Size(max = 255) val storageRegion: String? = null,
    @field:Size(max = 512) val storageEndpoint: String? = null,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 255) val storageAccessKey: String? = null,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 32768) val storageSecretKey: String? = null,
    @field:NotBlank @field:Size(max = 512) val storageRootPath: String = "folders",
    val vectorReindexEnabled: Boolean = false,
    @field:Min(15) @field:Max(10080) val vectorReindexIntervalMinutes: Int = 1440,
    val useServerIdentity: Boolean? = null,
    @field:Size(max = 32) val vectorReindexSchedule: String = "INTERVAL",
    @field:Size(max = 5) val vectorReindexTime: String = "00:00",
    @field:Size(max = 16) val vectorReindexDayOfWeek: String = "FRIDAY",
    @field:Size(max = 100) val vectorReindexTimeZone: String = "Africa/Johannesburg",
) { override fun toString() = "AdminSettingsRequest(storageProvider=$storageProvider)" }

data class AdminSettingsResponse(
    val id: String,
    val storageProvider: String,
    val storageBucket: String?,
    val storageRegion: String?,
    val storageEndpoint: String?,
    val storageAccessKeyConfigured: Boolean,
    val storageSecretKeyConfigured: Boolean,
    val storageRootPath: String,
    val vectorReindexEnabled: Boolean,
    val vectorReindexIntervalMinutes: Int,
    val lastVectorReindexAt: String?,
    val lastVectorReindexStatus: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val encryptionReady: Boolean,
    val vectorReindexSchedule: String,
    val vectorReindexTime: String,
    val vectorReindexDayOfWeek: String,
    val vectorReindexTimeZone: String,
    val vectorReindexNextRunAt: String?,
    val vectorReindexPendingChanges: Boolean,
    val vectorReindexLastCheckAt: String?,
)
