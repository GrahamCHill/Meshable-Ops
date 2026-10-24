package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.integrations.*
import com.starter.plugins.sourcecontrol.*
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class SourceControlTrackingTest {
    private val mapper=jacksonObjectMapper()
    private val rows=mock(PluginSettingsRepository::class.java)
    private val config=mock(PluginConfigurationService::class.java)
    private val registry=PluginRegistry(listOf(GitHubPlugin(),GitLabPlugin(),GiteaPlugin()),rows)
    private val paths=mutableListOf<String>()
    private val responses=mutableMapOf<String,String>()
    private val http=mock(IntegrationHttpClient::class.java) {call->if(call.method.name=="scoped") {
        val path=call.arguments[2] as String;paths+=path
        IntegrationResponse(200,mapper.readTree(responses[path] ?: error("Unexpected provider path: $path")))
    } else RETURNS_DEFAULTS.answer(call)}
    private val service=SourceControlService(registry,config,http)
    private fun enable(id:String) {
        `when`(rows.findById(id)).thenReturn(Optional.of(PluginSettings(id,true)))
        `when`(config.current(id)).thenReturn(mapOf("accessToken" to "canary-secret","repositories" to "team/repo")+(if(id=="gitea")mapOf("baseUrl" to "https://git.example.test")else emptyMap()))
    }
    @Test fun `branch names with slashes are encoded as one API segment for all providers`() {
        for(id in listOf("github","gitlab","gitea")) {
            enable(id)
            val prefix=when(id){"gitlab"->"api/v4/projects/team%2Frepo";"gitea"->"api/v1/repos/team/repo";else->"repos/team/repo"}
            val url=when(id){"gitlab"->"https://gitlab.com/team/repo";"gitea"->"https://git.example.test/team/repo";else->"https://github.com/team/repo"}
            responses[prefix]="""{"html_url":"$url","web_url":"$url","full_name":"team/repo","name_with_namespace":"team/repo"}"""
            responses[prefix+(if(id=="gitlab") "/repository" else "")+"/branches/feature%2Fportal"]="""{"name":"feature/portal","protected":true}"""
            val result=service.trackedResource(id,"team/repo","BRANCH","feature/portal","USER")
            assertThat(result.state).isEqualTo("protected");assertThat(result.ref).isEqualTo("feature/portal")
            assertThat(result.url).startsWith(url+"/").endsWith("feature%2Fportal")
            assertThat(paths.last()).endsWith("branches/feature%2Fportal")
        }
    }
    @Test fun `invalid references and unapproved repositories stop before HTTP`() {
        enable("github")
        listOf("../secret","a b","feature//x","a@{b","a\\b").forEach {branch->assertThatThrownBy {service.trackedResource("github","team/repo","BRANCH",branch,"USER")}.isInstanceOf(BadRequestException::class.java)}
        assertThatThrownBy {service.trackedResource("github","private/other","BRANCH","main","USER")}.isInstanceOf(ForbiddenException::class.java)
        assertThat(paths).isEmpty()
    }
    @Test fun `GitHub deployments include the latest status without using remote status URLs`() {
        enable("github")
        responses["repos/team/repo"]="""{"html_url":"https://github.com/team/repo"}"""
        responses["repos/team/repo/deployments/42"]="""{"id":42,"environment":"production","ref":"main","statuses_url":"https://attacker.test/secret"}"""
        responses["repos/team/repo/deployments/42/statuses?per_page=1"]="""[{"state":"success","log_url":"https://attacker.test/secret","target_url":"https://attacker.test/secret"}]"""
        val result=service.trackedResource("github","team/repo","DEPLOYMENT","42","USER")
        assertThat(result.state).isEqualTo("success");assertThat(result.environment).isEqualTo("production");assertThat(result.ref).isEqualTo("main")
        assertThat(result.url).isEqualTo("https://github.com/team/repo/deployments")
        assertThat(mapper.writeValueAsString(result)).doesNotContain("canary-secret","attacker")
    }
    @Test fun `GitLab deployments use the approved project and provider status`() {
        enable("gitlab")
        responses["api/v4/projects/team%2Frepo"]="""{"web_url":"https://gitlab.com/team/repo"}"""
        responses["api/v4/projects/team%2Frepo/deployments/7"]="""{"id":7,"status":"running","ref":"release","environment":{"name":"staging"},"deployable":{"web_url":"https://gitlab.com/team/repo/-/jobs/99"}}"""
        val result=service.trackedResource("gitlab","team/repo","DEPLOYMENT","7","USER")
        assertThat(result.state).isEqualTo("running");assertThat(result.environment).isEqualTo("staging")
        assertThat(result.url).isEqualTo("https://gitlab.com/team/repo/-/jobs/99")
    }
    @Test fun `Gitea manual deployments are constrained to the approved repository`() {
        enable("gitea");responses["api/v1/repos/team/repo"]="""{"html_url":"https://git.example.test/team/repo"}"""
        val valid="https://git.example.test/team/repo/actions/runs/4"
        val result=service.trackedResource("gitea","team/repo","DEPLOYMENT","4","USER",valid,"staging","success")
        assertThat(result.manual).isTrue();assertThat(result.url).isEqualTo(valid)
        listOf("https://attacker.test/run","https://git.example.test/other/repo/run",valid+"?token=secret","https://git.example.test/team/repo/../other","bad url").forEach {url->
            assertThatThrownBy {service.trackedResource("gitea","team/repo","DEPLOYMENT","4","USER",url)}.isInstanceOf(BadRequestException::class.java)
        }
    }
}
