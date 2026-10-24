package com.starter.plugins.projectassets

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import com.starter.projects.ProjectAccess
import jakarta.persistence.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class ProjectAssetRecord(@Id val id: UUID = UUID.randomUUID(), var projectId: UUID = UUID.randomUUID(),
    @Column(length=120) var label: String = "", @Column(length=2000) var remoteId: String = "",
    @get:JsonIgnore @Column(columnDefinition="TEXT",nullable=false) var payloadJson: String = "{}",
    var updatedAt: Instant = Instant.now(), @Version var revision: Long = 0)

@Entity @Table(name="plugin_jira_projects",indexes=[Index(columnList="project_id")])
class JiraProjectRecord : ProjectAssetRecord()
@Entity @Table(name="plugin_figma_files",indexes=[Index(columnList="project_id")])
class FigmaFileRecord : ProjectAssetRecord()
@Entity @Table(name="plugin_mermaid_diagrams",indexes=[Index(columnList="project_id")])
class MermaidDiagramRecord : ProjectAssetRecord()

data class ProjectAsset(val id: UUID, val projectId: UUID, val label: String, val remoteId: String, val payload: String,
    val updatedAt: Instant, val revision: Long)

/** One fixed namespace per plugin. Project membership and table policy are checked before every read or write. */
@Service
class ProjectAssetStore(private val jdbc: JdbcTemplate, private val projects: ProjectAccess, private val plugins: PluginRegistry) {
    private fun table(plugin: String) = when(plugin) {
        "jira" -> "plugin_jira_projects"; "figma" -> "plugin_figma_files"; "mermaid" -> "plugin_mermaid_diagrams"
        else -> throw NotFoundException("Project plugin unavailable")
    }
    fun authorize(email: String, plugin: String, projectId: UUID, write: Boolean = false): User {
        val actor = projects.actor(email)
        projects.require(projectId,actor,write)
        plugins.requireTable(plugin,table(plugin),actor.role,write)
        return actor
    }
    fun list(email: String, plugin: String, projectId: UUID): List<ProjectAsset> {
        authorize(email,plugin,projectId)
        return rows(plugin,projectId)
    }
    private fun rows(plugin: String, projectId: UUID) = jdbc.query("SELECT * FROM ${table(plugin)} WHERE project_id = ? ORDER BY label,id LIMIT 101",
        {rs,_ -> ProjectAsset(rs.getObject("id",UUID::class.java),projectId,rs.getString("label"),rs.getString("remote_id"),rs.getString("payload_json"),rs.getTimestamp("updated_at").toInstant(),rs.getLong("revision"))},projectId)
    fun get(email: String, plugin: String, projectId: UUID, id: UUID) = list(email,plugin,projectId).find { it.id == id } ?: throw NotFoundException("Project link unavailable")
    @Transactional
    fun save(email: String, plugin: String, projectId: UUID, id: UUID?, label: String, remoteId: String, payload: String, revision: Long): ProjectAsset {
        authorize(email,plugin,projectId,true)
        if (label.isBlank() || label.length > 120 || label.any(Char::isISOControl) || remoteId.length > 2000 || payload.length > 100000)
            throw BadRequestException("Use a name of 1–120 characters and bounded project content")
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",org.springframework.jdbc.core.RowCallbackHandler {},"project-asset:$plugin:$projectId")
        val existing = rows(plugin,projectId)
        val old = id?.let { key -> existing.find { it.id == key } ?: throw NotFoundException("Project link unavailable") }
        if ((old?.revision ?: -1) != revision) throw ConflictException("This project link changed. Reload before saving.")
        if (old == null && existing.size >= 100) throw BadRequestException("A project supports up to 100 links per plugin")
        if (plugin != "mermaid" && existing.any { it.id != id && it.remoteId == remoteId }) throw ConflictException("This resource is already linked to the project")
        val key = id ?: UUID.randomUUID(); val now = Instant.now()
        if (old == null) jdbc.update("INSERT INTO ${table(plugin)} (id,project_id,label,remote_id,payload_json,updated_at,revision) VALUES (?,?,?,?,?,?,0)",key,projectId,label.trim(),remoteId,payload,java.sql.Timestamp.from(now))
        else if (jdbc.update("UPDATE ${table(plugin)} SET label=?,remote_id=?,payload_json=?,updated_at=?,revision=revision+1 WHERE id=? AND project_id=? AND revision=?",label.trim(),remoteId,payload,java.sql.Timestamp.from(now),key,projectId,revision) != 1)
            throw ConflictException("This project link changed. Reload before saving.")
        return ProjectAsset(key,projectId,label.trim(),remoteId,payload,now,(old?.revision?.plus(1) ?: 0))
    }
    @Transactional
    fun remove(email: String, plugin: String, projectId: UUID, id: UUID, revision: Long) {
        authorize(email,plugin,projectId,true)
        if (jdbc.update("DELETE FROM ${table(plugin)} WHERE id=? AND project_id=? AND revision=?",id,projectId,revision) != 1) throw ConflictException("This project link changed. Reload before removing it.")
    }
}
