package com.starter.notification

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationLongTextTest {
    @Test fun `long comments keep all content and unicode while observing channel limits and deduplication`() {
        val f=NotificationFixture();f.configure()
        val text="A".repeat(1599)+"😀"+"B".repeat(1900)+"\nReply: https://workspace.example.test/ticket?comment=one"
        val result=f.publisher.publish("general.message",f.member.id,mapOf("message" to text),"thread-comment",splitLongText=true)
        assertThat(result.values.single().status).isEqualTo("ACCEPTED")
        assertThat(f.sms.sent.map { it.text }.joinToString("")).isEqualTo(text)
        assertThat(f.sms.sent).hasSize(3).allMatch { it.text.length <= 1600 && !it.text.last().isHighSurrogate() }
        f.publisher.publish("general.message",f.member.id,mapOf("message" to text),"thread-comment",splitLongText=true)
        assertThat(f.sms.sent).hasSize(3)
    }
}
