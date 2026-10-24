package com.starter.model.postgres

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
data class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String = "",

    @Column(nullable = false, unique = true)
    val username: String = "",

    @Column(name = "name")
    val name: String? = null,

    @Column(name = "contact_number")
    val contactNumber: String? = null,

    @get:JsonIgnore @Column(nullable = false)
    var password: String = "",

    @Column(nullable = false)
    var role: String = "USER",

    @Column(name = "can_upload_documents", nullable = false)
    var canUploadDocuments: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0")
    var sessionVersion: Long = 0,
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("false")
    var mfaPromptDismissed: Boolean = false,

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    override fun toString() = "User(id=$id, role=$role)"
}
