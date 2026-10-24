package com.starter.mfa

import com.starter.email.EmailConfiguration
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate

class EmailMfaGuardTest {
    @Test fun `email delivery cannot be switched off or made invalid while email is the only factor`() {
        val credentials = mock(MfaCredentialRepository::class.java)
        val guard = EmailMfaGuard(mock(JdbcTemplate::class.java), credentials)
        `when`(credentials.emailOnlyCount()).thenReturn(1)
        assertThatThrownBy { guard.check(EmailConfiguration(enabled = false)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { guard.check(EmailConfiguration(enabled = true)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { guard.checkNotificationRoute(false) }.isInstanceOf(BadRequestException::class.java)
        guard.checkNotificationRoute(true)
        `when`(credentials.emailOnlyCount()).thenReturn(0)
        guard.check(EmailConfiguration(enabled = false))
        guard.checkNotificationRoute(false)
    }
}
