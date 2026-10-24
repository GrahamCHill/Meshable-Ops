package com.starter.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.mfa.EmailMfaGuard
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingsService(private val records: NotificationSettingsRepository,private val transports: NotificationTransports,
    private val catalogue: NotificationCatalogue,private val renderer: NotificationRenderer,private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,private val mfa: EmailMfaGuard) {
    fun configuration(): NotificationConfiguration=records.findById(1).orElse(null)?.let {mapper.readValue(it.configurationJson,NotificationConfiguration::class.java)} ?: NotificationConfiguration()
    fun methodEnabled(id: String,config: NotificationConfiguration=configuration())=config.methods[id] ?: (id=="email")
    fun eligible(definition: NotificationDefinition)=transports.all().filter {method -> (!definition.privateToRecipient || method.recipientKind != NotificationRecipientKind.FIXED) && when(definition.category) {
        NotificationCategory.SECURITY -> method.supportsOtp && method.recipientKind!=NotificationRecipientKind.FIXED
        NotificationCategory.TRANSACTIONAL -> method.id=="email"
        else -> true
    }}.map {it.id}.toSet()
    fun rule(definition: NotificationDefinition,config: NotificationConfiguration=configuration())=config.rules[definition.id] ?: NotificationRule(
        enabled=definition.enabledByDefault,allowedMethods=setOf("email"),defaultMethods=setOf("email"),allowMemberChoice=definition.category in setOf(NotificationCategory.GENERAL,NotificationCategory.WORKFLOW))
    fun template(definition: NotificationDefinition,method: NotificationMethod,rule: NotificationRule)=rule.templates[method.id] ?: NotificationTemplate(subject=definition.subject,text=definition.text,
        parameters=listOf(if(definition.category==NotificationCategory.SECURITY) "{{code}}" else if(definition.category==NotificationCategory.WORKFLOW) definition.text else "{{message}}"))
    fun permitted(eventId: String,methodId: String): Boolean {
        val definition=catalogue.get(eventId);val config=configuration();val rule=rule(definition,config)
        return catalogue.active(definition) && rule.enabled && methodId in rule.allowedMethods && methodId in eligible(definition) && methodEnabled(methodId,config)
    }
    fun view(): NotificationSettingsView {
        val row=records.findById(1).orElse(null);val config=row?.let {mapper.readValue(it.configurationJson,NotificationConfiguration::class.java)} ?: NotificationConfiguration()
        val visible = transports.visible(); val visibleIds = visible.map { it.id }.toSet()
        return NotificationSettingsView(row?.revision ?: -1,visible.map {NotificationMethodView(it,methodEnabled(it.id,config),transports.available(it.id))},
            catalogue.all().map {definition -> val rule=rule(definition,config);NotificationDefinitionView(definition,rule.copy(allowedMethods=rule.allowedMethods.intersect(visibleIds),defaultMethods=rule.defaultMethods.intersect(visibleIds),templates=visible.filter {it.id in eligible(definition)}.associate {it.id to template(definition,it,rule)}),eligible(definition).intersect(visibleIds))})
    }
    @Transactional
    fun save(request: NotificationSettingsRequest): NotificationSettingsView {
        jdbc.query("SELECT pg_advisory_xact_lock(1938674202)",org.springframework.jdbc.core.RowCallbackHandler {})
        val existing=records.findById(1).orElse(null)
        if((existing?.revision ?: -1)!=request.revision) throw ConflictException("Notification settings changed. Reload before saving.")
        val previous=existing?.let {mapper.readValue(it.configurationJson,NotificationConfiguration::class.java)} ?: NotificationConfiguration()
        val methods=transports.all().associateBy {it.id}
        if(request.methods.keys.any {it !in methods} || request.rules.size>200) throw BadRequestException("Choose installed delivery methods and notification types")
        val rules=request.rules.mapValues {(id,input) ->
            val definition=catalogue.get(id);val allowed=eligible(definition)
            if(input.allowedMethods.any {it !in allowed} || !input.allowedMethods.containsAll(input.defaultMethods) || input.templates.keys.any {it !in allowed}) throw BadRequestException("Choose supported methods and defaults for this notification")
            if(input.allowMemberChoice && definition.category in setOf(NotificationCategory.SECURITY,NotificationCategory.TRANSACTIONAL)) throw BadRequestException("Security codes and explicit attachment sends do not have member opt-outs")
            input.copy(templates=input.templates.mapValues {(method,template) -> renderer.normalize(definition,methods.getValue(method),template)})
        }
        // A hidden disabled-provider method keeps its saved choices/templates for re-enablement.
        val hidden = methods.keys - transports.visible().map { it.id }.toSet()
        val merged = rules.mapValues { (id, rule) -> val old = this.rule(catalogue.get(id),previous); rule.copy(allowedMethods = rule.allowedMethods + old.allowedMethods.intersect(hidden), defaultMethods = rule.defaultMethods + old.defaultMethods.intersect(hidden), templates = old.templates.filterKeys { it in hidden } + rule.templates) }
        val config=NotificationConfiguration(previous.methods+request.methods,previous.rules+merged)
        val otp=rule(catalogue.get("security.otp"),config)
        mfa.checkNotificationRoute(methodEnabled("email",config) && otp.enabled && "email" in otp.allowedMethods)
        val row=existing ?: NotificationSettingsRecord()
        row.configurationJson=mapper.writeValueAsString(config);records.saveAndFlush(row)
        return view()
    }
}
