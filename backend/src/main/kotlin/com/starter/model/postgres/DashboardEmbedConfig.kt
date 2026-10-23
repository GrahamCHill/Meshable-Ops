package com.starter.model.postgres

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dashboard_embed_configs")
@EntityListeners(AuditingEntityListener::class)
data class DashboardEmbedConfig(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var label: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "dashboard_id", nullable = false)
    var dashboardId: String = "",

    @Column(name = "guest_token_resource_id")
    var guestTokenResourceId: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dashboard_embed_config_roles", joinColumns = [JoinColumn(name = "dashboard_embed_config_id")])
    @Column(name = "role_name")
    var allowedRoles: MutableList<String> = mutableListOf(),

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
