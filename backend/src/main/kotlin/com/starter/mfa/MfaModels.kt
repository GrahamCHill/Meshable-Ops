package com.starter.mfa

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Entity @Table(name = "mfa_credentials")
class MfaCredential(
    @Id @Column(name = "id") val userId: UUID = UUID.randomUUID(),
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class) @Column(columnDefinition = "TEXT") var totpSecret: String? = null,
    var emailEnabled: Boolean = false,
    var lastTotpStep: Long = -1,
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var recoveryHashes: String = "[]",
) {
    fun enabled() = totpSecret != null || emailEnabled
    override fun toString() = "MfaCredential(userId=$userId)"
}
enum class ChallengePurpose { LOGIN, SETUP_TOTP, SETUP_EMAIL, MANAGE_EMAIL }
@Entity @Table(name = "mfa_challenges")
class MfaChallenge(
    @Id val id: String = "",
    val userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val purpose: ChallengePurpose = ChallengePurpose.LOGIN,
    val expiresAt: Instant = Instant.now(),
    val sessionVersion: Long = 0,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class) @Column(columnDefinition = "TEXT") var pendingSecret: String? = null,
    @get:JsonIgnore var emailCodeHash: String? = null,
    var emailSentAt: Instant? = null,
    var attempts: Int = 0,
    var consumed: Boolean = false,
) { override fun toString() = "MfaChallenge(purpose=$purpose)" }

interface MfaCredentialRepository : JpaRepository<MfaCredential, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT m FROM MfaCredential m WHERE m.userId = :id")
    fun locked(id: UUID): Optional<MfaCredential>
    @Query("SELECT count(m) FROM MfaCredential m WHERE m.userId = :id AND (m.totpSecret IS NOT NULL OR m.emailEnabled = true)")
    fun enabledCount(id: UUID): Long
    @Query("SELECT count(m) FROM MfaCredential m WHERE m.emailEnabled = true AND m.totpSecret IS NULL")
    fun emailOnlyCount(): Long
}
interface MfaChallengeRepository : JpaRepository<MfaChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT m FROM MfaChallenge m WHERE m.id = :id")
    fun locked(id: String): Optional<MfaChallenge>
    fun deleteByExpiresAtBefore(before: Instant)
}
