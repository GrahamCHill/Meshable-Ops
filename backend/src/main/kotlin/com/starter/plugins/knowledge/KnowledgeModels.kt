package com.starter.plugins.knowledge

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity @Table(name = KnowledgePlugin.PAGES)
class KnowledgePage(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var title: String = "",
    var parentId: UUID? = null,
    @Column(columnDefinition = "TEXT", nullable = false) var markdown: String = "",
    @Column(columnDefinition = "TEXT", nullable = false) var assetsJson: String = "{}",
    @Column(nullable = false) var updatedAt: Instant = Instant.now(),
    @Version var revision: Long = 0,
)

interface KnowledgePageRepository : JpaRepository<KnowledgePage, UUID> {
    @Query("select new com.starter.plugins.knowledge.KnowledgePageSummary(p.id, p.title, p.parentId, p.updatedAt, p.revision) from KnowledgePage p order by p.title, p.id")
    fun summaries(): List<KnowledgePageSummary>
    fun existsByParentId(parentId: UUID): Boolean
}

@Entity @Table(name = KnowledgePlugin.SETTINGS)
class KnowledgeSettings(
    @Id val id: Int = 1,
    @Column(columnDefinition = "TEXT", nullable = false) var exporterIdsJson: String = "[]",
    @Column(columnDefinition = "TEXT", nullable = false) var coverHtml: String = "",
    @Column(columnDefinition = "TEXT", nullable = false) var coverAssetsJson: String = "{}",
    @Version var revision: Long = 0,
)
interface KnowledgeSettingsRepository : JpaRepository<KnowledgeSettings, Int>

data class KnowledgePageSummary(val id: UUID, val title: String, val parentId: UUID?, val updatedAt: Instant, val revision: Long)
data class KnowledgePageInput(val title: String, val markdown: String, val parentId: UUID? = null, val revision: Long = -1, val assets: Map<String, String>? = null)
data class KnowledgeDiagram(val key: String, val source: String)
data class KnowledgeHeading(val id: String, val title: String, val level: Int)
data class KnowledgeRendered(val html: String, val diagrams: List<KnowledgeDiagram>, val headings: List<KnowledgeHeading>)
data class KnowledgePageView(val id: UUID, val title: String, val parentId: UUID?, val markdown: String, val assets: Map<String, String>, val revision: Long, val rendered: KnowledgeRendered)
data class KnowledgeStatus(val canEdit: Boolean, val canExport: Boolean, val canManage: Boolean)
data class KnowledgeMember(val id: UUID, val name: String, val email: String, val active: Boolean = true)
data class KnowledgeSettingsInput(val coverHtml: String, val coverAssets: Map<String, String>, val revision: Long = -1)
data class KnowledgePermissionsInput(val exporterIds: Set<UUID>, val revision: Long = -1)
data class KnowledgeSettingsView(val exporterIds: Set<UUID>, val coverHtml: String, val coverAssets: Map<String, String>, val revision: Long, val defaultHtml: String, val members: List<KnowledgeMember>)
data class KnowledgeSelection(val id: UUID, val revision: Long)
data class KnowledgeExportInput(val title: String, val pages: List<KnowledgeSelection>, val diagrams: Map<String, String> = emptyMap())
data class KnowledgePreparedPage(val id: UUID, val title: String, val revision: Long, val diagrams: List<KnowledgeDiagram>)
data class KnowledgeCoverPreview(val coverHtml: String, val coverAssets: Map<String, String>)
