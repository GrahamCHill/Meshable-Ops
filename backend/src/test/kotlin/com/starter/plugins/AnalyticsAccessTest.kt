package com.starter.plugins

import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.analytics.*
import com.starter.plugins.integrations.IntegrationUnavailable
import com.starter.projects.ProjectAccess
import com.starter.projects.ProjectService
import com.starter.security.RateLimits
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AnalyticsAccessTest {
    private val actor=User(email="owner@example.test")
    private val context=UUID.randomUUID();private val id=UUID.randomUUID();private val p=AnalyticsProvider.GOOGLE_ADS
    private val config=mapOf("approvedResources" to "1234567890","clientId" to "test-client","clientSecret" to "test-secret","refreshToken" to "test-refresh","developerToken" to "test-developer")
    private val store=mock(AnalyticsStore::class.java);private val configuration=mock(PluginConfigurationService::class.java)
    private val registry=mock(PluginRegistry::class.java);private val providers=mock(AnalyticsProviders::class.java)
    private val service=AnalyticsService(store,configuration,registry,providers,mock(RateLimits::class.java))
    private val selection=AnalyticsSelection("1234567890",AnalyticsFilter.CAMPAIGN,"123")
    private val row=AnalyticsRow(id,"Launch",AnalyticsPayload(selection),3)
    private val today=LocalDate.now(ZoneOffset.UTC)
    private val input=AnalyticsSyncInput(3,today.minusDays(7),today.minusDays(1))
    init {
        `when`(store.authorize(actor.email,p,AnalyticsContext.PROJECT,context,true)).thenReturn(actor)
        `when`(store.get(actor.email,p,AnalyticsContext.PROJECT,context,id)).thenReturn(row)
        `when`(configuration.current(p.id)).thenReturn(config)
        `when`(registry.missingConfiguration(p.id,config)).thenReturn(emptyList())
    }
    @Test fun `unauthorized project and client access fails before any provider call`() {
        doThrow(NotFoundException("Project unavailable")).`when`(store).authorize(actor.email,p,AnalyticsContext.PROJECT,context,true)
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input)}.isInstanceOf(NotFoundException::class.java)
        doThrow(ForbiddenException("Client owner required")).`when`(store).authorize(actor.email,p,AnalyticsContext.CLIENT,context,true)
        assertThatThrownBy {service.link(actor.email,p,AnalyticsContext.CLIENT,context,AnalyticsLinkInput("Launch",selection))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(providers,configuration)
    }
    @Test fun `unapproved resources and stale revisions cannot fetch or overwrite snapshots`() {
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input.copy(revision=2))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.link(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsLinkInput("Other",selection.copy(resourceId="9999999999")))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(providers)
        assertThat(mockingDetails(store).invocations.map {it.method.name}).doesNotContain("save")
    }
    @Test fun `settings changes and provider failures preserve the previous snapshot`() {
        `when`(providers.fetch(p,config,selection,input.from,input.to)).thenReturn(AnalyticsSnapshot(input.from,input.to,emptyList()))
        `when`(configuration.current(p.id)).thenReturn(config,config+("clientSecret" to "rotated"))
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input)}.isInstanceOf(ConflictException::class.java)
        `when`(configuration.current(p.id)).thenReturn(config)
        `when`(providers.fetch(p,config,selection,input.from,input.to)).thenThrow(IntegrationUnavailable("Provider offline"))
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input)}.isInstanceOf(IntegrationUnavailable::class.java)
        assertThat(mockingDetails(store).invocations.map {it.method.name}).doesNotContain("save")
    }
    @Test fun `revoking approval hides cached metrics and invalid dates never call a provider`() {
        `when`(store.list(actor.email,p,AnalyticsContext.PROJECT,context)).thenReturn(listOf(row,row.copy(id=UUID.randomUUID(),payload=AnalyticsPayload(selection.copy(resourceId="9999999999")))))
        assertThat(service.list(actor.email,p,AnalyticsContext.PROJECT,context).map {it.id}).containsExactly(id)
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input.copy(to=today))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.sync(actor.email,p,AnalyticsContext.PROJECT,context,id,input.copy(from=today.minusDays(60)))}.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(providers)
    }
    @Test fun `store authorization uses client owner checks and project management checks separately`() {
        val projects=mock(ProjectAccess::class.java);val clients=mock(ProjectService::class.java);val jdbc=mock(JdbcTemplate::class.java)
        `when`(projects.actor(actor.email)).thenReturn(actor)
        val secured=AnalyticsStore(jdbc,jacksonObjectMapper(),projects,clients,registry)
        doThrow(ForbiddenException("Client owner required")).`when`(clients).requireClient(actor.email,context,true)
        assertThatThrownBy {secured.authorize(actor.email,p,AnalyticsContext.CLIENT,context,true)}.isInstanceOf(ForbiddenException::class.java)
        assertThat(mockingDetails(projects).invocations.map {it.method.name}).doesNotContain("require");verifyNoInteractions(jdbc)
        secured.authorize(actor.email,p,AnalyticsContext.PROJECT,context,true)
        verify(projects).require(context,actor,true)
        verify(registry).requireTable(p.id,p.table,actor.role,true,false)
    }
}
