package com.starter.plugins.sourcecontrol

import com.starter.plugins.*
import com.starter.exception.BadRequestException
import com.starter.plugins.integrations.IntegrationHttpClient
import org.springframework.stereotype.Component

abstract class SourceControlPlugin(id: String, name: String, private val defaultBase: String) : WorkspacePlugin {
    override val manifest = PluginManifest(id, name, "1.0.0-a", "Link approved repositories, branches, requests and deployments to projects and boards.",
        compatibleVersions = listOf("1.0.0"),
        extensions = setOf(PluginExtension.API, PluginExtension.SETTINGS),
        defaultRoles = setOf("ADMIN"),
        capabilities = mapOf("source.repositories" to "Repository information", "source.pull-requests" to "Pull and merge requests", "source.branches" to "Branch metadata", "source.deployments" to "Deployment tracking"),
        features = listOf(PluginFeature("board-links", "Repository and PR links on boards", setOf("kanban.boards"))),
        settingsFields = listOf(
            PluginSettingField("baseUrl", "Service API base URL", required = defaultBase.isEmpty(), help = when(id) {"github" -> "Blank uses https://api.github.com. For Enterprise, include /api/v3."; "gitlab" -> "Blank uses https://gitlab.com. Do not include /api/v4."; else -> "Your Gitea HTTPS server URL; do not include /api/v1."}, maxLength = 1000),
            PluginSettingField("accessToken", "Access token", PluginFieldType.SECRET, true, "Grant read access only to the repositories selected below. Deployment refresh also requires read access to deployments.", 4096),
            PluginSettingField("repositoryAccess", "Repository access", help = "SELECTED (default) or ALL repositories accessible to the configured token."),
            PluginSettingField("repositories", "Selected repositories", help = "Choose repositories from the provider in Settings. Existing comma-separated owner/repo paths remain supported.", maxLength = 4096)))
    override fun validateConfiguration(values: Map<String, String>) {
        values["baseUrl"]?.let { IntegrationHttpClient.endpoint(it) }
        values["repositories"]?.let { repositories(it) }
        if(values["repositoryAccess"] !in setOf(null,"SELECTED","ALL"))throw BadRequestException("Choose all accessible or selected repositories")
    }
    override fun missingConfiguration(values:Map<String,String>):List<String> = super.missingConfiguration(values) +
        if(values["repositoryAccess"]!="ALL"&&values["repositories"].isNullOrBlank())listOf("Selected repositories")else emptyList()
    companion object {
        fun repositories(value: String): List<String> {
            val names = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (names.size !in 1..100 || names.any { it.length > 200 || it.split('/').size < 2 || it.split('/').any { part -> part in listOf(".","..") || !part.matches(Regex("[A-Za-z0-9_.-]+")) } })
                throw BadRequestException("Choose up to 100 valid owner/repository paths")
            return names.distinct()
        }
    }
}
@Component class GitHubPlugin : SourceControlPlugin("github", "GitHub", "https://api.github.com")
@Component class GitLabPlugin : SourceControlPlugin("gitlab", "GitLab", "https://gitlab.com")
@Component class GiteaPlugin : SourceControlPlugin("gitea", "Gitea", "")
