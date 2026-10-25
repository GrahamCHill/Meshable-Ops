package com.starter.plugins.analytics

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class AnalyticsContext { PROJECT, CLIENT }
enum class AnalyticsFilter { ALL, CAMPAIGN, PAGE }
data class AnalyticsSelection(val resourceId: String="",val filter: AnalyticsFilter=AnalyticsFilter.ALL,val selector: String="")
data class AnalyticsLinkInput(val label: String,val selection: AnalyticsSelection)
data class AnalyticsSyncInput(val revision: Long,val from: LocalDate,val to: LocalDate)
data class AnalyticsMetric(val key: String,val label: String,val value: Double?,val unit: String="NUMBER")
data class AnalyticsSnapshot(val from: LocalDate,val to: LocalDate,val metrics: List<AnalyticsMetric>,val currency: String?=null,
    val notes: List<String> = emptyList(),val syncedAt: Instant=Instant.now())
data class AnalyticsPayload(val selection: AnalyticsSelection=AnalyticsSelection(),val snapshot: AnalyticsSnapshot?=null)
data class AnalyticsLink(val id: UUID,val label: String,val selection: AnalyticsSelection,val snapshot: AnalyticsSnapshot?,val revision: Long,val url: String)
data class AnalyticsOptions(val resourceLabel: String,val resources: Set<String>,val filters: Set<AnalyticsFilter>,val configured: Boolean)

@MappedSuperclass
abstract class AnalyticsLinkRecord(@Id val id: UUID=UUID.randomUUID(),@Enumerated(EnumType.STRING) @Column(length=12,nullable=false) var contextKind: AnalyticsContext=AnalyticsContext.PROJECT,
    @Column(nullable=false) var contextId: UUID=UUID.randomUUID(),@Column(length=120,nullable=false) var label: String="",
    @get:JsonIgnore @Column(columnDefinition="TEXT",nullable=false) var payloadJson: String="{}",var updatedAt: Instant=Instant.now(),@Version var revision: Long=0)
@Entity @Table(name="plugin_google_ads_links",indexes=[Index(columnList="context_kind,context_id")]) class GoogleAdsLinkRecord: AnalyticsLinkRecord()
@Entity @Table(name="plugin_meta_ads_links",indexes=[Index(columnList="context_kind,context_id")]) class MetaAdsLinkRecord: AnalyticsLinkRecord()
@Entity @Table(name="plugin_google_analytics_links",indexes=[Index(columnList="context_kind,context_id")]) class GoogleAnalyticsLinkRecord: AnalyticsLinkRecord()
@Entity @Table(name="plugin_meta_page_analytics_links",indexes=[Index(columnList="context_kind,context_id")]) class MetaPageAnalyticsLinkRecord: AnalyticsLinkRecord()
@Entity @Table(name="plugin_cloudflare_analytics_links",indexes=[Index(columnList="context_kind,context_id")]) class CloudflareAnalyticsLinkRecord: AnalyticsLinkRecord()
