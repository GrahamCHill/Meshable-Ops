package com.starter.security

import com.starter.model.postgres.User
import com.starter.service.JwtService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class TotpTest {
    @Test fun `RFC6238 SHA1 reference vectors match`() {
        val secret = Totp.encode("12345678901234567890".toByteArray())
        listOf(59L to "94287082", 1111111109L to "07081804", 1111111111L to "14050471", 1234567890L to "89005924", 2000000000L to "69279037", 20000000000L to "65353130").forEach { (seconds, expected) ->
            assertThat(Totp.code(secret, seconds / 30, 8)).isEqualTo(expected)
        }
    }
    @Test fun `six digit code accepts clock tolerance but rejects replay malformed and stale codes`() {
        val secret = Totp.secret(); val step = 100000L
        assertThat(Totp.verify(secret, Totp.code(secret, step), -1, step * 30)).isEqualTo(step)
        assertThat(Totp.verify(secret, Totp.code(secret, step), step, step * 30)).isNull()
        assertThat(Totp.verify(secret, Totp.code(secret, step - 1), -1, step * 30)).isEqualTo(step - 1)
        assertThat(Totp.verify(secret, Totp.code(secret, step + 1), -1, step * 30)).isEqualTo(step + 1)
        assertThat(Totp.verify(secret, Totp.code(secret, step - 2), -1, step * 30)).isNull()
        listOf("", "12345", "1234567", "abcdef").forEach { assertThat(Totp.verify(secret, it, -1, step * 30)).isNull() }
        assertThat(Totp.secret()).hasSize(32).isNotEqualTo(secret)
    }
    @Test fun `session tokens require MFA claim and current security version after enrollment`() {
        val jwt = JwtService(); ReflectionTestUtils.setField(jwt, "secret", "sample-jwt-signing-key-for-isolated-unit-tests-only-123456789")
        val user = User(email = "admin@example.test", password = "hash", sessionVersion = 3)
        val secured = SessionUserDetails(user, true)
        val valid = jwt.generateToken(secured, mapOf("sv" to 3, "mfa" to true))
        assertThat(jwt.isTokenValid(valid, secured)).isTrue()
        assertThat(jwt.isTokenValid(jwt.generateToken(secured, mapOf("sv" to 3)), secured)).isFalse()
        assertThat(jwt.isTokenValid(jwt.generateToken(secured, mapOf("sv" to 2, "mfa" to true)), secured)).isFalse()
        user.sessionVersion++
        assertThat(jwt.isTokenValid(valid, SessionUserDetails(user, true))).isFalse()
    }
}
