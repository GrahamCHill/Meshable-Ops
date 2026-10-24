package com.starter.mfa

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.EmailDeliveryResult
import com.starter.exception.BadRequestException
import com.starter.model.postgres.User
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.repository.postgres.UserRepository
import com.starter.security.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional

class MfaServiceTest {
    private val users = mock(UserRepository::class.java)
    private val credentials = mock(MfaCredentialRepository::class.java)
    private val challenges = mock(MfaChallengeRepository::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val guard = mock(EmailMfaGuard::class.java)
    private val passwords = SaltedPasswordEncoder()
    private val user = User(email = "admin@app.local", role = "ADMIN", password = passwords.encode("sample-admin-password"))
    private val credential = MfaCredential(user.id)
    private val saved = mutableMapOf<String, MfaChallenge>()
    private val service = MfaService(users, credentials, challenges, passwords, SecureValues("test-jwt-pepper"), SecretEncryption(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 4 })), notifications, jacksonObjectMapper(), mock(RateLimits::class.java), guard)
    @BeforeEach fun setup() {
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user)); `when`(users.lockedByEmail(user.email)).thenReturn(Optional.of(user))
        `when`(users.findById(user.id)).thenReturn(Optional.of(user)); `when`(users.lockedById(user.id)).thenReturn(Optional.of(user))
        `when`(credentials.findById(user.id)).thenReturn(Optional.of(credential)); `when`(credentials.locked(user.id)).thenReturn(Optional.of(credential))
        `when`(challenges.saveAndFlush(any(MfaChallenge::class.java))).thenAnswer { saved[it.getArgument<MfaChallenge>(0).id] = it.getArgument(0); it.getArgument(0) }
        `when`(challenges.findById(anyString())).thenAnswer { Optional.ofNullable(saved[it.getArgument(0)]) }
        `when`(challenges.locked(anyString())).thenAnswer { Optional.ofNullable(saved[it.getArgument(0)]) }
    }
    @Test fun `authenticator enrollment remains pending until a correct code and returns hashed one use recovery codes`() {
        val setup = service.begin(user.email, ChallengePurpose.SETUP_TOTP, MfaSetupRequest("sample-admin-password"))
        assertThat(credential.enabled()).isFalse(); assertThat(saved.keys).doesNotContain(setup.challengeToken)
        assertThat(setup.otpauthUri).startsWith("otpauth://totp/")
        val result = service.confirm(user.email, MfaVerifyRequest(setup.challengeToken, MfaMethod.TOTP, Totp.code(setup.secret!!, Instant.now().epochSecond / 30)))
        assertThat(credential.totpSecret).isEqualTo(setup.secret); assertThat(user.sessionVersion).isEqualTo(1)
        assertThat(result.second).hasSize(10); result.second.forEach { assertThat(credential.recoveryHashes).doesNotContain(it) }
        assertThatThrownBy { service.confirm(user.email, MfaVerifyRequest(setup.challengeToken, MfaMethod.TOTP, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
        val challenge = service.login(user)
        val recovery = result.second.first()
        service.verifyLogin(MfaVerifyRequest(challenge.challengeToken, MfaMethod.RECOVERY, recovery))
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(service.login(user).challengeToken, MfaMethod.RECOVERY, recovery)) }.isInstanceOf(MfaVerificationException::class.java)
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(challenge.challengeToken, MfaMethod.RECOVERY, result.second.last())) }.isInstanceOf(MfaVerificationException::class.java)
    }
    @Test fun `no email configuration cannot enroll email MFA`() {
        assertThatThrownBy { service.begin(user.email, ChallengePurpose.SETUP_EMAIL, MfaSetupRequest("sample-admin-password")) }.isInstanceOf(BadRequestException::class.java)
        assertThat(credential.emailEnabled).isFalse(); verify(notifications, never()).send(any<NotificationChannel>() ?: NotificationChannel.EMAIL, any<com.starter.dto.SendEmailRequest>() ?: com.starter.dto.SendEmailRequest("a@b.test", "subject", "text"), anyList())
    }
    @Test fun `email MFA requires the code actually sent to stored account email and never persists plaintext codes`() {
        `when`(notifications.availableOtp("email")).thenReturn(true)
        `when`(notifications.sendOtp(anyString(),any<com.starter.notification.NotificationTarget>() ?: com.starter.notification.NotificationTarget(),anyString(),anyInt())).thenReturn(EmailDeliveryResult("ACCEPTED", "Accepted"))
        val enrollment = service.begin(user.email, ChallengePurpose.SETUP_EMAIL, MfaSetupRequest("sample-admin-password"))
        assertThat(credential.emailEnabled).isFalse()
        val target = ArgumentCaptor.forClass(com.starter.notification.NotificationTarget::class.java)
        val sentCode = ArgumentCaptor.forClass(String::class.java)
        verify(notifications).sendOtp(eq("email") ?: "email",target.capture() ?: com.starter.notification.NotificationTarget(),sentCode.capture() ?: "",eq(5))
        assertThat(target.value.email).isEqualTo(user.email)
        assertThat(target.value.verifiedMethods).containsExactly("email")
        val code = sentCode.value
        val challenge = saved.values.single()
        assertThat(challenge.emailCodeHash).isNotEqualTo(code)
        val wrong = if (code == "000000") "111111" else "000000"
        assertThatThrownBy { service.confirm(user.email, MfaVerifyRequest(enrollment.challengeToken, MfaMethod.EMAIL, wrong)) }.isInstanceOf(MfaVerificationException::class.java)
        assertThat(challenge.attempts).isEqualTo(1); assertThat(credential.emailEnabled).isFalse()
        service.confirm(user.email, MfaVerifyRequest(enrollment.challengeToken, MfaMethod.EMAIL, code))
        assertThat(credential.emailEnabled).isTrue(); assertThat(challenge.emailCodeHash).isNull(); verify(guard, times(2)).lock()
    }
    @Test fun `five wrong attempts expired challenges disabled users and security version changes fail closed`() {
        credential.emailEnabled = true
        val challenge = service.login(user)
        repeat(5) { assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(challenge.challengeToken, MfaMethod.EMAIL, "123456")) }.isInstanceOf(MfaVerificationException::class.java) }
        assertThat(saved.values.single().attempts).isEqualTo(5)
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(challenge.challengeToken, MfaMethod.EMAIL, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
        val stale = service.login(user); user.sessionVersion++
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(stale.challengeToken, MfaMethod.EMAIL, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
        val disabled = service.login(user); user.active = false
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(disabled.challengeToken, MfaMethod.EMAIL, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
        user.active = true; val raw = "expired-token"; saved[SecureValues.hash(raw)] = MfaChallenge(SecureValues.hash(raw), user.id, expiresAt = Instant.now().minusSeconds(1), sessionVersion = user.sessionVersion)
        assertThatThrownBy { service.verifyLogin(MfaVerifyRequest(raw, MfaMethod.EMAIL, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
    }
    @Test fun `existing factors require password plus current proof to change security`() {
        credential.totpSecret = Totp.secret()
        assertThatThrownBy { service.begin(user.email, ChallengePurpose.SETUP_EMAIL, MfaSetupRequest("sample-admin-password")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.disable(user.email, MfaProofRequest("wrong-password", MfaMethod.TOTP, "123456")) }.isInstanceOf(MfaVerificationException::class.java)
        assertThat(credential.enabled()).isTrue()
    }
    @Test fun `primary admin recommendation is optional and stops after dismissal or enrollment`() {
        assertThat(service.recommended(user)).isTrue(); service.dismiss(user.email); assertThat(service.recommended(user)).isFalse()
        user.mfaPromptDismissed = false; `when`(credentials.enabledCount(user.id)).thenReturn(1)
        assertThat(service.recommended(user)).isFalse()
    }
}
