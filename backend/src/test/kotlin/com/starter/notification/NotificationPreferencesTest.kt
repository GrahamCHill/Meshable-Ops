package com.starter.notification

import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationPreferencesTest {
    private val f=NotificationFixture()
    @Test fun `members inherit defaults can opt out then restore administrator defaults`() {
        f.configure()
        assertThat(f.preferences.methodsFor(f.member,f.catalogue.get("general.message"))).containsExactly(f.sms.channel)
        val saved=f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("general.message" to emptySet())))
        assertThat(saved.notifications.first {it.id=="general.message"}.selected).isEmpty()
        assertThat(f.publisher.publish("general.message",f.member.id,mapOf("message" to "Must not send"))).isEmpty()
        f.preferences.save(f.member.email,NotificationPreferenceRequest(saved.revision))
        assertThat(f.preferences.methodsFor(f.member,f.catalogue.get("general.message"))).containsExactly(f.sms.channel)
    }
    @Test fun `member choice is restricted to allowed methods and never controls security or explicit PDF sends`() {
        assertThatThrownBy {f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("general.message" to setOf(f.sms.channel))))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("security.otp" to emptySet())))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("documents.email" to emptySet())))}.isInstanceOf(BadRequestException::class.java)
        assertThat(f.preferences.view(f.member.email).notifications.map {it.id}).doesNotContain("security.otp","documents.email")
    }
    @Test fun `administrator enforced choices supersede a saved opt out and stale preference writes conflict`() {
        f.configure()
        val saved=f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("general.message" to emptySet())))
        assertThatThrownBy {f.preferences.save(f.member.email,NotificationPreferenceRequest(-1))}.isInstanceOf(ConflictException::class.java)
        f.configure(rule=NotificationRule(allowedMethods=setOf(f.sms.channel),defaultMethods=setOf(f.sms.channel),allowMemberChoice=false))
        assertThat(f.preferences.methodsFor(f.member,f.catalogue.get("general.message"))).containsExactly(f.sms.channel)
        assertThat(saved.revision).isZero()
    }
}
