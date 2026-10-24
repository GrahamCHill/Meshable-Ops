package com.starter.notification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.*
import com.starter.email.*
import com.starter.mfa.EmailMfaGuard
import com.starter.model.postgres.User
import com.starter.plugins.*
import com.starter.repository.postgres.UserRepository
import com.starter.security.RateLimits
import com.starter.service.branding.BrandingPreferencesService
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*

class TestSmsMethod : NotificationChannelAdapter {
    override val channel="smsportal.sms"
    override val pluginId="smsportal"
    override val label="SMSportal test provider"
    override val recipientKind=NotificationRecipientKind.PHONE
    override val supportsOtp=true
    override val maxTextLength=1600
    val sent=mutableListOf<ChannelMessage>()
    override fun available()=true
    override fun send(message: ChannelMessage): EmailDeliveryResult {sent+=message;return EmailDeliveryResult("ACCEPTED","Accepted")}
}
class NotificationFixture {
    val mapper=jacksonObjectMapper().findAndRegisterModules()
    val records=mock(NotificationSettingsRepository::class.java)
    val preferenceRecords=mock(NotificationPreferenceRepository::class.java)
    val plugins=mock(PluginRegistry::class.java)
    val email=mock(EmailDeliveryService::class.java)
    val emailSettings=mock(EmailSettingsService::class.java)
    val guard=mock(EmailMfaGuard::class.java)
    val journal=mock(NotificationDeliveryRecorder::class.java)
    val branding=mock(BrandingPreferencesService::class.java)
    val actors=mock(PluginActor::class.java)
    val users=mock(UserRepository::class.java)
    val sms=TestSmsMethod()
    val member=User(email="member@example.test",username="member",contactNumber="+27821234567")
    val emailMessages=mutableListOf<SendEmailRequest>()
    var stored: NotificationSettingsRecord?=null
    val savedPreferences=mutableMapOf<UUID,NotificationPreferenceRecord>()
    var pluginEnabled=true
    private val claimed=mutableSetOf<String>()
    init {
        `when`(plugins.definition(anyString())).thenAnswer {call -> val id=call.arguments[0] as String;PluginManifest(id,id,"1.0.0","",extensions=setOf(PluginExtension.NOTIFICATIONS),providesSms=true)}
        doAnswer {if(!pluginEnabled) throw com.starter.exception.NotFoundException("Disabled");null}.`when`(plugins).requireAvailable(anyString(),anyString())
        `when`(records.findById(1)).thenAnswer {Optional.ofNullable(stored)}
        `when`(records.saveAndFlush(any(NotificationSettingsRecord::class.java))).thenAnswer {call -> val row=call.getArgument<NotificationSettingsRecord>(0);if(stored!=null) row.revision++;stored=row;row}
        `when`(preferenceRecords.findById(any(UUID::class.java))).thenAnswer {Optional.ofNullable(savedPreferences[it.arguments[0]])}
        `when`(preferenceRecords.saveAndFlush(any(NotificationPreferenceRecord::class.java))).thenAnswer {call -> val row=call.getArgument<NotificationPreferenceRecord>(0);if(row.userId in savedPreferences) row.revision++;savedPreferences[row.userId]=row;row}
        `when`(emailSettings.current()).thenReturn(EmailConfiguration(enabled=true,fromAddress="sender@example.test",smtpHost="mail.example.test",smtpAuth=SmtpAuth.NONE))
        `when`(email.send(any<SendEmailRequest>() ?: SendEmailRequest("x@y.test","Subject","Text"),anyList())).thenAnswer {emailMessages+=it.getArgument<SendEmailRequest>(0);EmailDeliveryResult("ACCEPTED","Accepted")}
        `when`(branding.current()).thenReturn(BrandingPreferences(applicationName="Sample business"))
        `when`(actors.fresh(member.email)).thenReturn(member)
        `when`(users.findById(member.id)).thenReturn(Optional.of(member))
        doAnswer {call -> val key="${call.arguments[0]}:${call.arguments[1]}:${call.arguments[4]}";if(claimed.add(key)) UUID.randomUUID() else null}.`when`(journal).claim(anyString(),anyString(),any<NotificationTarget>() ?: NotificationTarget(),anyString(),anyString())
    }
    val transports=NotificationTransports(email,emailSettings,listOf(sms),plugins)
    val catalogue=NotificationCatalogue(listOf(com.starter.plugins.documents.DocumentNotifications(),com.starter.plugins.workflows.WorkflowNotifications()),plugins)
    val renderer=NotificationRenderer()
    val settings=NotificationSettingsService(records,transports,catalogue,renderer,mapper,mock(JdbcTemplate::class.java),guard)
    val preferences=NotificationPreferencesService(preferenceRecords,settings,catalogue,transports,actors,mapper,mock(JdbcTemplate::class.java))
    val notifications=NotificationService(transports,settings,catalogue,renderer,journal,branding)
    val publisher=NotificationPublisher(catalogue,preferences,users,notifications,mock(RateLimits::class.java))
    fun configure(event: String="general.message",rule: NotificationRule=NotificationRule(allowedMethods=setOf("email",sms.channel),defaultMethods=setOf(sms.channel)),methods: Map<String,Boolean> = mapOf(sms.channel to true))=
        settings.save(NotificationSettingsRequest(stored?.revision ?: -1,methods,mapOf(event to rule)))
}
