package com.starter.plugins.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.integrations.IntegrationUnavailable
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class CloudflareAnalyticsReports(private val http: IntegrationHttpClient,private val mapper: ObjectMapper) {
    fun fetch(config: Map<String,String>,s: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot {
        val pathVariable=if(s.filter==AnalyticsFilter.PAGE)", ${'$'}path: string" else ""
        val pathFilter=if(s.filter==AnalyticsFilter.PAGE)", clientRequestPath: ${'$'}path" else ""
        val variables=mutableMapOf("zone" to s.resourceId,"start" to "${from}T00:00:00Z","end" to "${to.plusDays(1)}T00:00:00Z")
        if(s.filter==AnalyticsFilter.PAGE)variables["path"]=s.selector
        val query="""query Metrics(${'$'}zone: string, ${'$'}start: Time, ${'$'}end: Time$pathVariable) {
            viewer { zones(filter: {zoneTag: ${'$'}zone}) { httpRequestsAdaptiveGroups(limit: 1, filter: {datetime_geq: ${'$'}start, datetime_lt: ${'$'}end, requestSource: "eyeball"$pathFilter}) { count sum { visits edgeResponseBytes } avg { sampleInterval } } } }
        }""".trimIndent()
        val result=http.scoped("POST","https://api.cloudflare.com","client/v4/graphql",mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}"),
            mapper.writeValueAsString(mapOf("query" to query,"variables" to variables))).body
            ?: throw IntegrationUnavailable("Cloudflare returned no analytics")
        if(result.path("errors").size()>0)throw IntegrationUnavailable("Cloudflare analytics are unavailable. Check Analytics: Read permissions, zone plan, date retention and dataset access.")
        val zones=array(result.path("data").path("viewer").path("zones"),1)
        if(zones.size!=1)throw IntegrationUnavailable("Cloudflare did not return the approved zone")
        val rows=array(zones.single().path("httpRequestsAdaptiveGroups"),1)
        val sampled=rows.any {(numeric(it.path("avg").path("sampleInterval")) ?: 1.0)>1}
        return AnalyticsSnapshot(from,to,listOf(AnalyticsMetric("requests","HTTP requests",total(rows) {it.path("count")}),
            AnalyticsMetric("visits","Visits",total(rows) {it.path("sum").path("visits")}),AnalyticsMetric("bytes","Data transferred",total(rows) {it.path("sum").path("edgeResponseBytes")},"BYTES")),
            notes=listOf("Cloudflare edge traffic uses UTC days. Visits are referred or direct entries, not unique visitors. Only end-user traffic is included. Availability and retention depend on the zone plan.")+
                if(sampled)listOf("Cloudflare sampled this adaptive dataset; displayed values are provider estimates.") else emptyList())
    }
}
