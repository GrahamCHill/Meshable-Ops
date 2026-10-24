package com.starter.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.AccessControlService
import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProjectSettingsService(private val records: ProjectSettingsRepository, private val groups: WorkGroupRepository,
    private val users: UserRepository, private val mapper: ObjectMapper, private val access: AccessControlService, private val jdbc: JdbcTemplate) {
    fun current() = records.findById(1).orElse(null)?.let { ProjectSettingsView(mapper.readValue(it.settingsJson, ProjectSettings::class.java), it.revision) } ?: ProjectSettingsView(ProjectSettings(), -1)
    fun groups() = groups.findAll().sortedBy { it.name }.map(::view)
    fun people() = users.findAll().filter { it.active && it.role != "EXTERNAL" }.map { ProjectPerson(it.id, it.name ?: it.username, it.email) }
    @Transactional
    fun save(request: ProjectSettingsView): ProjectSettingsView {
        access.requireFeature("admin.projects")
        // Shared project-save locks keep a section from being removed while it is being assigned.
        jdbc.execute("SELECT pg_advisory_xact_lock(50272026)")
        val value = request.settings
        ProjectSectionRules.validate(value)
        if(users.findAllById(value.sectionMoveUserIds).filter {it.active && it.role!="EXTERNAL"}.map {it.id}.toSet()!=value.sectionMoveUserIds)throw BadRequestException("Choose active internal users for moving projects")
        val assigned = jdbc.queryForList("SELECT DISTINCT category FROM workspace_projects", String::class.java).toSet()
        if (!value.sections.map { it.id }.toSet().containsAll(assigned)) throw BadRequestException("Move projects to another section before removing their current section")
        if (value.tags.size > 100 || value.tags.map { it.id }.distinct().size != value.tags.size || value.tags.map { it.name.trim().lowercase() }.distinct().size != value.tags.size) throw BadRequestException("Use up to 100 unique project tags")
        val known = groups.findAll().map { it.id }.toSet()
        value.tags.forEach { ProjectValidation.name(it.name, 80); if (it.description.length > 500 || !known.containsAll(it.groupIds)) throw BadRequestException("Use valid tag descriptions and groups") }
        val row = records.findById(1).orElse(null)
        if ((row?.revision ?: -1) != request.revision) throw ConflictException("Project settings changed. Reload before saving.")
        val saved = row ?: ProjectSettingsRecord()
        saved.settingsJson = mapper.writeValueAsString(value.copy(tags = value.tags.map { it.copy(name = it.name.trim()) }, sections = value.sections.map { it.copy(name = it.name.trim()) }))
        records.saveAndFlush(saved)
        return current()
    }
    @Transactional
    fun saveGroup(id: UUID?, request: WorkGroupInput): WorkGroupView {
        access.requireFeature("admin.projects")
        ProjectValidation.name(request.name, 100)
        if (request.description.length > 1000 || request.details.memberIds.size > 500 || request.details.tagIds.size > 100) throw BadRequestException("Group details exceed their limits")
        val internal = users.findAllById(request.details.memberIds).filter { it.active && it.role != "EXTERNAL" }.map { it.id }.toSet()
        if (internal != request.details.memberIds) throw BadRequestException("Choose active internal members")
        val row = id?.let { groups.findById(it).orElseThrow { NotFoundException("Group unavailable") } } ?: WorkGroupRecord()
        if (id != null && row.revision != request.revision) throw ConflictException("This group changed. Reload before saving.")
        val tags = current().settings.tags.map { it.id }.toSet()
        val priorTags = if (id == null) emptySet() else mapper.readValue(row.detailsJson, WorkGroupData::class.java).tagIds
        if (!(tags + priorTags).containsAll(request.details.tagIds)) throw BadRequestException("Choose existing tags")
        row.name = request.name.trim(); row.description = request.description; row.kind = request.kind; row.projectRole = request.projectRole
        row.detailsJson = mapper.writeValueAsString(request.details.copy(tagIds = request.details.tagIds.intersect(tags)))
        return view(groups.saveAndFlush(row))
    }
    private fun view(row: WorkGroupRecord) = WorkGroupView(row.id, row.name, row.description, row.kind, row.projectRole, mapper.readValue(row.detailsJson, WorkGroupData::class.java), row.revision)
}
