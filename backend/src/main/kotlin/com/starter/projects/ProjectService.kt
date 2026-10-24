package com.starter.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.AccessControlService
import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.branding.BrandingPreferencesService
import com.starter.service.storage.normalizeStorageFolder
import com.starter.service.storage.normalizeStorageObject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProjectService(private val projects: ProjectRepository, private val clients: ClientRepository, private val groups: WorkGroupRepository,
    private val users: UserRepository, private val items: ItemRepository, private val policy: ProjectAccess,
    private val access: AccessControlService, private val settings: ProjectSettingsService, private val branding: BrandingPreferencesService,
    private val mapper: ObjectMapper, private val jdbc: JdbcTemplate) {
    fun people(email: String): List<ProjectPerson> { policy.actor(email); return users.findAll().filter { it.active && it.role != "EXTERNAL" }.sortedBy { it.name ?: it.username }.map { ProjectPerson(it.id, it.name ?: it.username, it.email) } }
    fun options(email: String): Map<String, Any> { policy.actor(email); return mapOf("tags" to settings.current().settings.tags, "groups" to groups.findAll().map { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "kind" to it.kind, "projectRole" to it.projectRole) }, "identityName" to branding.current().applicationName) }
    fun list(email: String, query: String = "", category: ProjectCategory? = null, tags: Set<UUID> = emptySet()): List<ProjectSummary> {
        val actor = policy.actor(email)
        if (query.length > 200 || tags.size > 20) throw BadRequestException("Use a search up to 200 characters and up to 20 tag filters")
        val args = mutableListOf<Any>(*ProjectAccessSql.args(actor))
        val conditions = mutableListOf(ProjectAccessSql.predicate())
        if (category != null) { conditions += "p.category = ?"; args += category.name }
        if (query.isNotBlank()) { conditions += "(p.name ILIKE ? ESCAPE '\\' OR COALESCE(c.name, '') ILIKE ? ESCAPE '\\' OR CAST(p.details_json AS jsonb)->>'description' ILIKE ? ESCAPE '\\')"; val term = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; repeat(3) { args += term } }
        tags.forEach { conditions += "jsonb_exists(COALESCE(CAST(p.details_json AS jsonb)->'tagIds','[]'::jsonb), ?)"; args += it.toString() }
        val identity = branding.current().applicationName
        return jdbc.query("SELECT p.id, p.name, p.client_id, c.name AS client_name, p.category, p.details_json, p.updated_at FROM workspace_projects p LEFT JOIN workspace_clients c ON c.id = p.client_id WHERE ${conditions.joinToString(" AND ")} ORDER BY p.updated_at DESC, p.id LIMIT 200",
            { rs, _ -> ProjectSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getObject("client_id", UUID::class.java), rs.getString("client_name") ?: identity,
                ProjectCategory.valueOf(rs.getString("category")), mapper.readValue(rs.getString("details_json"), ProjectDetails::class.java).tagIds, rs.getTimestamp("updated_at").toInstant()) }, *args.toTypedArray())
    }
    fun get(email: String, id: UUID): ProjectView = view(policy.require(id, policy.actor(email)), policy.actor(email))
    @Transactional
    fun save(email: String, id: UUID?, input: ProjectInput): ProjectView {
        val actor = policy.actor(email)
        val row = id?.let { policy.require(it, actor, true) } ?: ProjectRecord(ownerId = actor.id, leadId = actor.id)
        if (id != null && row.revision != input.revision) throw ConflictException("This project changed. Reload before saving.")
        ProjectValidation.name(input.name)
        val value = input.details
        if (value.description.length > 12000 || value.members.size > 300 || value.groupIds.size > 30 || value.tagIds.size > 50 || value.storage.size > 100 || value.members.map { it.userId }.distinct().size != value.members.size) throw BadRequestException("Project details exceed their limits")
        val people = value.members.map { it.userId }.toSet() + input.leadId
        if (users.findAllById(people).filter { it.active && it.role != "EXTERNAL" }.map { it.id }.toSet() != people) throw BadRequestException("Choose active internal project members and a lead")
        if (groups.findAllById(value.groupIds).filter { it.kind == GroupKind.TEAM }.map { it.id }.toSet() != value.groupIds) throw BadRequestException("Choose explicit team groups for project access; department tags do not grant access")
        if (!settings.current().settings.tags.map { it.id }.containsAll(value.tagIds)) throw BadRequestException("Choose existing project tags")
        input.clientId?.let { client(actor, it) }
        ProjectValidation.contacts(value.contacts); ProjectValidation.links(value.links)
        val linked = value.storage.map { link ->
            ProjectValidation.name(link.label)
            val path = if (link.directory) normalizeStorageFolder(link.path) else normalizeStorageObject(link.path)
            val normalized = link.copy(path = path)
            if (!visibleStorage(actor, normalized)) throw ForbiddenException("A linked storage path is unavailable to your account")
            normalized
        }
        if (linked.map { it.id }.distinct().size != linked.size) throw BadRequestException("Storage link IDs must be unique")
        val hidden = if (id == null) emptyList() else policy.details(row).storage.filter { !visibleStorage(actor, it) }
        row.name = input.name.trim(); row.leadId = input.leadId; row.clientId = input.clientId; row.category = input.category
        row.detailsJson = mapper.writeValueAsString(value.copy(storage = (hidden + linked).distinctBy { it.id })); row.updatedAt = Instant.now()
        if (id == null && policy.role(actor, row) !in setOf(ProjectRole.TEAM_LEAD, ProjectRole.MANAGER)) throw BadRequestException("Include yourself as the project lead or a manager when creating a project")
        return view(projects.saveAndFlush(row), actor)
    }
    fun members(email: String, id: UUID): List<ProjectPerson> { val row = policy.require(id, policy.actor(email)); return users.findAllById(policy.members(row)).filter { it.active && it.role != "EXTERNAL" && policy.canRead(it, row) }.map { ProjectPerson(it.id, it.name ?: it.username, it.email) } }
    fun clients(email: String): List<ClientView> {
        val actor = policy.actor(email)
        val visible = jdbc.query("SELECT DISTINCT p.client_id FROM workspace_projects p WHERE p.client_id IS NOT NULL AND ${ProjectAccessSql.predicate()}", { rs, _ -> rs.getObject(1, UUID::class.java) }, *ProjectAccessSql.args(actor)).toSet()
        return clients.findAll().filter { Roles.admin(actor.role) || it.ownerId == actor.id || it.id in visible }.sortedBy { it.name }.map { clientView(it, actor) }
    }
    fun getClient(email: String, id: UUID) = clientView(client(policy.actor(email), id), policy.actor(email))
    @Transactional
    fun saveClient(email: String, id: UUID?, input: ClientInput): ClientView {
        val actor = policy.actor(email); ProjectValidation.name(input.name)
        ProjectValidation.contacts(input.details.contacts); ProjectValidation.links(input.details.links)
        if (input.details.description.length > 12000) throw BadRequestException("Client descriptions are limited to 12000 characters")
        val row = id?.let { client(actor, it).also { value -> if (value.ownerId != actor.id && !Roles.admin(actor.role)) throw ForbiddenException("Only the client owner or an administrator can change inherited client details") } } ?: ClientRecord(ownerId = actor.id)
        if (id != null && row.revision != input.revision) throw ConflictException("This client changed. Reload before saving.")
        row.name = input.name.trim(); row.detailsJson = mapper.writeValueAsString(input.details)
        return clientView(clients.saveAndFlush(row), actor)
    }
    fun visibleStorage(actor: User, link: ProjectStorageLink): Boolean {
        val root = items.findById(link.folderId).orElse(null) ?: return false
        return root.accessible && root.storageConnection?.enabled != false && access.storage(actor).allows(link.folderId, link.path, link.directory)
    }
    private fun client(actor: User, id: UUID): ClientRecord {
        val row = clients.findById(id).orElse(null) ?: throw NotFoundException("Client unavailable")
        if (row.ownerId == actor.id || Roles.admin(actor.role)) return row
        val allowed = jdbc.query("SELECT p.id FROM workspace_projects p WHERE p.client_id = ? AND ${ProjectAccessSql.predicate()} LIMIT 1", { rs, _ -> rs.getObject(1, UUID::class.java) }, id, *ProjectAccessSql.args(actor))
        if (allowed.isEmpty()) throw NotFoundException("Client unavailable")
        return row
    }
    private fun clientView(row: ClientRecord, actor: User) = ClientView(row.id, row.name, mapper.readValue(row.detailsJson, ClientDetails::class.java), row.revision, row.ownerId == actor.id || Roles.admin(actor.role))
    private fun view(row: ProjectRecord, actor: User): ProjectView {
        val inherited = row.clientId?.let { clients.findById(it).orElse(null) }
        val contacts = inherited?.let { mapper.readValue(it.detailsJson, ClientDetails::class.java).contacts } ?: settings.current().settings.identityContacts
        return ProjectView(row.id, row.name, row.clientId, inherited?.name ?: branding.current().applicationName, row.leadId, row.category,
            policy.details(row).let { it.copy(storage = it.storage.filter { link -> visibleStorage(actor, link) }) }, contacts,
            row.revision, policy.role(actor, row) != ProjectRole.MEMBER, row.updatedAt)
    }
}
