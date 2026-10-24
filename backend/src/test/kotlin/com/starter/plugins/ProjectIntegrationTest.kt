package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.jira.*
import com.starter.plugins.figma.*
import com.starter.plugins.mermaid.*
import com.starter.plugins.integrations.*
import com.starter.plugins.projectassets.*
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.projects.ProjectAccess
import com.starter.security.RateLimits
import org.springframework.jdbc.core.JdbcTemplate
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ProjectIntegrationTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val project=UUID.randomUUID();private val id=UUID.randomUUID()
    private val user=User(email="manager@example.test")
    private val config=mapOf("baseUrl" to "https://jira.example.test","email" to "reader@example.test","accessToken" to "canary-secret","projectKeys" to "OPS")
    @Test fun `new project plugins declare isolated tables and valid manifests`() {
        listOf(JiraPlugin(),FigmaPlugin(),MermaidPlugin()).forEach { PluginManifests.validate(it.manifest) }
        assertThatThrownBy { JiraPlugin().validateConfiguration(config+mapOf("baseUrl" to "https://user:secret@jira.example.test")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { JiraPlugin.projectKeys("OPS OR project = PRIVATE") }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `Jira metrics use categories deduplicate issues and retain explicit partial snapshots`() {
        fun issue(id:String,category:String,due:String?,points:Double) = mapper.readTree(mapper.writeValueAsString(mapOf("id" to id,"fields" to mapOf("status" to mapOf("name" to category,"statusCategory" to mapOf("key" to category)),"duedate" to due,"issuetype" to mapOf("name" to "Task"),"customfield_1" to points,"resolutiondate" to "2026-10-20T10:00:00Z"))))
        val done=issue("1","done","2026-10-01",3.0)
        val result=JiraMetricCalculator.calculate(listOf(done,done,issue("2","indeterminate","2026-10-23",5.0),issue("3","new",null,2.0)),"customfield_1",true,LocalDate.of(2026,10,24))
        assertThat(result.total).isEqualTo(3);assertThat(result.completed).isEqualTo(1);assertThat(result.inProgress).isEqualTo(1)
        assertThat(result.overdue).isEqualTo(1);assertThat(result.storyPoints).isEqualTo(10.0);assertThat(result.partial).isTrue()
        assertThat(result.completedLast30Days).isEqualTo(1)
    }
    @Test fun `Jira Cloud refresh follows enhanced cursor pagination and never copies issue bodies`() {
        val bodies=mutableListOf<String>();var page=0
        val http=mock(IntegrationHttpClient::class.java) { call ->
            if(call.method.name=="scoped") {
                assertThat(call.arguments[2]).isEqualTo("rest/api/3/search/jql");bodies+=call.arguments[4] as String;page++
                IntegrationResponse(200,mapper.readTree(if(page==1) """{"issues":[{"id":"1","fields":{"status":{"name":"Review","statusCategory":{"key":"indeterminate"}}}}],"isLast":false,"nextPageToken":"next"}""" else """{"issues":[{"id":"2","fields":{"status":{"name":"Done","statusCategory":{"key":"done"}}}}],"isLast":true}"""))
            } else RETURNS_DEFAULTS.answer(call)
        }
        val assets=mock(ProjectAssetStore::class.java) { call -> when(call.method.name) {
            "authorize" -> user
            "get" -> ProjectAsset(id,project,"Jira work","OPS","{}",Instant.now(),2)
            "save" -> ProjectAsset(id,project,"Jira work","OPS",call.arguments[6] as String,Instant.now(),3)
            else -> RETURNS_DEFAULTS.answer(call)
        } }
        val configuration=mock(PluginConfigurationService::class.java);val registry=mock(PluginRegistry::class.java)
        `when`(configuration.current("jira")).thenReturn(config);`when`(registry.missingConfiguration("jira",config)).thenReturn(emptyList())
        val result=JiraProjectService(assets,configuration,registry,http,mapper,mock(RateLimits::class.java)).sync(user.email,project,id,2)
        val metrics=result.metrics!!
        assertThat(metrics.total).isEqualTo(2);assertThat(metrics.completed).isEqualTo(1);assertThat(metrics.partial).isFalse()
        assertThat(mapper.readTree(bodies.last()).path("nextPageToken").asText()).isEqualTo("next")
        assertThat(mapper.writeValueAsString(result)).doesNotContain("canary-secret","fields")
    }
    @Test fun `unapproved Jira projects and stale refresh revisions stop before HTTP calls`() {
        val assets=mock(ProjectAssetStore::class.java);val configuration=mock(PluginConfigurationService::class.java);val registry=mock(PluginRegistry::class.java);val http=mock(IntegrationHttpClient::class.java)
        `when`(assets.authorize(user.email,"jira",project,true)).thenReturn(user)
        `when`(assets.get(user.email,"jira",project,id)).thenReturn(ProjectAsset(id,project,"Private","PRIVATE","{}",Instant.now(),2))
        `when`(configuration.current("jira")).thenReturn(config);`when`(registry.missingConfiguration("jira",config)).thenReturn(emptyList())
        val service=JiraProjectService(assets,configuration,registry,http,mapper,mock(RateLimits::class.java))
        assertThatThrownBy { service.sync(user.email,project,id,1) }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { service.sync(user.email,project,id,2) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(http)
    }
    @Test fun `Data Center refresh respects server page limits smaller than the requested page size`() {
        val dataCenter=config+mapOf("deployment" to "DATA_CENTER");val starts=mutableListOf<Int>();var page=0
        val http=mock(IntegrationHttpClient::class.java) { call -> if(call.method.name=="scoped") {
            assertThat(call.arguments[2]).isEqualTo("rest/api/2/search")
            starts+=mapper.readTree(call.arguments[4] as String).path("startAt").asInt();page++
            IntegrationResponse(200,mapper.readTree("""{"issues":[{"id":"$page","fields":{"status":{"name":"Done","statusCategory":{"key":"done"}}}}],"maxResults":1,"total":2}"""))
        } else RETURNS_DEFAULTS.answer(call) }
        val assets=mock(ProjectAssetStore::class.java) { call -> when(call.method.name) {
            "authorize"->user;"get"->ProjectAsset(id,project,"Data Center","OPS","{}",Instant.now(),0)
            "save"->ProjectAsset(id,project,"Data Center","OPS",call.arguments[6] as String,Instant.now(),1)
            else->RETURNS_DEFAULTS.answer(call)
        } }
        val configuration=mock(PluginConfigurationService::class.java);val registry=mock(PluginRegistry::class.java)
        `when`(configuration.current("jira")).thenReturn(dataCenter);`when`(registry.missingConfiguration("jira",dataCenter)).thenReturn(emptyList())
        val result=JiraProjectService(assets,configuration,registry,http,mapper,mock(RateLimits::class.java)).sync(user.email,project,id,0)
        assertThat(starts).containsExactly(0,1);assertThat(result.metrics!!.total).isEqualTo(2)
    }
    @Test fun `Figma refresh reads only approved metadata and does not retain provider user profiles`() {
        val url="https://www.figma.com/design/Abcdef12345/Portal"
        val assets=mock(ProjectAssetStore::class.java) { call -> when(call.method.name) {
            "authorize"->user;"get"->ProjectAsset(id,project,"Design",url,"{}",Instant.now(),0)
            "save"->ProjectAsset(id,project,"Design",url,call.arguments[6] as String,Instant.now(),1)
            else->RETURNS_DEFAULTS.answer(call)
        } }
        val configuration=mock(PluginConfigurationService::class.java)
        `when`(configuration.current("figma")).thenReturn(mapOf("accessToken" to "private-token","fileKeys" to "Abcdef12345"))
        val http=mock(IntegrationHttpClient::class.java) { call -> if(call.method.name=="scoped") {
            assertThat(call.arguments[1]).isEqualTo("https://api.figma.com");assertThat(call.arguments[2]).isEqualTo("v1/files/Abcdef12345/meta")
            IntegrationResponse(200,mapper.readTree("""{"file":{"name":"Portal","last_touched_at":"2026-10-24T10:00:00Z","version":"42","editorType":"figma","creator":{"email":"private-profile@example.test"}}}"""))
        } else RETURNS_DEFAULTS.answer(call) }
        val result=FigmaProjectService(assets,configuration,http,mapper,mock(RateLimits::class.java)).refresh(user.email,project,id,0)
        assertThat(result.metadata!!.name).isEqualTo("Portal")
        assertThat(mapper.writeValueAsString(result)).doesNotContain("private-token","private-profile@example.test")
    }
    @Test fun `Figma URLs cannot redirect metadata requests to arbitrary hosts and file access needs approval`() {
        val good="https://www.figma.com/design/Abcdef12345/Project?node-id=1-2"
        assertThat(FigmaUrls.key(good)).isEqualTo("Abcdef12345")
        listOf("http://www.figma.com/design/Abcdef12345/a","https://figma.com.evil.test/design/Abcdef12345/a","https://user:secret@figma.com/design/Abcdef12345/a","javascript:alert(1)").forEach { url -> assertThatThrownBy { FigmaUrls.key(url) }.isInstanceOf(BadRequestException::class.java) }
        val assets=mock(ProjectAssetStore::class.java);val configuration=mock(PluginConfigurationService::class.java);val http=mock(IntegrationHttpClient::class.java)
        `when`(assets.authorize(user.email,"figma",project,true)).thenReturn(user)
        `when`(assets.get(user.email,"figma",project,id)).thenReturn(ProjectAsset(id,project,"Design",good,"{}",Instant.now(),0))
        `when`(configuration.current("figma")).thenReturn(mapOf("accessToken" to "private-token","fileKeys" to "OtherFile123"))
        assertThatThrownBy { FigmaProjectService(assets,configuration,http,mapper,mock(RateLimits::class.java)).refresh(user.email,project,id,0) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(http)
    }
    @Test fun `project and plugin access are checked before reading integration data`() {
        val jdbc=mock(JdbcTemplate::class.java);val projects=mock(ProjectAccess::class.java);val registry=mock(PluginRegistry::class.java)
        `when`(projects.actor(user.email)).thenReturn(user)
        doThrow(NotFoundException("Project unavailable")).`when`(projects).require(project,user,false)
        val store=ProjectAssetStore(jdbc,projects,registry)
        assertThatThrownBy { store.list(user.email,"jira",project) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(jdbc,registry)
    }
}
