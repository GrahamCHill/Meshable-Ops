package com.starter.plugins.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.integrations.IntegrationUnavailable
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class MetaAnalyticsReports(private val http: IntegrationHttpClient,private val mapper: ObjectMapper) {
    private fun headers(config: Map<String,String>)=mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}")
    fun ads(config: Map<String,String>,s: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot {
        val fields="account_id,account_currency,impressions,clicks,inline_link_clicks,reach,spend"+if(s.filter==AnalyticsFilter.CAMPAIGN)",campaign_id" else ""
        val params=mutableMapOf("fields" to fields,"level" to if(s.filter==AnalyticsFilter.CAMPAIGN)"campaign" else "account",
            "time_range" to mapper.writeValueAsString(mapOf("since" to from.toString(),"until" to to.toString())),"limit" to "2")
        if(s.filter==AnalyticsFilter.CAMPAIGN)params["filtering"]=mapper.writeValueAsString(listOf(mapOf("field" to "campaign.id","operator" to "IN","value" to listOf(s.selector))))
        val query=params.entries.joinToString("&") {"${it.key}=${encoded(it.value)}"}
        val result=http.scoped("GET","https://graph.facebook.com","${config["apiVersion"] ?: "v26.0"}/act_${s.resourceId}/insights?$query",headers(config)).body
            ?: throw IntegrationUnavailable("Meta Ads returned no report")
        if(result.has("error") || result.path("paging").path("next").asText().isNotEmpty())throw IntegrationUnavailable("Meta Ads could not return a complete aggregate report")
        val rows=array(result.path("data"),1)
        if(rows.any {it.path("account_id").asText()!=s.resourceId || s.filter==AnalyticsFilter.CAMPAIGN && it.path("campaign_id").asText()!=s.selector})
            throw IntegrationUnavailable("Meta returned metrics outside the requested resource")
        fun metric(key: String)=total(rows) {it.path(key)}
        val impressions=metric("impressions");val clicks=metric("clicks");val spend=metric("spend")
        return AnalyticsSnapshot(from,to,listOf(AnalyticsMetric("impressions","Impressions",impressions),AnalyticsMetric("clicks","Clicks",clicks),
            AnalyticsMetric("linkClicks","Link clicks",metric("inline_link_clicks")),AnalyticsMetric("reach","Reach",metric("reach")),AnalyticsMetric("spend","Spend",spend,"CURRENCY"),
            AnalyticsMetric("ctr","Click-through rate",ratio(clicks,impressions,100.0),"PERCENT"),AnalyticsMetric("cpc","Cost per click",ratio(spend,clicks),"CURRENCY")),
            currency(rows.firstOrNull()?.path("account_currency")?.asText()),listOf("Meta attribution and ad account timezone apply. Reach is the provider's deduplicated period aggregate; do not add it across links.")+
                if(rows.isEmpty())listOf("No activity was returned for this resource and period; currency is unavailable.") else emptyList())
    }
    fun page(config: Map<String,String>,s: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot {
        val requested=listOf("page_media_view","page_post_engagements")
        val pageHeaders=if(config["resolvePageToken"]!="false") {
            val page=http.scoped("GET","https://graph.facebook.com","${config["apiVersion"] ?: "v26.0"}/${s.resourceId}?fields=id,access_token",headers(config)).body
                ?: throw IntegrationUnavailable("Meta did not resolve an approved Page token")
            val pageToken=page.path("access_token").asText()
            if(page.path("id").asText()!=s.resourceId || pageToken.isBlank() || pageToken.length>8192 || pageToken.any(Char::isISOControl))throw IntegrationUnavailable("Meta could not authorize this Page. Check user token Page access and permissions.")
            mapOf("Authorization" to "Bearer $pageToken")
        } else headers(config)
        val result=http.scoped("GET","https://graph.facebook.com","${config["apiVersion"] ?: "v26.0"}/${s.resourceId}/insights?metric=${requested.joinToString(",")}&period=day&since=$from&until=${to.plusDays(1)}",pageHeaders).body
            ?: throw IntegrationUnavailable("Meta returned no Page insights")
        if(result.has("error"))throw IntegrationUnavailable("Meta Page insights are unavailable for these permissions, metrics or dates")
        val rows=array(result.path("data"),10)
        val metrics=requested.mapIndexed {index,key->
            val matches=rows.filter {it.path("name").asText()==key}
            if(matches.size>1)throw IntegrationUnavailable("Meta returned duplicate Page metrics")
            val row=matches.singleOrNull()
            val value=if(row==null)null else {
                if(row.path("period").asText()!="day")throw IntegrationUnavailable("Meta Page metrics did not use daily periods")
                val seen=mutableSetOf<LocalDate>()
                val values=array(row.path("values"),35).filter {point->
                    val date=runCatching {OffsetDateTime.parse(point.path("end_time").asText()).toLocalDate().minusDays(1)}
                        .recoverCatching {OffsetDateTime.parse(point.path("end_time").asText(),DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toLocalDate().minusDays(1)}
                        .getOrElse {throw IntegrationUnavailable("Meta returned a Page metric without a valid reporting date")}
                    if(date in from..to && !seen.add(date))throw IntegrationUnavailable("Meta returned duplicate daily Page values")
                    date in from..to
                }
                if(values.isEmpty())null else total(values) {it.path("value")}
            }
            AnalyticsMetric(key,listOf("Media views","Post engagements")[index],value)
        }
        return AnalyticsSnapshot(from,to,metrics,notes=listOf("Totals sum the returned daily Page counts, using Meta's reporting dates. Missing or withheld metrics are unavailable, not zero. Page eligibility and API-version restrictions may limit results. No personal audience data is imported."))
    }
}
