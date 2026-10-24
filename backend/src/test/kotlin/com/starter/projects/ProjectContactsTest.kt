package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.dto.BrandingPreferences
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.branding.BrandingPreferencesService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class ProjectContactsTest {
    private val mapper = jacksonObjectMapper()
    private val projects = mock(ProjectRepository::class.java)
    private val clients = mock(ClientRepository::class.java)
    private val groups = mock(WorkGroupRepository::class.java)
    private val actors = mock(PluginActor::class.java)
    private val features = mock(AccessControlService::class.java)
    private val settings = mock(ProjectSettingsService::class.java)
    private val branding = mock(BrandingPreferencesService::class.java)
    private val policy = ProjectAccess(projects, groups, actors, features, mapper)
    private val service = ProjectService(projects, clients, groups, mock(UserRepository::class.java), mock(ItemRepository::class.java),
        policy, features, settings, branding, mapper, mock(JdbcTemplate::class.java))
    private val actor = User(email = "lead@example.test")
    private val clientContact = Contact(name = "Client contact", email = "client@example.test")
    private val ownContact = Contact(name = "Project contact", email = "project@example.test")
    private val client = ClientRecord(detailsJson = mapper.writeValueAsString(ClientDetails(contacts = listOf(clientContact))))
    private val project = ProjectRecord(leadId = actor.id, clientId = client.id)
    init {
        `when`(features.features(actor)).thenReturn(setOf("projects"))
        `when`(actors.current(actor.email)).thenReturn(actor)
        `when`(projects.findById(project.id)).thenReturn(Optional.of(project))
        `when`(clients.findById(client.id)).thenReturn(Optional.of(client))
        `when`(branding.current()).thenReturn(BrandingPreferences())
        `when`(settings.current()).thenReturn(ProjectSettingsView(ProjectSettings(), -1))
    }
    private fun read(details: ProjectDetails): ProjectView {
        project.detailsJson = mapper.writeValueAsString(details)
        return service.get(actor.email, project.id)
    }
    @Test fun `client contacts stay current and project overrides replace them`() {
        val inherited = ProjectDetails(contactSource = ProjectContactSource.CLIENT)
        assertThat(read(inherited).effectiveContacts).containsExactly(clientContact)
        val updated = clientContact.copy(phone = "+44 20 1234 5678")
        client.detailsJson = mapper.writeValueAsString(ClientDetails(contacts = listOf(updated)))
        assertThat(read(inherited).effectiveContacts).containsExactly(updated)
        assertThat(read(ProjectDetails(contacts = listOf(ownContact), contactSource = ProjectContactSource.PROJECT)).effectiveContacts).containsExactly(ownContact)
        assertThat(read(ProjectDetails(contactSource = ProjectContactSource.PROJECT)).effectiveContacts).isEmpty()
    }
    @Test fun `projects without clients use only their own contacts`() {
        project.clientId = null
        val empty = read(ProjectDetails())
        assertThat(empty.inheritedContacts).isEmpty()
        assertThat(empty.effectiveContacts).isEmpty()
        assertThat(empty.details.contactSource).isEqualTo(ProjectContactSource.PROJECT)
        assertThat(read(ProjectDetails(contacts = listOf(ownContact))).effectiveContacts).containsExactly(ownContact)
        verifyNoInteractions(clients)
    }
    @Test fun `legacy project contacts are preserved as overrides and empty legacy projects inherit`() {
        val legacy = mapper.writeValueAsString(mapOf("contacts" to listOf(ownContact)))
        assertThat(mapper.readValue(legacy, ProjectDetails::class.java).contactSource).isEqualTo(ProjectContactSource.PROJECT)
        assertThat(mapper.readValue("{}", ProjectDetails::class.java).contactSource).isEqualTo(ProjectContactSource.CLIENT)
    }
    @Test fun `old global contacts are ignored while project tags remain readable`() {
        val tag = ProjectTag(name = "Technology")
        val old = mapper.writeValueAsString(mapOf("tags" to listOf(tag), "identityContacts" to listOf(clientContact)))
        val decoded = mapper.readValue(old, ProjectSettings::class.java)
        assertThat(decoded.tags).containsExactly(tag)
        assertThat(mapper.readTree(mapper.writeValueAsString(decoded)).has("identityContacts")).isFalse()
    }
}
