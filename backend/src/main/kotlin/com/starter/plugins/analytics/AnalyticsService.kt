package com.starter.plugins.analytics

import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import com.starter.security.RateLimits
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AnalyticsService(private val store: AnalyticsStore,private val configuration: PluginConfigurationService,
    private val registry: PluginRegistry,private val providers: AnalyticsProviders,private val rates: RateLimits) {
    fun options(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID): AnalyticsOptions {
        store.authorize(email,p,kind,context)
        val config=configuration.current(p.id)
        return AnalyticsOptions(p.resourceLabel,approved(p,config),p.scopes,registry.missingConfiguration(p.id,config).isEmpty())
    }
    fun list(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID): List<AnalyticsLink> {
        val rows=store.list(email,p,kind,context);val allowed=approved(p,configuration.current(p.id))
        return rows.filter {it.payload.selection.resourceId in allowed}.map {view(p,it)}
    }
    fun link(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID,input: AnalyticsLinkInput): AnalyticsLink {
        val actor=store.authorize(email,p,kind,context,true)
        rates.check("${p.id}-link",actor.id.toString(),40,3600)
        val selection=validateSelection(p,input.selection);val config=configured(p,selection)
        unchanged(p,config)
        return view(p,store.save(email,p,kind,context,null,input.label,AnalyticsPayload(selection),-1))
    }
    fun sync(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID,id: UUID,input: AnalyticsSyncInput): AnalyticsLink {
        val actor=store.authorize(email,p,kind,context,true)
        val today=LocalDate.now(ZoneOffset.UTC)
        if(input.to<input.from || input.from.year<2000 || input.to>=today || ChronoUnit.DAYS.between(input.from,input.to)>30 || p==AnalyticsProvider.CLOUDFLARE && input.from<today.minusDays(30))
            throw BadRequestException("Choose up to 31 completed calendar days. Cloudflare also requires a start within the last 30 days; provider plans may impose shorter retention.")
        val row=store.get(email,p,kind,context,id)
        if(row.revision!=input.revision)throw ConflictException("This analytics link changed. Reload before refreshing.")
        val selection=validateSelection(p,row.payload.selection);val config=configured(p,selection)
        rates.check("${p.id}-refresh",actor.id.toString(),30,3600)
        val snapshot=providers.fetch(p,config,selection,input.from,input.to)
        unchanged(p,config)
        return view(p,store.save(email,p,kind,context,id,row.label,AnalyticsPayload(selection,snapshot),input.revision))
    }
    fun remove(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID,id: UUID,revision: Long)=store.remove(email,p,kind,context,id,revision)
    private fun approved(p: AnalyticsProvider,config: Map<String,String>)=config["approvedResources"]?.takeIf {it.isNotBlank()}?.let(p::resources).orEmpty()
    private fun configured(p: AnalyticsProvider,selection: AnalyticsSelection): Map<String,String> {
        val values=configuration.current(p.id);registry.validateConfiguration(p.id,values)
        if(registry.missingConfiguration(p.id,values).isNotEmpty())throw BadRequestException("Complete the ${p.title} connector settings first")
        if(selection.resourceId !in approved(p,values))throw ForbiddenException("An administrator must approve this analytics resource first")
        return values
    }
    private fun unchanged(p: AnalyticsProvider,config: Map<String,String>) {if(configuration.current(p.id)!=config)throw ConflictException("Connector settings changed. Retry with the current connection.")}
    private fun view(p: AnalyticsProvider,row: AnalyticsRow): AnalyticsLink {
        val s=row.payload.selection
        val url=when(p) {
            AnalyticsProvider.GOOGLE_ADS -> "https://ads.google.com/aw/overview"
            AnalyticsProvider.META_ADS -> "https://adsmanager.facebook.com/adsmanager/manage/campaigns?act=${s.resourceId}"+(if(s.filter==AnalyticsFilter.CAMPAIGN)"&selected_campaign_ids=${s.selector}" else "")
            AnalyticsProvider.GOOGLE_ANALYTICS -> "https://analytics.google.com/analytics/web/#/p${s.resourceId}/reports/intelligenthome"
            AnalyticsProvider.META_PAGE -> "https://www.facebook.com/${s.resourceId}"
            AnalyticsProvider.CLOUDFLARE -> "https://dash.cloudflare.com/"
        }
        return AnalyticsLink(row.id,row.label,s,row.payload.snapshot,row.revision,url)
    }
    companion object {
        fun validateSelection(p: AnalyticsProvider,input: AnalyticsSelection): AnalyticsSelection {
            val value=input.copy(resourceId=input.resourceId.trim(),selector=input.selector.trim())
            p.resources(value.resourceId)
            if(value.filter !in p.scopes || value.resourceId.contains(',') || value.selector.length>500 || value.selector.any(Char::isISOControl))throw BadRequestException("Choose a supported analytics filter")
            if(value.filter==AnalyticsFilter.ALL && value.selector.isNotEmpty())throw BadRequestException("All-resource metrics do not use a selector")
            if(value.filter!=AnalyticsFilter.ALL && value.selector.isEmpty())throw BadRequestException("Enter the exact campaign or page selector")
            if(value.filter==AnalyticsFilter.CAMPAIGN && p!=AnalyticsProvider.GOOGLE_ANALYTICS && !value.selector.matches(Regex("[0-9]{1,30}")))throw BadRequestException("Use a numeric campaign ID")
            if(value.filter==AnalyticsFilter.PAGE && (!value.selector.startsWith('/') || value.selector.startsWith("//") || value.selector.contains('?') || value.selector.contains('#')))throw BadRequestException("Use an exact page path beginning with /, without a query or fragment")
            return value
        }
    }
}

@RestController
@RequestMapping("/api/plugins/{pluginId:google-ads|meta-ads|google-analytics|meta-page-analytics|cloudflare-analytics}/contexts/{kind}/{contextId}/links")
class AnalyticsController(private val service: AnalyticsService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,@PathVariable contextId: UUID)=service.list(user.username,AnalyticsProvider.from(pluginId),kind,contextId)
    @GetMapping("/options") fun options(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,@PathVariable contextId: UUID)=service.options(user.username,AnalyticsProvider.from(pluginId),kind,contextId)
    @PostMapping fun link(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,@PathVariable contextId: UUID,@RequestBody input: AnalyticsLinkInput)=service.link(user.username,AnalyticsProvider.from(pluginId),kind,contextId,input)
    @PostMapping("/{id}/sync") fun sync(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,@PathVariable contextId: UUID,@PathVariable id: UUID,@RequestBody input: AnalyticsSyncInput)=service.sync(user.username,AnalyticsProvider.from(pluginId),kind,contextId,id,input)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,@PathVariable contextId: UUID,@PathVariable id: UUID,@RequestParam revision: Long)=service.remove(user.username,AnalyticsProvider.from(pluginId),kind,contextId,id,revision)
}
