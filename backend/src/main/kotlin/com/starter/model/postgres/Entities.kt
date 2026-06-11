package com.starter.model.postgres

import jakarta.persistence.*
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

    @Column(nullable = false)
    var password: String = "",

    @Column(nullable = false)
    val role: String = "USER",

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "items")
@EntityListeners(AuditingEntityListener::class)
data class Item(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = User(),

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @ElementCollection
    @CollectionTable(name = "item_tags", joinColumns = [JoinColumn(name = "item_id")])
    @Column(name = "tag")
    var tags: MutableList<String> = mutableListOf(),

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
