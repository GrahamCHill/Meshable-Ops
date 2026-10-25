package com.starter.plugins.analytics

import com.starter.exception.BadRequestException
import com.starter.plugins.*
import org.springframework.stereotype.Component

enum class AnalyticsProvider(val id: String,val title: String,val resourceLabel: String) {
    GOOGLE_ADS("google-ads","Google Ads","Customer ID"), META_ADS("meta-ads","Meta Ads","Ad account ID"),
    GOOGLE_ANALYTICS("google-analytics","Google Analytics","GA4 property ID"),
    META_PAGE("meta-page-analytics","Meta Page Analytics","Facebook Page ID"),
    CLOUDFLARE("cloudflare-analytics","Cloudflare Analytics","Zone ID");
    val table get()="plugin_${id.replace('-','_')}_links"
    val google get()=this in setOf(GOOGLE_ADS,GOOGLE_ANALYTICS)
    val scopes get()=when(this) {
        GOOGLE_ADS,META_ADS -> setOf(AnalyticsFilter.ALL,AnalyticsFilter.CAMPAIGN)
        GOOGLE_ANALYTICS -> setOf(AnalyticsFilter.ALL,AnalyticsFilter.CAMPAIGN,AnalyticsFilter.PAGE)
        META_PAGE -> setOf(AnalyticsFilter.ALL)
        CLOUDFLARE -> setOf(AnalyticsFilter.ALL,AnalyticsFilter.PAGE)
    }
    fun resources(raw: String): Set<String> {
        val ids=raw.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val pattern=when(this) { GOOGLE_ADS -> Regex("[0-9]{10}"); CLOUDFLARE -> Regex("[a-fA-F0-9]{32}");else -> Regex("[0-9]{1,30}") }
        if(ids.isEmpty() || ids.size>100 || ids.any {!pattern.matches(it)})throw BadRequestException("Approve up to 100 valid ${resourceLabel.lowercase()} values, separated by commas")
        return ids
    }
    companion object {
        fun from(id: String)=entries.find {it.id==id} ?: throw com.starter.exception.NotFoundException("Analytics connector unavailable")
    }
}

abstract class AnalyticsPlugin(private val provider: AnalyticsProvider): WorkspacePlugin {
    override val manifest=PluginManifest(provider.id,provider.title,"1.0.0","Read reporting metrics and link approved campaigns, pages and sites to clients and projects.",
        compatibleVersions=listOf("1.0.0"),categories=setOf("Analytics"),contextTargets=setOf("PROJECT","CLIENT"),
        extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.DATABASE,PluginExtension.SETTINGS),
        capabilities=mapOf("${provider.id}.metrics" to "${provider.title} reporting"),
        adminMenu=PluginAdminMenu("analytics","Analytics"),tables=listOf(PluginTable(provider.table,"Scoped analytics links and snapshots")),
        settingsFields=buildList {
            add(PluginSettingField("approvedResources","Approved ${provider.resourceLabel.lowercase()} values",required=true,help="Comma-separated IDs local client owners and project managers may link. Restrict the provider credentials to these resources too. Google IDs use digits without hyphens.",maxLength=4000))
            if(provider.google) {
                add(PluginSettingField("clientId","Google OAuth client ID",required=true,maxLength=1000))
                add(PluginSettingField("clientSecret","Google OAuth client secret",PluginFieldType.SECRET,true,maxLength=4096))
                add(PluginSettingField("refreshToken","Google OAuth refresh token",PluginFieldType.SECRET,true,"Grant offline access with the adwords scope for Ads, or analytics.readonly for GA4. Credentials stay on the server.",4096))
            } else add(PluginSettingField("accessToken",if(provider==AnalyticsProvider.META_PAGE)"User or Page access token" else "Read-only API access token",PluginFieldType.SECRET,true,
                when(provider) { AnalyticsProvider.CLOUDFLARE -> "Use a scoped API token with Analytics: Read on the approved zones.";AnalyticsProvider.META_PAGE -> "Use a user token able to resolve approved Page tokens (pages_show_list), or a single Page token. Insights need read_insights and pages_read_engagement.";else -> "Use a Meta token with ads_read for the approved accounts. No advertising changes are made." },4096))
            if(provider==AnalyticsProvider.META_PAGE)add(PluginSettingField("resolvePageToken","Resolve each Page token",PluginFieldType.BOOLEAN,
                help="Enabled: resolve an approved Page's token from the configured user token on refresh, supporting multiple Pages. Disable when supplying one Page token directly.",defaultValue="true"))
            if(provider==AnalyticsProvider.GOOGLE_ADS) {
                add(PluginSettingField("developerToken","Google Ads developer token",PluginFieldType.SECRET,true,maxLength=4096))
                add(PluginSettingField("managerCustomerId","Manager customer ID",help="Optional 10-digit login-customer-id for a manager account."))
                add(PluginSettingField("apiVersion","Google Ads API version",defaultValue="v26",help="Pinned REST major version, for example v26. Review provider sunset dates before changing."))
            }
            if(provider in setOf(AnalyticsProvider.META_ADS,AnalyticsProvider.META_PAGE))add(PluginSettingField("apiVersion","Meta Graph API version",defaultValue="v26.0",help="Pinned Graph version, for example v26.0. Available Page metrics depend on the Page and API version."))
        })
    override fun validateConfiguration(values: Map<String,String>) {
        values["approvedResources"]?.let(provider::resources)
        values["managerCustomerId"]?.let {if(!it.matches(Regex("[0-9]{10}")))throw BadRequestException("Use a 10-digit manager customer ID")}
        values["apiVersion"]?.let {if(!it.matches(if(provider==AnalyticsProvider.GOOGLE_ADS)Regex("v[0-9]{2,3}") else Regex("v[0-9]{2,3}\\.0")))throw BadRequestException("Use a supported provider API version")}
    }
}
@Component class GoogleAdsPlugin: AnalyticsPlugin(AnalyticsProvider.GOOGLE_ADS)
@Component class MetaAdsPlugin: AnalyticsPlugin(AnalyticsProvider.META_ADS)
@Component class GoogleAnalyticsPlugin: AnalyticsPlugin(AnalyticsProvider.GOOGLE_ANALYTICS)
@Component class MetaPageAnalyticsPlugin: AnalyticsPlugin(AnalyticsProvider.META_PAGE)
@Component class CloudflareAnalyticsPlugin: AnalyticsPlugin(AnalyticsProvider.CLOUDFLARE)
