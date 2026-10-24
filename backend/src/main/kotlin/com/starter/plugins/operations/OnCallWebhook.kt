package com.starter.plugins.operations

import com.fasterxml.jackson.databind.JsonNode
import com.starter.webhook.IncomingWebhookExtension
import com.starter.exception.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class OnCallWebhook(private val store:OperationsStore,private val access:OperationsAccess,private val oncall:OnCallService):IncomingWebhookExtension {
    override fun receive(endpointId:UUID,deliveryId:String,payload:JsonNode) {
        if(!access.enabled("on-call"))return
        val row=store.list("on-call","id=? AND kind='SOURCE'",store.stable("on-call-source:$endpointId")).firstOrNull() ?: return
        val source=store.decode(row,MonitorSource::class.java);if(!source.enabled)return
        val state=payload.path("status").asText().ifBlank {payload.path("data").path("essentials").path("monitorCondition").asText().ifBlank {payload.path("detail").path("state").path("value").asText()}}.uppercase()
        if(state in setOf("RESOLVED","OK","RECOVERED"))return // Recovery never creates a new page or closes a human-owned incident.
        val actor=store.jdbc.queryForObject("SELECT email FROM users WHERE id=? AND active=true",String::class.java,row.ownerId) ?: return
        access.admin(actor,"on-call",true);access.project(actor,"on-call",row.projectId!!,true,true)
        val title=payload.path("title").asText().ifBlank {payload.path("data").path("essentials").path("alertRule").asText().ifBlank {payload.path("detail").path("alarmName").asText()}}
        val description=payload.path("description").asText().ifBlank {payload.path("data").path("essentials").path("description").asText().ifBlank {"Service monitor ${source.label} reported an incident."}}
        OperationsValidation.text(title,"monitor alert title",160);OperationsValidation.text(description,"monitor alert description",8000)
        val severity=AlertSeverity.entries.find {it.name==payload.path("severity").asText().uppercase()} ?: when(payload.path("data").path("essentials").path("severity").asText().uppercase()){ "SEV0"->AlertSeverity.SEV1;"SEV1"->AlertSeverity.SEV2;"SEV2"->AlertSeverity.SEV3;else->AlertSeverity.SEV2 }
        val event=payload.path("eventId").asText().ifBlank {deliveryId}
        oncall.create(row.ownerId,row.projectId,"monitor:$endpointId:$event",ServiceAlert(title,description,severity,source="MONITOR: ${source.label}",confirmed=true,nextPageAt=Instant.now()))
    }
}
