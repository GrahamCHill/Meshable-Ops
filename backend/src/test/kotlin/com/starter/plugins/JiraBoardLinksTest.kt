package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.jira.*
import com.starter.plugins.integrations.*
import com.starter.plugins.projectassets.*
import com.starter.security.RateLimits
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID

class JiraBoardLinksTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val actor=User(email="manager@example.test")
    private val project=UUID.randomUUID();private val id=UUID.randomUUID()
    private var payload="""{"total":3,"completed":1}""";private var revision=4L
    private val config=mapOf("baseUrl" to "https://jira.example.test/context","email" to "reader@example.test","accessToken" to "secret","projectKeys" to "OPS")
    private val configuration=mock(PluginConfigurationService::class.java)
    private val registry=mock(PluginRegistry::class.java)
    private val paths=mutableListOf<String>()
    private val http=mock(IntegrationHttpClient::class.java) {call->if(call.method.name=="scoped") {
        val path=call.arguments[2] as String;paths+=path
        IntegrationResponse(200,mapper.readTree(if(path.startsWith("rest/agile/1.0/board?")) """{"values":[{"id":12,"name":"Delivery board","type":"kanban"}],"isLast":true}""" else """{"issues":[],"isLast":true}"""))
    }else RETURNS_DEFAULTS.answer(call)}
    private val assets=mock(ProjectAssetStore::class.java) {call->when(call.method.name) {
        "authorize"->actor
        "get"->ProjectAsset(id,project,"Jira delivery","OPS",payload,Instant.now(),revision)
        "save"->{payload=call.arguments[6] as String;revision++;ProjectAsset(id,project,"Jira delivery","OPS",payload,Instant.now(),revision)}
        else->RETURNS_DEFAULTS.answer(call)
    }}
    private val service=JiraProjectService(assets,configuration,registry,http,mapper,mock(RateLimits::class.java))
    init {`when`(configuration.current("jira")).thenReturn(config);`when`(registry.missingConfiguration("jira",config)).thenReturn(emptyList())}
    @Test fun `board selection is scoped to approved project and survives metric refresh`() {
        val result=service.setBoard(actor.email,project,id,JiraBoardInput(4,12))
        assertThat(paths.single()).isEqualTo("rest/agile/1.0/board?projectKeyOrId=OPS&maxResults=50&startAt=0")
        assertThat(result.boardUrl).isEqualTo("https://jira.example.test/context/secure/RapidBoard.jspa?rapidView=12")
        assertThat(result.metrics!!.total).isEqualTo(3)
        val refreshed=service.sync(actor.email,project,id,5)
        assertThat(refreshed.boardId).isEqualTo(12L);assertThat(refreshed.metrics!!.total).isZero()
    }
    @Test fun `foreign and stale board choices never save`() {
        assertThatThrownBy {service.setBoard(actor.email,project,id,JiraBoardInput(3,12))}.isInstanceOf(ConflictException::class.java)
        assertThat(paths).isEmpty()
        assertThatThrownBy {service.setBoard(actor.email,project,id,JiraBoardInput(4,99))}.isInstanceOf(BadRequestException::class.java)
        assertThat(revision).isEqualTo(4L)
    }
    @Test fun `clearing board shortcut preserves existing snapshots without provider calls`() {
        payload="""{"total":3,"boardId":12}"""
        val result=service.setBoard(actor.email,project,id,JiraBoardInput(4,null))
        assertThat(result.boardUrl).isNull();assertThat(result.boardId).isNull();assertThat(result.metrics!!.total).isEqualTo(3)
        assertThat(paths).isEmpty()
    }
}
