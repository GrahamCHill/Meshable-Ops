package com.starter.plugins.sourcecontrol

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.*
import com.starter.plugins.*
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class SourceControlService(private val registry: PluginRegistry, private val configuration: PluginConfigurationService,
    private val http: IntegrationHttpClient) {
    private val ids = setOf("github", "gitlab", "gitea")
    fun repositories(id: String, role: String): List<String> = repositoryOptions(id,1,role).repositories
    fun repositoryOptions(id:String,page:Int,role:String?=null):RepositoryOptions {
        if(page !in 1..1000)throw BadRequestException("Choose a repository page between 1 and 1000")
        val config=if(role==null) connection(id) else configured(id,role)
        if(role!=null&&config["repositoryAccess"]!="ALL") {
            val selected=SourceControlPlugin.repositories(config.getValue("repositories"))
            return RepositoryOptions(selected.drop((page-1)*100).take(100),page,selected.size>page*100)
        }
        val path=when(id) {"github"->"user/repos?per_page=100&page=$page&sort=full_name&affiliation=owner,collaborator,organization_member";"gitlab"->"api/v4/projects?membership=true&per_page=100&page=$page&order_by=path&sort=asc";else->"api/v1/user/repos?limit=100&page=$page"}
        val nodes=http.scoped("GET",base(id,config),path,headers(id,config)).body ?: throw IntegrationUnavailable("Provider returned no repositories")
        if(!nodes.isArray)throw IntegrationUnavailable("Provider returned an unsupported repository list")
        val names=nodes.take(100).map {it.path(if(id=="gitlab")"path_with_namespace" else "full_name").asText()}
        names.forEach {SourceControlPlugin.repositories(it)}
        if(role!=null)registry.requireAvailable(id,role)
        if(configuration.current(id)!=config)throw ConflictException("Repository settings changed. Load the list again.")
        return RepositoryOptions(names.distinct(),page,nodes.size()>=100)
    }
    private fun connection(id:String):Map<String,String> {
        if(id !in ids)throw NotFoundException("Source control plugin unavailable")
        val config=configuration.current(id)
        if(config["accessToken"].isNullOrBlank()||id=="gitea"&&config["baseUrl"].isNullOrBlank())throw BadRequestException("Save the provider URL and access token first")
        IntegrationHttpClient.endpoint(base(id,config));return config
    }
    fun approvedFor(id:String,repository:String,role:String):String=approved(id,repository,configured(id,role))
    fun fingerprint(id:String,role:String):String=java.security.MessageDigest.getInstance("SHA-256").digest(configured(id,role).toSortedMap().entries.joinToString("\u0000"){"${it.key}=${it.value}"}.toByteArray()).joinToString(""){"%02x".format(it)}
    fun pulls(id: String, repository: String, role: String): List<DevelopmentResource> {
        val config = configured(id, role); val repo = approved(id, repository, config)
        val node = request(id, config, repo, null, true)
        if (!node.isArray) throw IntegrationUnavailable("Provider did not return pull requests")
        registry.requireAvailable(id, role)
        if(configuration.current(id)!=config)throw ConflictException("Repository settings changed. Load the list again.")
        return node.take(30).map { resource(id, repo, it, true, config) }
    }
    fun resource(id: String, repository: String, number: Int?, role: String): DevelopmentResource {
        if (number != null && number <= 0) throw BadRequestException("Use a positive pull request number")
        val config = configured(id, role); val repo = approved(id, repository, config)
        val node = request(id, config, repo, number, false)
        registry.requireAvailable(id, role)
        if(configuration.current(id)!=config)throw ConflictException("Repository settings changed. Load the resource again.")
        return resource(id, repo, node, number != null, config)
    }
    private fun configured(id: String, role: String): Map<String, String> {
        if (id !in ids) throw NotFoundException("Source control plugin unavailable")
        registry.requireAvailable(id, role)
        val values = configuration.current(id)
        if (registry.missingConfiguration(id, values).isNotEmpty()) throw BadRequestException("Complete this source control plugin's configuration first")
        registry.validateConfiguration(id, values)
        return values
    }
    private fun approved(id: String, repository: String, config: Map<String, String>):String {
        val valid=SourceControlPlugin.repositories(repository).singleOrNull() ?: throw BadRequestException("Choose one repository")
        if(config["repositoryAccess"]=="ALL")return valid
        return SourceControlPlugin.repositories(config.getValue("repositories")).find {it.equals(valid,ignoreCase=id=="github")} ?: throw ForbiddenException("This repository has not been approved by an administrator")
    }
    private fun base(id: String, config: Map<String, String>) = config["baseUrl"] ?: if (id == "github") "https://api.github.com" else "https://gitlab.com"
    private fun request(id: String, config: Map<String, String>, repo: String, number: Int?, list: Boolean): JsonNode {
        val path = if (id == "gitlab") "api/v4/projects/${encode(repo)}" + if (list) "/merge_requests?state=opened&per_page=30" else number?.let { "/merge_requests/$it" }.orEmpty()
            else (if (id == "gitea") "api/v1/" else "") + "repos/" + repo.split('/').joinToString("/") { encode(it) } + if (list) "/pulls?state=open&limit=30&per_page=30" else number?.let { "/pulls/$it" }.orEmpty()
        return http.scoped("GET", base(id, config), path, headers(id,config)).body ?: throw IntegrationUnavailable("Provider returned no repository metadata")
    }
    private fun headers(id:String,config:Map<String,String>) = when (id) {
            "gitlab" -> mapOf("PRIVATE-TOKEN" to config.getValue("accessToken"))
            "gitea" -> mapOf("Authorization" to "token ${config.getValue("accessToken")}")
            else -> mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}", "Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
        }
    private fun resource(id: String, repo: String, node: JsonNode, pull: Boolean, config: Map<String, String>): DevelopmentResource {
        val url = node.path(if (id == "gitlab") "web_url" else "html_url").asText()
        val uri = runCatching { URI(url) }.getOrNull()
        val apiHost = URI(base(id, config)).host
        val expected = if (apiHost == "api.github.com") "github.com" else apiHost.removePrefix("api.")
        if (uri == null || uri.scheme != "https" || uri.userInfo != null || uri.host !in setOf(apiHost, expected)) throw IntegrationUnavailable("Provider returned an unsupported resource URL")
        val state = when { !pull -> "repository"; node.path("merged").asBoolean() || node.path("merged_at").isTextual -> "merged"; else -> node.path("state").asText("unknown") }
        return DevelopmentResource(id, repo, if (pull) node.path(if (id == "gitlab") "iid" else "number").asInt().takeIf { it > 0 } ?: throw IntegrationUnavailable("Provider returned no request number") else null,
            (if (pull) node.path("title").asText() else node.path(if (id == "gitlab") "name_with_namespace" else "full_name").asText(repo)).take(300), url.take(2000), state.take(30))
    }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
data class DevelopmentResource(val providerId: String, val repository: String, val number: Int?, val title: String, val url: String, val state: String)
data class RepositoryOptions(val repositories:List<String>,val page:Int,val hasMore:Boolean)
