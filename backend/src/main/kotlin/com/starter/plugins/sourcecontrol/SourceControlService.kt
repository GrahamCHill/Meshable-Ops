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
    fun trackedResource(id: String, repository: String, kind: String, reference: String, role: String,
        manualUrl: String? = null, environment: String = "", manualState: String = "unknown"): TrackedDevelopmentResource {
        if(kind !in setOf("BRANCH","PULL_REQUEST","DEPLOYMENT"))throw BadRequestException("Choose a branch, pull request or deployment")
        if(reference.isBlank() || reference.length>200 || reference.any(Char::isISOControl))throw BadRequestException("Enter a resource reference of up to 200 characters")
        if(kind=="BRANCH" && (reference.startsWith('-') || reference.contains("..") || reference.contains("@{") || reference.any {it.isWhitespace() || it in "~^:?*[\\"} || reference.split('/').any {it.isEmpty() || it.startsWith('.') || it.endsWith('.') || it.endsWith(".lock")}))
            throw BadRequestException("Enter a valid Git branch name")
        val config=configured(id,role);val repo=approved(id,repository,config)
        if(kind=="PULL_REQUEST") {
            val number=reference.toIntOrNull()?.takeIf {it>0} ?: throw BadRequestException("Use a positive request number")
            val pull=resource(id,repo,number,role)
            return TrackedDevelopmentResource(pull.title,pull.url,pull.state)
        }
        val repositoryResource=resource(id,repo,null,role)
        val prefix=if(id=="gitlab") "api/v4/projects/${encode(repo)}" else (if(id=="gitea") "api/v1/" else "")+"repos/"+repo.split('/').joinToString("/") {encode(it)}
        fun fetch(path: String)=http.scoped("GET",base(id,config),path,headers(id,config)).body ?: throw IntegrationUnavailable("Provider returned no development metadata")
        val result=if(kind=="BRANCH") {
            val node=fetch(prefix+(if(id=="gitlab") "/repository/branches/" else "/branches/")+encode(reference))
            if(node.path("name").asText()!=reference)throw IntegrationUnavailable("Provider returned an unexpected branch")
            val path=when(id) {"gitlab"->"/-/tree/";"gitea"->"/src/branch/";else->"/tree/"}
            TrackedDevelopmentResource(reference,repositoryResource.url.trimEnd('/')+path+encode(reference),if(node.path("protected").asBoolean()) "protected" else "active",ref=reference)
        } else if(id=="gitea") {
            if(environment.length>120 || environment.any(Char::isISOControl) || manualState !in setOf("unknown","pending","running","success","failed","cancelled"))throw BadRequestException("Use a short deployment environment and supported status")
            val url=manualUrl?.let {runCatching {URI(it)}.getOrNull()} ?: throw BadRequestException("Link the deployment run URL for this Gitea repository")
            val root=URI(repositoryResource.url)
            if(url.scheme!="https" || url.userInfo!=null || url.host!=root.host || url.port!=root.port || url.query!=null || url.fragment!=null || !url.path.startsWith(root.path.trimEnd('/')+"/") || url.normalize()!=url || url.rawPath.contains('%') || url.toString().length>2000)
                throw BadRequestException("Use a deployment run URL inside this repository, without query parameters")
            TrackedDevelopmentResource("Deployment $reference",url.toString(),manualState,environment,manual=true)
        } else {
            val number=reference.toLongOrNull()?.takeIf {it>0} ?: throw BadRequestException("Use a positive deployment ID")
            val node=fetch("$prefix/deployments/$number")
            if(node.path("id").asLong()!=number)throw IntegrationUnavailable("Provider returned an unexpected deployment")
            val status=if(id=="github")fetch("$prefix/deployments/$number/statuses?per_page=1").firstOrNull() else null
            val env=if(id=="github")node.path("environment").asText() else node.path("environment").path("name").asText()
            val candidate=if(id=="github")status?.path("log_url")?.asText() else node.path("deployable").path("web_url").asText()
            val root=URI(repositoryResource.url);val candidateUri=candidate?.takeIf {it.length<=2000}?.let {runCatching {URI(it)}.getOrNull()}
            val runUrl=candidateUri?.takeIf {it.scheme=="https" && it.userInfo==null && it.host==root.host && it.port==root.port && it.path?.startsWith(root.path.trimEnd('/')+"/")==true && it.normalize()==it && !it.rawPath.contains('%') && it.query==null && it.fragment==null}?.toString()
            TrackedDevelopmentResource("Deployment #$number",runUrl ?: repositoryResource.url.trimEnd('/')+(if(id=="github") "/deployments" else "/-/environments"),
                (status?.path("state")?.asText() ?: node.path("status").asText("pending")).take(30),env.take(120),node.path("ref").asText().take(200))
        }
        registry.requireAvailable(id,role)
        if(configuration.current(id)!=config)throw ConflictException("Repository settings changed. Refresh the resource again.")
        return result
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

data class TrackedDevelopmentResource(val title: String, val url: String, val state: String, val environment: String = "", val ref: String = "", val manual: Boolean = false)
