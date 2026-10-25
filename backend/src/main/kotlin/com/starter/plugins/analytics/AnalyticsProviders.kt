package com.starter.plugins.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.starter.plugins.integrations.IntegrationUnavailable
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.time.LocalDate

@Service
class AnalyticsProviders(private val google: GoogleAnalyticsReports,private val meta: MetaAnalyticsReports,private val cloudflare: CloudflareAnalyticsReports) {
    fun fetch(p: AnalyticsProvider,config: Map<String,String>,selection: AnalyticsSelection,from: LocalDate,to: LocalDate): AnalyticsSnapshot=when(p) {
        AnalyticsProvider.GOOGLE_ADS -> google.ads(config,selection,from,to)
        AnalyticsProvider.GOOGLE_ANALYTICS -> google.analytics(config,selection,from,to)
        AnalyticsProvider.META_ADS -> meta.ads(config,selection,from,to)
        AnalyticsProvider.META_PAGE -> meta.page(config,selection,from,to)
        AnalyticsProvider.CLOUDFLARE -> cloudflare.fetch(config,selection,from,to)
    }
}
internal fun encoded(value: String)=URLEncoder.encode(value,Charsets.UTF_8)
internal fun numeric(node: JsonNode): Double? {
    if(node.isMissingNode || node.isNull)return null
    return node.asText().toDoubleOrNull()?.takeIf {it.isFinite() && kotlin.math.abs(it)<=1e18}
        ?: throw IntegrationUnavailable("Provider returned an unsupported numeric metric")
}
internal fun total(rows: List<JsonNode>,read: (JsonNode)->JsonNode): Double? {
    val values=rows.map {numeric(read(it))}
    if(values.any {it==null})return null
    return values.filterNotNull().sum().takeIf {it.isFinite() && kotlin.math.abs(it)<=1e18}
        ?: throw IntegrationUnavailable("Provider metric exceeded its supported range")
}
internal fun ratio(numerator: Double?,denominator: Double?,scale: Double=1.0)=if(numerator!=null && denominator!=null && denominator>0)numerator/denominator*scale else null
internal fun currency(value: String?)=value?.takeIf {it.matches(Regex("[A-Z]{3}"))}
internal fun array(node: JsonNode,max: Int=100): List<JsonNode> {
    if(!node.isArray || node.size()>max)throw IntegrationUnavailable("Provider returned an unsupported report shape")
    return node.toList()
}
