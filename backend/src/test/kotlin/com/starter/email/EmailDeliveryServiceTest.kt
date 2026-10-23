package com.starter.email

import com.starter.dto.SendEmailRequest
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class EmailDeliveryServiceTest {
    private val settings = mock(EmailSettingsService::class.java)
    private val provider = mock(EmailProviderClient::class.java)
    private val service = EmailDeliveryService(settings, provider)
    private val message = SendEmailRequest("reader@example.test", "Test email", "Example text")

    @Test fun `disabled delivery is optional and never contacts an email service`() {
        `when`(settings.current()).thenReturn(EmailConfiguration())
        assertThat(service.send(message).status).isEqualTo("DISABLED")
        verifyNoInteractions(provider)
    }
    @Test fun `provider failure yields an explicit optional result and does not break the calling workflow`() {
        val config = EmailConfiguration(enabled = true)
        `when`(settings.current()).thenReturn(config)
        doThrow(EmailDeliveryException("SMTP authentication failed")).`when`(provider).send(config, message)
        assertThat(service.send(message).status).isEqualTo("UNAVAILABLE")
    }
    @Test fun `unexpected failures cannot expose credentials or message contents`() {
        `when`(settings.current()).thenThrow(IllegalStateException("private-password private-message"))
        assertThat(service.send(message).message).doesNotContain("private-password", "private-message")
    }
    @Test fun `accepted delivery does not claim confirmed inbox receipt`() {
        val config = EmailConfiguration(enabled = true)
        `when`(settings.current()).thenReturn(config)
        val result = service.send(message)
        assertThat(result.status).isEqualTo("ACCEPTED")
        assertThat(result.message).contains("Inbox delivery is not confirmed")
        verify(provider).send(config, message)
    }
    @Test fun `message validation blocks header injection and oversized content before sending`() {
        listOf(message.copy(subject = "Hello\r\nBcc: other@example.test"), message.copy(recipient = "reader@example.test,other@example.test"), message.copy(text = "a".repeat(20001))).forEach {
            assertThatThrownBy { service.send(it) }.isInstanceOf(BadRequestException::class.java)
        }
        verifyNoInteractions(settings, provider)
    }
}
