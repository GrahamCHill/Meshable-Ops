package com.starter.plugins.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.integrations.IntegrationUnavailable
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GoogleAnalyticsReports(private val http: IntegrationHttpClient,private val mapper: ObjectMapper) {
    private fun token(config: Map<String,String>): String {
        val form=mapOf("grant_type" to "refresh_token","client_id" to config.getValue("clientId"),"client_secret" to config.getValue("clientSecret"),"refresh_token" to config.getValue("refreshToken"))
            .entries.joinToString("&") {"${it.key}=${encoded(it.value)}"}
        val result=http.scoped("POST","https://oauth2.googleapis.com","token",body=form,contentType="application/x-www-form-urlencoded").body
            ?: throw IntegrationUnavailable("Google returned no authorization response")
        return result.path("access_token").asText().takeIf {it.isNotBlank() && it.length<=8192 && it.none(Char::isISOControl)}
            ?: throw IntegrationUnavailable("Google authorization failed. Reconnect the reporting account.")
    }
    fun ads(config: Map<String,String>,s: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot {
        val headers=mutableMapOf("Authorization" to "Bearer ${token(config)}","developer-token" to config.getValue("developerToken"))
        config["managerCustomerId"]?.let {headers["login-customer-id"]=it}
        val resource=if(s.filter==AnalyticsFilter.CAMPAIGN)"campaign" else "customer"
        val query="SELECT customer.currency_code, metrics.impressions, metrics.clicks, metrics.cost_micros, metrics.conversions, metrics.conversions_value FROM $resource WHERE segments.date BETWEEN '$from' AND '$to'"+
            if(s.filter==AnalyticsFilter.CAMPAIGN)" AND campaign.id = ${s.selector}" else ""
        val result=http.scoped("POST","https://googleads.googleapis.com","${config["apiVersion"] ?: "v26"}/customers/${s.resourceId}/googleAds:search",headers,mapper.writeValueAsString(mapOf("query" to query))).body
            ?: throw IntegrationUnavailable("Google Ads returned no metrics")
        if(result.has("error") || result.path("nextPageToken").asText().isNotEmpty())throw IntegrationUnavailable("Google Ads could not return a complete aggregate report")
        val rows=if(result.has("results"))array(result.path("results"),2) else emptyList()
        if(rows.size>1)throw IntegrationUnavailable("Google Ads returned multiple aggregates")
        fun metric(key: String)=total(rows) {it.path("metrics").path(key)}
        val impressions=metric("impressions");val clicks=metric("clicks");val spend=metric("costMicros")?.div(1_000_000)
        return AnalyticsSnapshot(from,to,listOf(AnalyticsMetric("impressions","Impressions",impressions),AnalyticsMetric("clicks","Clicks",clicks),
            AnalyticsMetric("spend","Spend",spend,"CURRENCY"),AnalyticsMetric("ctr","Click-through rate",ratio(clicks,impressions,100.0),"PERCENT"),
            AnalyticsMetric("cpc","Cost per click",ratio(spend,clicks),"CURRENCY"),AnalyticsMetric("conversions","Conversions",metric("conversions")),
            AnalyticsMetric("conversionValue","Conversion value",metric("conversionsValue"),"CURRENCY")),currency(rows.firstOrNull()?.path("customer")?.path("currencyCode")?.asText()),
            listOf("Google Ads attribution and account timezone apply. Conversion counts can be fractional; recent reporting may change.")+if(rows.isEmpty())listOf("No activity was returned for this resource and period; currency is unavailable.") else emptyList())
    }
    fun analytics(config: Map<String,String>,s: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot {
        val keys=listOf("activeUsers","sessions","screenPageViews","engagedSessions","keyEvents","totalRevenue")
        val body=mutableMapOf<String,Any>("dateRanges" to listOf(mapOf("startDate" to from.toString(),"endDate" to to.toString())),"metrics" to keys.map {mapOf("name" to it)},"limit" to "1")
        if(s.filter!=AnalyticsFilter.ALL)body["dimensionFilter"]=mapOf("filter" to mapOf("fieldName" to if(s.filter==AnalyticsFilter.PAGE)"pagePath" else "sessionCampaignName",
            "stringFilter" to mapOf("matchType" to "EXACT","value" to s.selector,"caseSensitive" to true)))
        val result=http.scoped("POST","https://analyticsdata.googleapis.com","v1beta/properties/${s.resourceId}:runReport",mapOf("Authorization" to "Bearer ${token(config)}"),mapper.writeValueAsString(body)).body
            ?: throw IntegrationUnavailable("Google Analytics returned no report")
        if(result.has("error") || result.path("rowCount").asInt()>1)throw IntegrationUnavailable("Google Analytics did not return a complete aggregate report")
        val rows=if(result.has("rows"))array(result.path("rows"),1) else emptyList()
        val reportedKeys=if(result.has("metricHeaders"))array(result.path("metricHeaders"),10).map {it.path("name").asText()} else emptyList()
        if(rows.isNotEmpty() && reportedKeys!=keys)throw IntegrationUnavailable("Google Analytics returned unexpected metric columns")
        val values=if(rows.isEmpty())List<Double?>(keys.size) {0.0} else array(rows.single().path("metricValues"),keys.size).also {if(it.size!=keys.size)throw IntegrationUnavailable("Google Analytics returned incomplete metrics")}.map {numeric(it.path("value"))}
        val metrics=keys.mapIndexed {index,key->AnalyticsMetric(key,listOf("Active users","Sessions","Page / screen views","Engaged sessions","Key events","Revenue")[index],values[index],if(key=="totalRevenue")"CURRENCY" else "NUMBER")}+
            AnalyticsMetric("engagementRate","Engagement rate",ratio(values[3],values[1],100.0),"PERCENT")
        val metadata=result.path("metadata")
        return AnalyticsSnapshot(from,to,metrics,currency(metadata.path("currencyCode").asText()),buildList {
            add("GA4 property timezone and attribution apply. Campaign filters match session campaign names exactly; page filters match paths exactly. Users are deduplicated by GA4 for this report.")
            if(metadata.path("subjectToThresholding").asBoolean())add("Google applied privacy thresholding; some data may be withheld.")
            if(metadata.path("dataLossFromOtherRow").asBoolean())add("Google reports data loss from grouped rows.")
            if(metadata.path("samplingMetadatas").size()>0)add("Google reports sampled results.")
            if(rows.isEmpty())add("No activity was returned for this resource and period.")
        })
    }
}
