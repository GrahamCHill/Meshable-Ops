package com.starter.plugins.jira

import com.starter.plugins.*
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

@Component
class JiraPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("jira","Jira","1.0.0","Link projects to Jira and refresh delivery metrics without copying issues into Kanban.",
        compatibleVersions=listOf("1.0.0","1.1.0"),extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.SETTINGS,PluginExtension.DATABASE),
        categories=setOf("Development"),contextTargets=setOf("PROJECT"),capabilities=mapOf("jira.metrics" to "Jira project metrics"),
        tables=listOf(PluginTable("plugin_jira_projects","Jira project links and metric snapshots")),
        settingsFields=listOf(PluginSettingField("baseUrl","Jira API base URL",required=true,help="HTTPS Jira site, or https://api.atlassian.com/ex/jira/{cloudId} for scoped Cloud API tokens. Do not append /rest/api.",maxLength=1000),
            PluginSettingField("siteUrl","Jira browser URL",help="Optional Jira site address for links, such as https://team.atlassian.net. Required when the API base uses api.atlassian.com.",maxLength=1000),
            PluginSettingField("deployment","Deployment type",help="CLOUD (default) or DATA_CENTER."),
            PluginSettingField("email","Jira account email",help="Required for Cloud API-token authentication. Use a service account with access only to approved projects.",maxLength=254),
            PluginSettingField("accessToken","API token",PluginFieldType.SECRET,true,"Cloud API token or Data Center personal access token with read access.",4096),
            PluginSettingField("projectKeys","Approved project keys",required=true,help="Comma-separated Jira project keys local project managers may link.",maxLength=2000),
            PluginSettingField("storyPointsField","Story points field",help="Optional custom field ID, for example customfield_10016. Blank omits point totals.")))
    override fun validateConfiguration(values: Map<String,String>) {
        values["baseUrl"]?.let { IntegrationHttpClient.endpoint(it) }
        values["siteUrl"]?.let { IntegrationHttpClient.endpoint(it) }
        if (values["deployment"] !in setOf(null,"CLOUD","DATA_CENTER")) throw BadRequestException("Choose CLOUD or DATA_CENTER")
        values["projectKeys"]?.let(::projectKeys)
        if (values["storyPointsField"]?.matches(Regex("customfield_[0-9]{1,12}")) == false) throw BadRequestException("Use a Jira custom field ID")
    }
    override fun missingConfiguration(values: Map<String,String>) = super.missingConfiguration(values) +
        (if (values["deployment"] != "DATA_CENTER" && values["email"].isNullOrBlank()) listOf("Jira account email") else emptyList()) +
        (if (values["baseUrl"]?.let { java.net.URI(it).host } == "api.atlassian.com" && values["siteUrl"].isNullOrBlank()) listOf("Jira browser URL") else emptyList())
    companion object {
        fun projectKeys(raw: String): Set<String> {
            val keys = raw.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            if (keys.isEmpty() || keys.size > 100 || keys.any { !it.matches(Regex("[A-Z][A-Z0-9_]{0,39}")) }) throw BadRequestException("Choose up to 100 valid Jira project keys")
            return keys
        }
    }
}
