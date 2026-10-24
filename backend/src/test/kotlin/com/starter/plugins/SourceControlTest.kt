package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.plugins.sourcecontrol.*
import com.starter.plugins.integrations.*
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class SourceControlTest {
    private val mapper = jacksonObjectMapper()
    private val settings = mock(PluginSettingsRepository::class.java)
    private val configuration = mock(PluginConfigurationService::class.java)
    private val registry = PluginRegistry(listOf(GitHubPlugin(),GitLabPlugin(),GiteaPlugin()),settings)
    private val calls = mutableListOf<List<Any?>>()
    private var response = "{}"
    private val http = mock(IntegrationHttpClient::class.java) { call ->
        if (call.method.name == "scoped") {calls += call.arguments.toList(); IntegrationResponse(200,mapper.readTree(response))} else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val service = SourceControlService(registry,configuration,http)
    private fun enable(id: String,base: String? = null,repo: String = "team/repo") {
        `when`(settings.findById(id)).thenReturn(Optional.of(PluginSettings(id,true)))
        `when`(configuration.current(id)).thenReturn(mapOf("accessToken" to "private-token","repositories" to repo) + (base?.let { mapOf("baseUrl" to it) } ?: emptyMap()))
    }
    @Test fun `approved repositories are checked before making network calls`() {
        enable("github")
        assertThatThrownBy { service.resource("github","other/repo",null,"USER") }.isInstanceOf(ForbiddenException::class.java)
        assertThat(calls).isEmpty()
        assertThat(service.repositories("github","USER")).containsExactly("team/repo")
        assertThat(calls).isEmpty()
    }
    @Test fun `GitHub repository and pull requests use distinct paths and sanitized metadata`() {
        enable("github")
        response = """{"html_url":"https://github.com/team/repo","full_name":"team/repo","token":"not-returned"}"""
        assertThat(service.resource("github","TEAM/repo",null,"USER").title).isEqualTo("team/repo")
        assertThat(calls.last()[2]).isEqualTo("repos/team/repo")
        response = """{"html_url":"https://github.com/team/repo/pull/12","number":12,"title":"Fix","state":"closed","merged":true}"""
        assertThat(service.resource("github","team/repo",12,"USER").state).isEqualTo("merged")
        assertThat(calls.last()[2]).isEqualTo("repos/team/repo/pulls/12")
    }
    @Test fun `GitLab supports subgroup paths and merge request numbers`() {
        enable("gitlab",repo="group/sub/repo")
        response = """[{"web_url":"https://gitlab.com/group/sub/repo/-/merge_requests/8","iid":8,"title":"Build","state":"opened"}]"""
        assertThat(service.pulls("gitlab","group/sub/repo","USER").single().number).isEqualTo(8)
        assertThat(calls.last()[2] as String).isEqualTo("api/v4/projects/group%2Fsub%2Frepo/merge_requests?state=opened&per_page=30")
        assertThat((calls.last()[3] as Map<*,*>)["PRIVATE-TOKEN"]).isEqualTo("private-token")
    }
    @Test fun `Gitea supports a configured server and rejects unexpected resource hosts`() {
        enable("gitea","https://git.example.test/gitea")
        response = """{"html_url":"https://git.example.test/gitea/team/repo/pulls/1","number":1,"title":"Fix","state":"open"}"""
        assertThat(service.resource("gitea","team/repo",1,"USER").number).isEqualTo(1)
        assertThat(calls.last()[2]).isEqualTo("api/v1/repos/team/repo/pulls/1")
        response = """{"html_url":"https://attacker.test/link","number":1}"""
        assertThatThrownBy { service.resource("gitea","team/repo",1,"USER") }.isInstanceOf(IntegrationUnavailable::class.java)
    }
    @Test fun `disabled provider and malformed endpoints or repository paths are blocked`() {
        `when`(settings.findById("github")).thenReturn(Optional.empty())
        assertThatThrownBy { service.pulls("github","team/repo","USER") }.isInstanceOf(NotFoundException::class.java)
        assertThat(calls).isEmpty()
        listOf("http://git.example.test","https://user:secret@git.example.test","https://git.example.test/?secret=1","https://git.example.test/../private","https://git.example.test/%2e%2e").forEach {
            assertThatThrownBy { IntegrationHttpClient.endpoint(it) }.isInstanceOf(BadRequestException::class.java)
        }
        listOf("team/../repo","team/repo?token=secret","/repo","team/repo,../repo").forEach {
            assertThatThrownBy { SourceControlPlugin.repositories(it) }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `all accessible mode discovers provider repositories and selected mode stays local`() {
        enable("github")
        val all=mapOf("accessToken" to "private-token","repositoryAccess" to "ALL")
        `when`(configuration.current("github")).thenReturn(all)
        response="""[{"full_name":"team/one"},{"full_name":"team/two"}]"""
        assertThat(service.repositoryOptions("github",2,"USER").repositories).containsExactly("team/one","team/two")
        assertThat(calls.last()[2] as String).contains("user/repos?","page=2")
        response="""{"html_url":"https://github.com/team/other","full_name":"team/other"}"""
        assertThat(service.resource("github","team/other",null,"USER").repository).isEqualTo("team/other")
    }
    @Test fun `admin discovery works before activation without bypassing member activation`() {
        enable("gitlab",repo="group/one")
        `when`(settings.findById("gitlab")).thenReturn(Optional.empty())
        response="""[{"path_with_namespace":"group/sub/repo"}]"""
        assertThat(service.repositoryOptions("gitlab",1).repositories).containsExactly("group/sub/repo")
        assertThat(calls.last()[2] as String).startsWith("api/v4/projects?membership=true")
        assertThatThrownBy {service.repositoryOptions("gitlab",1,"USER")}.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy {service.repositoryOptions("gitlab",0)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `changed credentials invalidate provider responses`() {
        enable("github")
        val first=mapOf("accessToken" to "private-token","repositoryAccess" to "ALL")
        `when`(configuration.current("github")).thenReturn(first,first+("accessToken" to "rotated"))
        response="""[{"full_name":"team/repo"}]"""
        assertThatThrownBy {service.repositoryOptions("github",1,"USER")}.isInstanceOf(ConflictException::class.java)
    }
}
