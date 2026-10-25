package com.starter.security.oidc

import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.security.SecureValues
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_external_identities", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])])
class ExternalIdentity(
    @Id @Column(length = 64) val id: String = "",
    @Column(name = "user_id", nullable = false) val userId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 32) val provider: String = "",
    @Column(nullable = false, length = 512) val issuer: String = "",
    @Column(nullable = false, length = 255) val subject: String = "",
    @Column(nullable = false) val createdAt: Instant = Instant.now(),
)

interface ExternalIdentityRepository : JpaRepository<ExternalIdentity, String> {
    fun findAllByUserId(userId: UUID): List<ExternalIdentity>
    fun findByUserIdAndProvider(userId: UUID, provider: String): ExternalIdentity?
}

data class ProviderIdentity(val provider: String, val issuer: String, val subject: String) : Serializable {
    // Length-prefixed components avoid ambiguities; emails and display names never identify accounts.
    fun key() = SecureValues.hash(listOf(provider, issuer, subject).joinToString("") { "${it.length}:$it" })
}

@Service
class ExternalIdentities(private val rows: ExternalIdentityRepository, private val users: UserRepository) {
    fun linked(email: String): Set<String> = rows.findAllByUserId(account(email).id).map { it.provider }.toSet()

    fun resolve(identity: ProviderIdentity): User {
        val link = rows.findById(identity.key()).orElse(null)
            ?: throw ForbiddenException("This identity is not connected. Sign in with your workspace password, then connect it in Security & MFA.")
        return users.findById(link.userId).orElse(null)?.takeIf { it.active }
            ?: throw ForbiddenException("This workspace account is unavailable")
    }

    @Transactional
    fun connect(identity: ProviderIdentity, userId: UUID, sessionVersion: Long) {
        val user = users.lockedById(userId).orElse(null)?.takeIf { it.active && it.sessionVersion == sessionVersion }
            ?: throw ForbiddenException("Sign in again before connecting an identity")
        val existing = rows.findById(identity.key()).orElse(null)
        if (existing != null && existing.userId != user.id) throw ConflictException("This identity is already connected to another account")
        val previous = rows.findByUserIdAndProvider(user.id, identity.provider)
        if (previous != null && previous.id != identity.key()) throw ConflictException("Disconnect the existing identity for this provider first")
        if (existing == null) rows.saveAndFlush(ExternalIdentity(identity.key(), user.id, identity.provider, identity.issuer, identity.subject))
    }

    @Transactional
    fun disconnect(email: String, provider: String) {
        val user = account(email)
        users.lockedById(user.id).orElseThrow()
        rows.findByUserIdAndProvider(user.id, provider)?.let { rows.delete(it) }
    }

    fun account(email: String): User = users.findByEmail(email).orElse(null)?.takeIf { it.active }
        ?: throw ForbiddenException("This workspace account is unavailable")
}
