package com.starter.plugins

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.analytics.*
import com.starter.plugins.integrations.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate

class AnalyticsReportsTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val from=LocalDate.parse("2026-10-01");private val to=from.plusDays(2)
    private data class Request(val method:String,val base:String,val path:String,val headers:Map<String,String>,val body:String?)
    private val calls=mutableListOf<Request>()
    private var response="{}"
    private val http=mock(IntegrationHttpClient::class.java) {call->if(call.method.name=="scoped") {
        @Suppress("UNCHECKED_CAST") val request=Request(call.arguments[0] as String,call.arguments[1] as String,call.arguments[2] as String,call.arguments[3] as Map<String,String>,call.arguments[4] as String?)
        calls+=request
        IntegrationResponse(200,mapper.readTree(if(request.base=="https://oauth2.googleapis.com")"""{"access_token":"test-short-lived-token"}""" else response))
    }else RETURNS_DEFAULTS.answer(call)}
    private val google=GoogleAnalyticsReports(http,mapper);private val meta=MetaAnalyticsReports(http,mapper);private val cloudflare=CloudflareAnalyticsReports(http,mapper)
    private val googleConfig=mapOf("clientId" to "test-client","clientSecret" to "test-secret&value","refreshToken" to "test-refresh","developerToken" to "test-developer")
    private val metaConfig=mapOf("accessToken" to "test-page-token","resolvePageToken" to "false")
    private fun metric(value:AnalyticsSnapshot,key:String)=value.metrics.single {it.key==key}.value

    @Test fun `all five bundled manifests declare isolated tables and both contexts`() {
        val definitions=listOf(GoogleAdsPlugin(),MetaAdsPlugin(),GoogleAnalyticsPlugin(),MetaPageAnalyticsPlugin(),CloudflareAnalyticsPlugin())
        definitions.forEach {PluginManifests.validateIdentity(it.manifest);PluginManifests.validate(it.manifest)
            assertThat(it.manifest.contextTargets).containsExactlyInAnyOrder("PROJECT","CLIENT")
            assertThat(it.manifest.version).isEqualTo("1.0.0")
            assertThat(it.manifest.settingsFields.filter {field->field.key in setOf("accessToken","clientSecret","refreshToken","developerToken")}).allMatch {field->field.type==PluginFieldType.SECRET}}
        assertThat(definitions.map {it.manifest.tables.single().name}.toSet()).hasSize(5)
    }
    @Test fun `each analytics connector activates alone while every other connector is disabled`() {
        val definitions=listOf(GoogleAdsPlugin(),MetaAdsPlugin(),GoogleAnalyticsPlugin(),MetaPageAnalyticsPlugin(),CloudflareAnalyticsPlugin())
        definitions.forEach {enabled->
            val settings=mock(com.starter.repository.postgres.PluginSettingsRepository::class.java)
            `when`(settings.findAll()).thenReturn(listOf(com.starter.model.postgres.PluginSettings(enabled.manifest.id,true)))
            val registry=PluginRegistry(definitions,settings)
            assertThat(registry.available("ADMIN").map {it.manifest.id}).containsExactly(enabled.manifest.id)
            assertThat(enabled.manifest.requires).isEmpty();assertThat(enabled.manifest.requiresCapabilities).isEmpty()
        }
    }
    @Test fun `a Google reporting failure does not block Meta and a Meta failure does not block Google`() {
        val googleReports=mock(GoogleAnalyticsReports::class.java);val metaReports=mock(MetaAnalyticsReports::class.java);val cf=mock(CloudflareAnalyticsReports::class.java)
        val dispatcher=AnalyticsProviders(googleReports,metaReports,cf);val selection=AnalyticsSelection("123")
        val snapshot=AnalyticsSnapshot(from,to,emptyList())
        `when`(googleReports.ads(emptyMap(),selection,from,to)).thenThrow(IntegrationUnavailable("Google unavailable"))
        `when`(metaReports.ads(emptyMap(),selection,from,to)).thenReturn(snapshot)
        assertThatThrownBy {dispatcher.fetch(AnalyticsProvider.GOOGLE_ADS,emptyMap(),selection,from,to)}.isInstanceOf(IntegrationUnavailable::class.java)
        assertThat(dispatcher.fetch(AnalyticsProvider.META_ADS,emptyMap(),selection,from,to)).isEqualTo(snapshot)
        `when`(metaReports.page(emptyMap(),selection,from,to)).thenThrow(IntegrationUnavailable("Meta unavailable"))
        `when`(googleReports.analytics(emptyMap(),selection,from,to)).thenReturn(snapshot)
        assertThatThrownBy {dispatcher.fetch(AnalyticsProvider.META_PAGE,emptyMap(),selection,from,to)}.isInstanceOf(IntegrationUnavailable::class.java)
        assertThat(dispatcher.fetch(AnalyticsProvider.GOOGLE_ANALYTICS,emptyMap(),selection,from,to)).isEqualTo(snapshot)
        verifyNoInteractions(cf)
    }
    @Test fun `Google Ads uses server token exchange safe campaign query and micros conversion`() {
        response="""{"results":[{"customer":{"currencyCode":"ZAR"},"metrics":{"impressions":"100","clicks":"10","costMicros":"25000000","conversions":2.5,"conversionsValue":100}}]}"""
        val value=google.ads(googleConfig,AnalyticsSelection("1234567890",AnalyticsFilter.CAMPAIGN,"987"),from,to)
        assertThat(metric(value,"spend")).isEqualTo(25.0);assertThat(metric(value,"ctr")).isEqualTo(10.0);assertThat(metric(value,"cpc")).isEqualTo(2.5)
        assertThat(metric(value,"conversions")).isEqualTo(2.5);assertThat(value.currency).isEqualTo("ZAR")
        assertThat(calls.first().body).contains("client_secret=test-secret%26value").contains("grant_type=refresh_token")
        val query=mapper.readTree(calls.last().body).path("query").asText()
        assertThat(query).contains("FROM campaign","campaign.id = 987","2026-10-01","2026-10-03")
        assertThat(calls.last().headers["Authorization"]).isEqualTo("Bearer test-short-lived-token")
        assertThat(mapper.writeValueAsString(value)).doesNotContain("test-secret","test-refresh","test-short-lived-token")
        response="""{"results":[],"nextPageToken":"unexpected-more-data"}"""
        assertThatThrownBy {google.ads(googleConfig,AnalyticsSelection("1234567890"),from,to)}.isInstanceOf(IntegrationUnavailable::class.java)
    }
    @Test fun `GA4 uses exact escaped dimension filters and returns provider aggregates without summing users`() {
        val keys=listOf("activeUsers","sessions","screenPageViews","engagedSessions","keyEvents","totalRevenue")
        response=mapper.writeValueAsString(mapOf("metricHeaders" to keys.map {mapOf("name" to it)},"rows" to listOf(mapOf("metricValues" to listOf(12,20,40,15,3,50).map {mapOf("value" to it.toString())})),"rowCount" to 1,"metadata" to mapOf("currencyCode" to "USD","subjectToThresholding" to true)))
        val value=google.analytics(googleConfig,AnalyticsSelection("123",AnalyticsFilter.CAMPAIGN,"Launch \"A\""),from,to)
        assertThat(metric(value,"activeUsers")).isEqualTo(12.0);assertThat(metric(value,"engagementRate")).isEqualTo(75.0)
        val filter=mapper.readTree(calls.last().body).path("dimensionFilter").path("filter")
        assertThat(filter.path("fieldName").asText()).isEqualTo("sessionCampaignName")
        assertThat(filter.path("stringFilter").path("value").asText()).isEqualTo("Launch \"A\"")
        assertThat(value.notes).anyMatch {it.contains("thresholding")}
    }
    @Test fun `Meta campaign insights stay in the approved account and preserve deduplicated reach`() {
        response="""{"data":[{"account_id":"123","campaign_id":"456","account_currency":"EUR","impressions":"200","clicks":"20","inline_link_clicks":"15","reach":"145","spend":"40"}]}"""
        val value=meta.ads(metaConfig,AnalyticsSelection("123",AnalyticsFilter.CAMPAIGN,"456"),from,to)
        assertThat(metric(value,"reach")).isEqualTo(145.0);assertThat(metric(value,"ctr")).isEqualTo(10.0)
        assertThat(calls.single().path).startsWith("v26.0/act_123/insights?").doesNotContain("test-page-token")
        assertThat(java.net.URLDecoder.decode(calls.single().path,Charsets.UTF_8)).contains("campaign.id","456")
        response="""{"data":[{"account_id":"999","campaign_id":"456"}]}"""
        assertThatThrownBy {meta.ads(metaConfig,AnalyticsSelection("123",AnalyticsFilter.CAMPAIGN,"456"),from,to)}.isInstanceOf(IntegrationUnavailable::class.java)
    }
    @Test fun `Page totals use daily reporting boundaries and missing metrics stay unavailable`() {
        response="""{"data":[{"name":"page_media_view","period":"day","values":[{"value":999,"end_time":"2026-10-01T00:00:00+0000"},{"value":10,"end_time":"2026-10-02T00:00:00+0000"},{"value":15,"end_time":"2026-10-03T00:00:00+0000"}]}],"paging":{"next":"https://untrusted.example.test/?access_token=never-follow"}}"""
        val value=meta.page(metaConfig,AnalyticsSelection("123"),from,to)
        assertThat(metric(value,"page_media_view")).isEqualTo(25.0)
        assertThat(metric(value,"page_post_engagements")).isNull()
        assertThat(calls).hasSize(1)
        assertThat(mapper.writeValueAsString(value)).doesNotContain("never-follow","test-page-token")
    }
    @Test fun `Cloudflare uses GraphQL variables and reports adaptive sampling without treating visits as unique users`() {
        response="""{"data":{"viewer":{"zones":[{"httpRequestsAdaptiveGroups":[{"count":1000,"sum":{"visits":150,"edgeResponseBytes":2500000},"avg":{"sampleInterval":10}}]}]}}}"""
        val path="/launch\"sale";val value=cloudflare.fetch(mapOf("accessToken" to "test-cloudflare"),AnalyticsSelection("a".repeat(32),AnalyticsFilter.PAGE,path),from,to)
        val body=mapper.readTree(calls.single().body)
        assertThat(body.path("query").asText()).doesNotContain(path)
        assertThat(body.path("variables").path("path").asText()).isEqualTo(path)
        assertThat(metric(value,"requests")).isEqualTo(1000.0);assertThat(metric(value,"bytes")).isEqualTo(2500000.0)
        assertThat(value.notes).anyMatch {it.contains("sampled")}
        response="""{"errors":[{"message":"private provider text"}]}"""
        assertThatThrownBy {cloudflare.fetch(mapOf("accessToken" to "test-cloudflare"),AnalyticsSelection("a".repeat(32)),from,to)}
            .isInstanceOf(IntegrationUnavailable::class.java).hasMessageNotContaining("private provider text")
    }
    @Test fun `unsafe selectors and non finite metrics are rejected`() {
        assertThatThrownBy {AnalyticsService.validateSelection(AnalyticsProvider.GOOGLE_ADS,AnalyticsSelection("1234567890",AnalyticsFilter.CAMPAIGN,"1 OR 1=1"))}.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        assertThatThrownBy {AnalyticsService.validateSelection(AnalyticsProvider.CLOUDFLARE,AnalyticsSelection("a".repeat(32),AnalyticsFilter.PAGE,"https://localhost/"))}.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        response="""{"data":[{"account_id":"123","impressions":"NaN"}]}"""
        assertThatThrownBy {meta.ads(metaConfig,AnalyticsSelection("123"),from,to)}.isInstanceOf(IntegrationUnavailable::class.java)
    }
}
