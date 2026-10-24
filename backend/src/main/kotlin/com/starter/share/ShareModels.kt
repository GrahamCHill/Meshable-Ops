package com.starter.share

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.starter.security.PasswordBytes
import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Entity @Table(name = "file_shares")
class FileShare(
    @Id val id: UUID = UUID.randomUUID(), @Column(unique = true) val tokenHash: String = "",
    val ownerId: UUID = UUID.randomUUID(), val folderId: UUID = UUID.randomUUID(),
    @Column(columnDefinition = "TEXT") val storagePath: String = "",
    @Column(columnDefinition = "TEXT") val filename: String = "",
    val recipient: String = "", @get:JsonIgnore val passwordHash: String = "",
    val sourceHash: String = "", val pageCount: Int = 0,
    val createdAt: Instant = Instant.now(), val expiresAt: Instant = Instant.now(),
    var revoked: Boolean = false, var deliveryStatus: String = "PENDING",
) { override fun toString() = "FileShare(id=$id)" }
@Entity @Table(name = "share_access_grants")
class ShareAccessGrant(@Id val id: String = "", val shareId: UUID = UUID.randomUUID(), val expiresAt: Instant = Instant.now())
@Entity @Table(name = "file_share_settings")
class FileShareSettings(@Id val id: Long = 1, var enabled: Boolean = true,
    @Column(columnDefinition = "TEXT") var publicBaseUrl: String = "", var maxLifetimeHours: Int = 168)
interface FileShareRepository : JpaRepository<FileShare, UUID> {
    fun findByTokenHash(hash: String): Optional<FileShare>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<FileShare>
}
interface ShareAccessGrantRepository : JpaRepository<ShareAccessGrant, String> {
    @org.springframework.transaction.annotation.Transactional fun deleteByExpiresAtBefore(before: Instant)
}
interface FileShareSettingsRepository : JpaRepository<FileShareSettings, Long>
data class CreateShareRequest(val folderId: UUID, @field:NotBlank @field:Size(max = 2000) val path: String,
    @field:NotBlank @field:Email @field:Size(max = 254) val recipient: String,
    @field:Size(min = 12, max = 72) @field:PasswordBytes @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val password: String,
    @field:Min(1) @field:Max(720) val lifetimeHours: Int = 72) { override fun toString() = "CreateShareRequest()" }
data class SharePasswordRequest(@field:NotBlank @field:PasswordBytes @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val password: String) { override fun toString() = "SharePasswordRequest()" }
data class ShareSettingsRequest(val enabled: Boolean, @field:Size(max = 2000) val publicBaseUrl: String,
    @field:Min(1) @field:Max(720) val maxLifetimeHours: Int = 168)
data class ShareSettingsView(val enabled: Boolean, val publicBaseUrl: String, val maxLifetimeHours: Int, val emailAvailable: Boolean)
data class ShareView(val id: UUID, val filename: String, val recipient: String, val createdAt: Instant, val expiresAt: Instant, val revoked: Boolean, val deliveryStatus: String)
data class ShareCreated(val share: ShareView, val url: String) { override fun toString() = "ShareCreated()" }
data class ShareUnlocked(val accessToken: String, val expiresAt: Instant) { override fun toString() = "ShareUnlocked()" }
data class SharedFileView(val filename: String, val pageCount: Int, val recipient: String, val expiresAt: Instant, val canPreview: Boolean, val canDownload: Boolean)
