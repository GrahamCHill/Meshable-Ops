package com.starter.plugins.analytics

import com.starter.exception.BadRequestException
import java.util.UUID

data class AnalyticsResourceAssignment(val resource: String,val kind: AnalyticsContext?=null,val id: UUID?=null)

/** An approved account is not implicitly shared across unrelated local clients. GLOBAL is an explicit admin choice. */
object AnalyticsResourcePolicy {
    fun parse(provider: AnalyticsProvider,raw: String): Set<AnalyticsResourceAssignment> {
        if(raw.isBlank())return emptySet()
        val entries=raw.split(';').map(String::trim).filter(String::isNotEmpty)
        if(entries.size>100)throw BadRequestException("Use up to 100 analytics resource assignments")
        return entries.map {entry->
            val parts=entry.split('=',limit=2)
            if(parts.size!=2 || parts[0].trim().contains(','))throw BadRequestException("Use RESOURCE_ID=CLIENT:UUID, RESOURCE_ID=PROJECT:UUID or RESOURCE_ID=GLOBAL assignments separated by semicolons")
            val resource=parts[0].trim();provider.resources(resource)
            val target=parts[1].trim()
            if(target=="GLOBAL")AnalyticsResourceAssignment(resource) else {
                val context=target.split(':',limit=2)
                val kind=context.firstOrNull()?.let {value->AnalyticsContext.entries.find {it.name==value}}
                val id=context.getOrNull(1)?.let {runCatching {UUID.fromString(it)}.getOrNull()}
                if(kind==null || id==null || id.toString()!=context[1].lowercase())throw BadRequestException("Assign each analytics resource to a valid CLIENT:UUID, PROJECT:UUID or GLOBAL scope")
                AnalyticsResourceAssignment(resource,kind,id)
            }
        }.toSet()
    }
    fun allowed(assignments: Set<AnalyticsResourceAssignment>,kind: AnalyticsContext,context: UUID,parentClient: UUID?): Set<String> = assignments.filter {
        it.kind==null || it.kind==kind && it.id==context || kind==AnalyticsContext.PROJECT && it.kind==AnalyticsContext.CLIENT && it.id==parentClient
    }.map {it.resource}.toSet()
}
