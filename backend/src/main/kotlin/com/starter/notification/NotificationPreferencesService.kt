package com.starter.notification

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationPreferencesService(private val records: NotificationPreferenceRepository,private val settings: NotificationSettingsService,
    private val catalogue: NotificationCatalogue,private val transports: NotificationTransports,private val actors: PluginActor,
    private val mapper: ObjectMapper,private val jdbc: JdbcTemplate) {
    private fun decode(row: NotificationPreferenceRecord?): Map<String,Set<String>> = row?.let {mapper.readValue(it.choicesJson,object:TypeReference<Map<String,Set<String>>>() {})} ?: emptyMap()
    fun selected(actor: User,definition: NotificationDefinition): Set<String> {
        val rule=settings.rule(definition)
        val custom=if(rule.allowMemberChoice && definition.category in setOf(NotificationCategory.GENERAL,NotificationCategory.WORKFLOW)) decode(records.findById(actor.id).orElse(null))[definition.id] else null
        return (custom ?: rule.defaultMethods).intersect(rule.allowedMethods).intersect(settings.eligible(definition))
    }
    fun view(email: String): NotificationPreferencesView {
        val actor=actors.fresh(email);val row=records.findById(actor.id).orElse(null);val choices=decode(row)
        val items=catalogue.all().filter {it.category in setOf(NotificationCategory.GENERAL,NotificationCategory.WORKFLOW) && catalogue.active(it)}.map {definition ->
            val rule=settings.rule(definition)
            NotificationPreferenceItem(definition.id,definition.label,definition.description,rule.enabled,rule.allowMemberChoice,rule.defaultMethods,selected(actor,definition),definition.id !in choices,
                transports.all().filter {it.id in rule.allowedMethods}.map {NotificationMethodView(it,settings.methodEnabled(it.id),transports.available(it.id) && transports.reachable(it.id, actor.id) && (it.recipientKind!=NotificationRecipientKind.PHONE || validPhone(actor.contactNumber)))})
        }
        return NotificationPreferencesView(row?.revision ?: -1,items,validPhone(actor.contactNumber))
    }
    @Transactional
    fun save(email: String,request: NotificationPreferenceRequest): NotificationPreferencesView {
        val actor=actors.fresh(email)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",org.springframework.jdbc.core.RowCallbackHandler {},"notification-preferences:${actor.id}")
        val row=records.findById(actor.id).orElse(null)
        if((row?.revision ?: -1)!=request.revision) throw ConflictException("Your notification preferences changed. Reload before saving.")
        if(request.choices.size>200) throw BadRequestException("Choose supported notifications")
        request.choices.forEach {(id,methods) ->
            val definition=catalogue.get(id);val rule=settings.rule(definition)
            if(definition.category !in setOf(NotificationCategory.GENERAL,NotificationCategory.WORKFLOW) || !rule.allowMemberChoice || methods.any {it !in rule.allowedMethods || it !in settings.eligible(definition)}) throw BadRequestException("Choose only delivery methods permitted by the administrator")
        }
        val saved=row ?: NotificationPreferenceRecord(actor.id)
        saved.choicesJson=mapper.writeValueAsString(request.choices);records.saveAndFlush(saved)
        return view(email)
    }
    fun methodsFor(actor: User,definition: NotificationDefinition)=selected(actor,definition)
    private fun validPhone(value: String?)=value?.matches(Regex("\\+?[1-9][0-9]{6,14}"))==true
}
