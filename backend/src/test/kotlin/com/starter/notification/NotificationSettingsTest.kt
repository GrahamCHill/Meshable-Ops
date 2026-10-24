package com.starter.notification

import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class NotificationSettingsTest {
    private val f=NotificationFixture()
    @Test fun `first save and subsequent edits reject stale revisions and preserve other notifications`() {
        val first=f.configure()
        assertThat(first.revision).isEqualTo(0)
        assertThatThrownBy {f.settings.save(NotificationSettingsRequest(-1))}.isInstanceOf(ConflictException::class.java)
        f.configure("workflow.completed",NotificationRule(enabled=true))
        assertThat(f.settings.rule(f.catalogue.get("general.message")).allowedMethods).contains(f.sms.channel)
        assertThat(f.settings.rule(f.catalogue.get("workflow.completed")).enabled).isTrue()
    }
    @Test fun `unsupported methods member opt outs on OTP and broken security templates cannot be saved`() {
        assertThatThrownBy {f.configure(rule=NotificationRule(allowedMethods=setOf("unknown")))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.configure("security.otp",NotificationRule())}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.configure("security.otp",NotificationRule(allowMemberChoice=false,templates=mapOf("email" to NotificationTemplate(text="Missing code"))))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.configure("documents.email",NotificationRule(allowedMethods=setOf(f.sms.channel),defaultMethods=emptySet(),allowMemberChoice=false))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `notification saves share the email-only MFA interlock before persistence`() {
        doThrow(BadRequestException("Email-only factor")).`when`(f.guard).checkNotificationRoute(false)
        assertThatThrownBy {f.settings.save(NotificationSettingsRequest(methods=mapOf("email" to false)))}.isInstanceOf(BadRequestException::class.java)
        assertThat(f.stored).isNull()
        f.configure();verify(f.guard).checkNotificationRoute(true)
    }
    @Test fun `HTML is sanitized when stored and disabling retains message templates`() {
        val rule=NotificationRule(templates=mapOf("email" to NotificationTemplate(html="<p onclick='bad()'>{{message}}</p><script>bad()</script>")))
        f.configure(rule=rule)
        assertThat(f.settings.rule(f.catalogue.get("general.message")).templates.getValue("email").html).isEqualTo("<p>{{message}}</p>")
        f.settings.save(NotificationSettingsRequest(f.stored!!.revision,methods=mapOf("email" to false)))
        assertThat(f.settings.rule(f.catalogue.get("general.message")).templates.getValue("email").html).isEqualTo("<p>{{message}}</p>")
    }
}
