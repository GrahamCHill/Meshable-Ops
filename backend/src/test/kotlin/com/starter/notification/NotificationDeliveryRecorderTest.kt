package com.starter.notification

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class NotificationDeliveryRecorderTest {
    @Test fun `delivery journal masks addresses hashes idempotency keys and never stores code or message bodies`() {
        val calls=mutableListOf<List<Any?>>()
        val jdbc=mock(JdbcTemplate::class.java) {call -> if(call.method.name=="update") {calls+=call.arguments.toList();1} else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)}
        val recorder=NotificationDeliveryRecorder(jdbc)
        val id=recorder.claim("security.otp","email",NotificationTarget(UUID.randomUUID(),email="private@example.test"),"private@example.test","opaque-operation")!!
        recorder.finish(id,"ACCEPTED")
        val args=calls.flatten().flatMap {if(it is Array<*>) it.toList() else listOf(it)}.map {it.toString()}
        assertThat(args.joinToString(" ")).doesNotContain("private@example.test","opaque-operation","123456")
        assertThat(args).contains("p***@example.test","ACCEPTED")
        assertThat(args.any {it.matches(Regex("[a-f0-9]{64}"))}).isTrue()
    }
}
