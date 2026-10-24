package com.starter.projects

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class ProjectCategory { ACTIVE, ARCHIVED, PROPOSAL }
enum class ProjectRole { MEMBER, TEAM_LEAD, MANAGER }
enum class GroupKind { TEAM, DEPARTMENT }
data class Contact(val id: UUID = UUID.randomUUID(), val name: String = "", val email: String = "", val phone: String = "", val position: String = "")
data class WebLink(val label: String, val url: String)
data class ProjectMember(val userId: UUID, val role: ProjectRole = ProjectRole.MEMBER)
data class ProjectStorageLink(val id: UUID = UUID.randomUUID(), val folderId: UUID, val path: String = "", val directory: Boolean = true, val label: String = "")
data class ProjectDetails(val description: String = "", val members: List<ProjectMember> = emptyList(), val groupIds: Set<UUID> = emptySet(),
    val tagIds: Set<UUID> = emptySet(), val contacts: List<Contact> = emptyList(), val links: List<WebLink> = emptyList(), val storage: List<ProjectStorageLink> = emptyList())
data class ClientDetails(val description: String = "", val contacts: List<Contact> = emptyList(), val links: List<WebLink> = emptyList())
data class WorkGroupData(val memberIds: Set<UUID> = emptySet(), val tagIds: Set<UUID> = emptySet())
data class ProjectTag(val id: UUID = UUID.randomUUID(), val name: String, val description: String = "", val groupIds: Set<UUID> = emptySet())
data class ProjectSettings(val tags: List<ProjectTag> = emptyList(), val identityContacts: List<Contact> = emptyList())

@Entity @Table(name = "workspace_clients")
class ClientRecord(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), var name: String = "",
    @get:JsonIgnore @Column(columnDefinition = "TEXT", nullable = false) var detailsJson: String = "{}", @Version var revision: Long = 0)
interface ClientRepository : JpaRepository<ClientRecord, UUID>

@Entity @Table(name = "workspace_projects", indexes = [Index(columnList = "client_id"), Index(columnList = "lead_id")])
class ProjectRecord(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), var clientId: UUID? = null,
    var leadId: UUID = UUID.randomUUID(), var name: String = "", @Enumerated(EnumType.STRING) var category: ProjectCategory = ProjectCategory.ACTIVE,
    @get:JsonIgnore @Column(columnDefinition = "TEXT", nullable = false) var detailsJson: String = "{}",
    var updatedAt: Instant = Instant.now(), @Version var revision: Long = 0)
interface ProjectRepository : JpaRepository<ProjectRecord, UUID>

@Entity @Table(name = "workspace_work_groups")
class WorkGroupRecord(@Id val id: UUID = UUID.randomUUID(), var name: String = "", @Column(length = 1000) var description: String = "",
    @Enumerated(EnumType.STRING) var kind: GroupKind = GroupKind.TEAM, @Enumerated(EnumType.STRING) var projectRole: ProjectRole = ProjectRole.MEMBER,
    @get:JsonIgnore @Column(columnDefinition = "TEXT", nullable = false) var detailsJson: String = "{}", @Version var revision: Long = 0)
interface WorkGroupRepository : JpaRepository<WorkGroupRecord, UUID>

@Entity @Table(name = "workspace_project_settings")
class ProjectSettingsRecord(@Id val id: Int = 1, @get:JsonIgnore @Column(columnDefinition = "TEXT", nullable = false) var settingsJson: String = "{}", @Version var revision: Long = 0)
interface ProjectSettingsRepository : JpaRepository<ProjectSettingsRecord, Int>

data class ClientInput(val name: String, val details: ClientDetails = ClientDetails(), val revision: Long = -1)
data class ClientView(val id: UUID, val name: String, val details: ClientDetails, val revision: Long, val canManage: Boolean)
data class ProjectInput(val name: String, val leadId: UUID, val clientId: UUID? = null, val category: ProjectCategory = ProjectCategory.ACTIVE,
    val details: ProjectDetails = ProjectDetails(), val revision: Long = -1)
data class ProjectView(val id: UUID, val name: String, val clientId: UUID?, val clientName: String, val leadId: UUID, val category: ProjectCategory,
    val details: ProjectDetails, val inheritedContacts: List<Contact>, val revision: Long, val canManage: Boolean, val updatedAt: Instant)
data class ProjectSummary(val id: UUID, val name: String, val clientId: UUID?, val clientName: String, val category: ProjectCategory, val tagIds: Set<UUID>, val updatedAt: Instant,
    val artwork: com.starter.service.ArtworkView = com.starter.service.ArtworkView())
data class WorkGroupInput(val name: String, val description: String = "", val kind: GroupKind = GroupKind.TEAM, val projectRole: ProjectRole = ProjectRole.MEMBER,
    val details: WorkGroupData = WorkGroupData(), val revision: Long = -1)
data class WorkGroupView(val id: UUID, val name: String, val description: String, val kind: GroupKind, val projectRole: ProjectRole, val details: WorkGroupData, val revision: Long)
data class ProjectSettingsView(val settings: ProjectSettings, val revision: Long)
data class ProjectPerson(val id: UUID, val name: String, val email: String)
