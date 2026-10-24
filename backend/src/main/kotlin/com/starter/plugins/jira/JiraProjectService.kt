package com.starter.plugins.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.*
import com.starter.plugins.integrations.*
import com.starter.plugins.projectassets.*
import com.starter.security.RateLimits
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.util.Base64
import java.util.UUID

data class JiraLinkInput(val key: String, val label: String = "")
data class JiraProjectView(val id: UUID, val key: String, val label: String, val url: String, val metrics: JiraMetrics?, val revision: Long,
    val boardId: Long? = null, val boardUrl: String? = null)
data class JiraBoardOption(val id: Long,val name: String,val type: String)
data class JiraBoardInput(val revision: Long,val boardId: Long? = null)

@Service
class JiraProjectService(private val assets: ProjectAssetStore, private val configuration: PluginConfigurationService,
    private val registry: PluginRegistry, private val http: IntegrationHttpClient, private val mapper: ObjectMapper, private val rates: RateLimits) {
    fun list(email: String, project: UUID): List<JiraProjectView> {
        val rows = assets.list(email,"jira",project)
        val config = configuration.current("jira")
        return rows.map { view(it,config) }
    }
    private fun configured(key: String): Map<String,String> {
        val values = configuration.current("jira")
        registry.validateConfiguration("jira",values)
        if (registry.missingConfiguration("jira",values).isNotEmpty()) throw BadRequestException("Complete the Jira plugin configuration first")
        if (key !in JiraPlugin.projectKeys(values.getValue("projectKeys"))) throw ForbiddenException("This Jira project has not been approved by an administrator")
        return values
    }
    fun link(email: String, project: UUID, input: JiraLinkInput): JiraProjectView {
        val actor = assets.authorize(email,"jira",project,true)
        rates.check("jira-link",actor.id.toString(),20,3600)
        val key = input.key.trim().uppercase()
        val config = configured(key)
        val remote = http.scoped("GET",config.getValue("baseUrl"),"rest/api/${version(config)}/project/$key",headers(config)).body
            ?: throw IntegrationUnavailable("Jira returned no project metadata")
        if (remote.path("key").asText().uppercase() != key) throw IntegrationUnavailable("Jira returned an unexpected project")
        unchanged(config)
        val label = input.label.trim().ifBlank { remote.path("name").asText(key).take(120) }
        return view(assets.save(email,"jira",project,null,label,key,"{}",-1),config)
    }
    fun sync(email: String, project: UUID, id: UUID, revision: Long): JiraProjectView {
        val actor = assets.authorize(email,"jira",project,true)
        rates.check("jira-sync",actor.id.toString(),10,3600)
        val row = assets.get(email,"jira",project,id)
        if (row.revision != revision) throw ConflictException("The Jira link changed. Reload before refreshing metrics.")
        val config = configured(row.remoteId)
        val fields = listOf("status","issuetype","duedate","resolutiondate") + listOfNotNull(config["storyPointsField"])
        val issues = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
        var token: String? = null; var start = 0; var more = true
        val seen = mutableSetOf<String>()
        for (page in 1..50) {
            val cloud = config["deployment"] != "DATA_CENTER"
            val body = mutableMapOf<String,Any>("jql" to "project = ${row.remoteId} ORDER BY id ASC","fields" to fields,"maxResults" to 100)
            if (cloud) token?.let { body["nextPageToken"] = it } else body["startAt"] = start
            val result = http.scoped("POST",config.getValue("baseUrl"),if (cloud) "rest/api/3/search/jql" else "rest/api/2/search",headers(config),mapper.writeValueAsString(body)).body
                ?: throw IntegrationUnavailable("Jira returned no issue metrics")
            val batch = result.path("issues")
            if (!batch.isArray || batch.size() > 100) throw IntegrationUnavailable("Jira returned an unsupported issue page")
            issues += batch.toList(); start += batch.size()
            if (cloud) {
                token = result.path("nextPageToken").takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()
                more = !result.path("isLast").asBoolean(token == null)
                if (more && (token == null || !seen.add(token!!) || token!!.length > 8000)) throw IntegrationUnavailable("Jira pagination did not advance")
            } else {
                more = if (result.has("total")) start < result.path("total").asInt() else batch.size() == 100
                if (more && batch.isEmpty) throw IntegrationUnavailable("Jira pagination did not advance")
            }
            if (!more) break
        }
        unchanged(config)
        val metrics = JiraMetricCalculator.calculate(issues,config["storyPointsField"],more)
        val payload=mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(metrics)
        mapper.readTree(row.payload).get("boardId")?.let {payload.set<com.fasterxml.jackson.databind.JsonNode>("boardId",it)}
        return view(assets.save(email,"jira",project,id,row.label,row.remoteId,mapper.writeValueAsString(payload),revision),config)
    }
    fun boards(email: String, project: UUID, id: UUID): List<JiraBoardOption> {
        val actor=assets.authorize(email,"jira",project)
        rates.check("jira-boards",actor.id.toString(),30,3600)
        val row=assets.get(email,"jira",project,id);val config=configured(row.remoteId)
        val result=boardOptions(config,row.remoteId)
        unchanged(config);assets.authorize(email,"jira",project)
        return result
    }
    fun setBoard(email: String, project: UUID, id: UUID, input: JiraBoardInput): JiraProjectView {
        val actor=assets.authorize(email,"jira",project,true)
        rates.check("jira-board-link",actor.id.toString(),20,3600)
        val row=assets.get(email,"jira",project,id)
        if(row.revision!=input.revision)throw ConflictException("The Jira link changed. Reload before selecting a board.")
        val config=configured(row.remoteId)
        if(input.boardId!=null && (input.boardId<=0 || boardOptions(config,row.remoteId).none {it.id==input.boardId}))throw BadRequestException("Choose a visible board belonging to this approved Jira project")
        unchanged(config)
        val payload=mapper.readTree(row.payload).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        if(input.boardId==null)payload.remove("boardId") else payload.put("boardId",input.boardId)
        return view(assets.save(email,"jira",project,id,row.label,row.remoteId,mapper.writeValueAsString(payload),input.revision),config)
    }
    private fun boardOptions(config: Map<String,String>,key: String): List<JiraBoardOption> {
        val result=mutableListOf<JiraBoardOption>();var start=0
        repeat(5) {
            val node=http.scoped("GET",config.getValue("baseUrl"),"rest/agile/1.0/board?projectKeyOrId=$key&maxResults=50&startAt=$start",headers(config)).body ?: throw IntegrationUnavailable("Jira returned no boards")
            val values=node.path("values")
            if(!values.isArray || values.size()>50)throw IntegrationUnavailable("Jira returned an unsupported board list")
            result+=values.map {JiraBoardOption(it.path("id").asLong(),it.path("name").asText().take(120),it.path("type").asText().take(30))}.filter {it.id>0}
            start+=values.size()
            if(node.path("isLast").asBoolean(values.size()<50) || values.isEmpty)return result.distinctBy {it.id}
        }
        return result.distinctBy {it.id}
    }
    fun remove(email: String, project: UUID, id: UUID, revision: Long) = assets.remove(email,"jira",project,id,revision)
    private fun version(config: Map<String,String>) = if (config["deployment"] == "DATA_CENTER") "2" else "3"
    private fun headers(config: Map<String,String>) = mapOf("Accept" to "application/json", "Authorization" to
        if (config["deployment"] == "DATA_CENTER") "Bearer ${config.getValue("accessToken")}" else "Basic " + Base64.getEncoder().encodeToString("${config.getValue("email")}:${config.getValue("accessToken")}".toByteArray(Charsets.UTF_8)))
    private fun unchanged(config: Map<String,String>) { if (configuration.current("jira") != config) throw ConflictException("Jira settings changed. Retry with the current connection.") }
    private fun view(row: ProjectAsset, config: Map<String,String>): JiraProjectView {
        val metrics = mapper.readTree(row.payload).takeIf { it.has("total") }?.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()?.let { it.remove("boardId");mapper.treeToValue(it,JiraMetrics::class.java) }
        val base = (config["siteUrl"] ?: config["baseUrl"]?.takeUnless { java.net.URI(it).host == "api.atlassian.com" })?.let { IntegrationHttpClient.endpoint(it).toString().trimEnd('/') }.orEmpty()
        val boardId=mapper.readTree(row.payload).path("boardId").asLong().takeIf {it>0}
        return JiraProjectView(row.id,row.remoteId,row.label,if (base.isEmpty()) "" else "$base/issues/?jql=project%3D${row.remoteId}",metrics,row.revision,
            boardId,if(boardId!=null && base.isNotEmpty()) "$base/secure/RapidBoard.jspa?rapidView=$boardId" else null)
    }
}

data class JiraSyncInput(val revision: Long)
@RestController @RequestMapping("/api/plugins/jira/projects/{projectId}/links")
class JiraProjectController(private val service: JiraProjectService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID) = service.list(user.username,projectId)
    @PostMapping fun link(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@RequestBody input: JiraLinkInput) = service.link(user.username,projectId,input)
    @PostMapping("/{id}/sync") fun sync(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestBody input: JiraSyncInput) = service.sync(user.username,projectId,id,input.revision)
    @GetMapping("/{id}/boards") fun boards(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID)=service.boards(user.username,projectId,id)
    @PutMapping("/{id}/board") fun board(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestBody input: JiraBoardInput)=service.setBoard(user.username,projectId,id,input)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestParam revision: Long) = service.remove(user.username,projectId,id,revision)
}
