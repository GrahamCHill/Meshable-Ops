package com.starter.notification

import com.starter.dto.SendEmailRequest
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class NotificationServiceTest {
    private val f=NotificationFixture()
    @Test fun `new plugin methods are discovered without expanding an enum and start switched off`() {
        assertThat(f.settings.view().methods.map {it.method.id}).containsExactly("email","smsportal.sms")
        assertThat(f.notifications.available(f.sms.channel)).isFalse()
        f.configure()
        val result=f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="+27821234567"),mapOf("message" to "Sample SMS","subject" to ""))
        assertThat(result.status).isEqualTo("ACCEPTED");assertThat(f.sms.sent.single().text).isEqualTo("Sample SMS")
    }
    @Test fun `method notification and plugin switches each prevent provider calls`() {
        f.configure(rule=NotificationRule(enabled=false,allowedMethods=setOf(f.sms.channel),defaultMethods=setOf(f.sms.channel)))
        assertThat(f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="+27821234567"),mapOf("message" to "Body")).status).isEqualTo("DISABLED")
        f.configure();f.pluginEnabled=false
        assertThat(f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="+27821234567"),mapOf("message" to "Body")).status).isEqualTo("DISABLED")
        f.pluginEnabled=true;f.configure(methods=mapOf(f.sms.channel to false))
        assertThat(f.notifications.send(f.sms.channel,ChannelMessage("Body","+27821234567")).status).isEqualTo("DISABLED")
        assertThat(f.sms.sent).isEmpty();verifyNoInteractions(f.journal)
    }
    @Test fun `each method receives its own template and HTML is delivered with plain text fallback`() {
        f.configure(rule=NotificationRule(allowedMethods=setOf("email",f.sms.channel),defaultMethods=setOf("email",f.sms.channel),templates=mapOf(
            "email" to NotificationTemplate(subject="Notice: {{subject}}",text="Hello {{recipientName}}: {{message}}",html="<h1>{{subject}}</h1><p>{{message}}</p>"),
            f.sms.channel to NotificationTemplate(text="SMS: {{message}}"))))
        val result=f.publisher.publish("general.message",f.member.id,mapOf("subject" to "Update","message" to "Saved"))
        assertThat(result.values.map {it.status}).containsOnly("ACCEPTED")
        assertThat(f.emailMessages.single().text).isEqualTo("Hello member: Saved")
        assertThat(f.emailMessages.single().html).isEqualTo("<h1>Update</h1><p>Saved</p>")
        assertThat(f.sms.sent.single().text).isEqualTo("SMS: Saved")
    }
    @Test fun `generic OTP delivery requires a verified contact and a method explicitly capable of OTP`() {
        f.configure("security.otp",NotificationRule(allowedMethods=setOf("email",f.sms.channel),defaultMethods=setOf("email"),allowMemberChoice=false))
        val target=NotificationTarget(phone="+27821234567")
        assertThatThrownBy {f.notifications.sendOtp(f.sms.channel,target,"654321",5)}.isInstanceOf(BadRequestException::class.java)
        assertThat(f.sms.sent).isEmpty()
        assertThat(f.notifications.sendOtp(f.sms.channel,target.copy(verifiedMethods=setOf(f.sms.channel)),"654321",5).status).isEqualTo("ACCEPTED")
        assertThat(f.sms.sent.single().text).contains("654321")
        assertThat(f.stored!!.configurationJson).doesNotContain("654321")
    }
    @Test fun `invalid phone numbers and oversized SMS bodies fail before claiming or sending`() {
        f.configure()
        assertThatThrownBy {f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="local"),mapOf("message" to "Body"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="+27821234567"),mapOf("message" to "x".repeat(1601)))}.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(f.journal);assertThat(f.sms.sent).isEmpty()
    }
    @Test fun `same delivery key is not sent twice and legacy email honors the general notification switch`() {
        f.configure()
        repeat(2) {f.notifications.deliver("general.message",f.sms.channel,NotificationTarget(phone="+27821234567"),mapOf("message" to "Body"),key="same-operation")}
        assertThat(f.sms.sent).hasSize(1)
        f.configure(rule=NotificationRule(enabled=false))
        assertThat(f.notifications.send(NotificationChannel.EMAIL,SendEmailRequest("recipient@example.test","Title","Body")).status).isEqualTo("DISABLED")
        assertThat(f.emailMessages).isEmpty()
    }
}
