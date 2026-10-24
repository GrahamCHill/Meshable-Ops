package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class ProjectSectionsTest {
    private val mapper = jacksonObjectMapper()
    private val records = mock(ProjectSettingsRepository::class.java)
    private val access = mock(AccessControlService::class.java)
    private val jdbc = mock(JdbcTemplate::class.java)
    private val groups = mock(WorkGroupRepository::class.java)
    private val service = ProjectSettingsService(records, groups, mock(UserRepository::class.java), mapper, access, jdbc)
    private var stored: ProjectSettingsRecord? = null
    init {
        `when`(records.findById(1)).thenAnswer { Optional.ofNullable(stored) }
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(ProjectSettingsRecord::class.java))).thenAnswer { call ->
            val row = call.arguments[0] as ProjectSettingsRecord
            row.revision = (stored?.revision ?: -1) + 1; stored = row; row
        }
        `when`(jdbc.queryForList("SELECT DISTINCT category FROM workspace_projects", String::class.java)).thenReturn(listOf("ACTIVE"))
    }
    @Test fun `legacy settings retain the original three section IDs and active default`() {
        val settings = mapper.readValue("{}", ProjectSettings::class.java)
        assertThat(settings.sections.map { it.id }).containsExactly("PROPOSAL", "ACTIVE", "ARCHIVED")
        assertThat(settings.defaultSectionId).isEqualTo("ACTIVE")
        ProjectSectionRules.validate(settings)
    }
    @Test fun `renaming reordering and adding sections preserves assigned IDs and selected default`() {
        val custom = ProjectSettings(sections = listOf(ProjectSection("prototype", " Prototypes "), ProjectSection("ACTIVE", "Active development"),
            ProjectSection("deployed", "Deployed"), ProjectSection("sunset", "Sunsetted")), defaultSectionId = "prototype")
        val result = service.save(ProjectSettingsView(custom, -1))
        assertThat(result.settings.sections.map { it.name }).containsExactly("Prototypes", "Active development", "Deployed", "Sunsetted")
        assertThat(result.settings.defaultSectionId).isEqualTo("prototype")
        assertThat(ProjectSectionRules.name(result.settings, "ACTIVE")).isEqualTo("Active development")
        assertThat(ProjectSectionRules.require(result.settings, "deployed")).isEqualTo("deployed")
        verify(jdbc).execute("SELECT pg_advisory_xact_lock(50272026)")
    }
    @Test fun `removing a populated section is rejected without saving settings`() {
        assertThatThrownBy { service.save(ProjectSettingsView(ProjectSettings(sections = listOf(ProjectSection("other", "Other")), defaultSectionId = "other"), -1)) }
            .isInstanceOf(BadRequestException::class.java).hasMessageContaining("Move projects")
        assertThat(stored).isNull()
    }
    @Test fun `stale section saves cannot overwrite a newer configuration`() {
        service.save(ProjectSettingsView(ProjectSettings(), -1))
        assertThatThrownBy { service.save(ProjectSettingsView(ProjectSettings(), -1)) }.isInstanceOf(ConflictException::class.java)
    }
    @Test fun `section names IDs counts and default membership are validated`() {
        val invalid = listOf(ProjectSettings(sections = emptyList()),
            ProjectSettings(sections = listOf(ProjectSection("ACTIVE", "Work"), ProjectSection("second", " work "))),
            ProjectSettings(sections = listOf(ProjectSection("ACTIVE", ""))),
            ProjectSettings(sections = listOf(ProjectSection("ACTIVE", "a".repeat(81)))),
            ProjectSettings(sections = listOf(ProjectSection("../bad", "Invalid"))),
            ProjectSettings(sections = listOf(ProjectSection("ACTIVE", "Work"), ProjectSection("ACTIVE", "Duplicate"))),
            ProjectSettings(defaultSectionId = "missing"),
            ProjectSettings(sections = (1..21).map { ProjectSection("s$it", "Section $it") }, defaultSectionId = "s1"))
        invalid.forEach { assertThatThrownBy { ProjectSectionRules.validate(it) }.isInstanceOf(BadRequestException::class.java) }
        assertThatThrownBy { ProjectSectionRules.require(ProjectSettings(), "unregistered") }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `administration feature checks run before settings or database operations`() {
        doThrow(ForbiddenException("Unavailable")).`when`(access).requireFeature("admin.projects")
        assertThatThrownBy { service.save(ProjectSettingsView(ProjectSettings(), -1)) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(jdbc, records)
    }
}
